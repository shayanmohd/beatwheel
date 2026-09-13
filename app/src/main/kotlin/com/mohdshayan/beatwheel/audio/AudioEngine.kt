package com.mohdshayan.beatwheel.audio

import android.os.Process
import com.mohdshayan.beatwheel.core.drone.BeatMeter
import com.mohdshayan.beatwheel.core.drone.DroneCanceller
import com.mohdshayan.beatwheel.core.drone.Waveform
import com.mohdshayan.beatwheel.core.pitch.Estimate
import com.mohdshayan.beatwheel.core.pitch.EstimatorConfig
import com.mohdshayan.beatwheel.core.pitch.PitchEstimator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

enum class ListenStatus { IDLE, STARTING, LISTENING, NO_PERMISSION, BUSY }

/** The live measurement the screens draw. MIDI is the sounding pitch; screens convert to written. */
data class Reading(
    val estimate: Estimate,
    /** Signed beats per second against the nearest drone partial, when the note sits within 1.5 Hz of it. */
    val beatsPerSecond: Double?,
    val timestampNanos: Long,
) {
    companion object {
        val EMPTY = Reading(Estimate.SILENT, null, 0L)
    }
}

data class DroneState(
    val playing: Boolean = false,
    val frequencyHz: Double = 0.0,
    val onSpeaker: Boolean = true,
)

/**
 * Owns the microphone and the drone. Listening is reference counted by the screens that show a
 * measurement (Tune and Stand) and stops shortly after the last one leaves, so moving between them
 * does not restart the input. The analysis thread runs the canceller, the estimator and the beat meter,
 * and publishes a conflated [Reading].
 */
class AudioEngine(
    private val sourceFactory: () -> SignalSource,
    private val droneFactory: (onFocusLost: () -> Unit) -> DroneSynth,
    private val scope: CoroutineScope,
) {
    private val _reading = MutableStateFlow(Reading.EMPTY)
    val reading: StateFlow<Reading> = _reading.asStateFlow()

    private val _status = MutableStateFlow(ListenStatus.IDLE)
    val status: StateFlow<ListenStatus> = _status.asStateFlow()

    private val _drone = MutableStateFlow(DroneState())
    val drone: StateFlow<DroneState> = _drone.asStateFlow()

    private val synth by lazy { droneFactory { stopDrone() } }

    @Volatile private var config = EstimatorConfig()
    @Volatile private var configDirty = true
    @Volatile private var droneHz = 0.0
    @Volatile private var cancelDrone = false

    private var holders = 0
    private var stopJob: Job? = null
    @Volatile private var thread: Thread? = null
    @Volatile private var running = false

    val isListening: Boolean get() = _status.value == ListenStatus.LISTENING

    fun configure(newConfig: EstimatorConfig) {
        config = newConfig
        configDirty = true
    }

    @Synchronized
    fun acquire() {
        holders++
        stopJob?.cancel()
        stopJob = null
        if (!running) startThread()
    }

    @Synchronized
    fun release() {
        holders = (holders - 1).coerceAtLeast(0)
        if (holders == 0) {
            stopJob?.cancel()
            stopJob = scope.launch {
                delay(800)
                synchronized(this@AudioEngine) { if (holders == 0) stopThread() }
            }
        }
    }

    /** Try the microphone again after a permission grant or when another app let go of it. */
    @Synchronized
    fun retry() {
        if (holders > 0) {
            stopThread()
            startThread()
        }
    }

    private fun startThread() {
        running = true
        _status.value = ListenStatus.STARTING
        thread = Thread({ loop() }, "beatwheel-analysis").also { it.start() }
    }

    private fun stopThread() {
        running = false
        thread?.join(1_000)
        thread = null
        if (_status.value == ListenStatus.LISTENING || _status.value == ListenStatus.STARTING) {
            _status.value = ListenStatus.IDLE
        }
        _reading.value = Reading.EMPTY
    }

    /** Returns false when the system would not give the drone audio focus, during a call for example. */
    fun startDrone(frequencyHz: Double, waveform: Waveform, volume: Float): Boolean {
        synth.set(frequencyHz, waveform, volume)
        synth.start()
        if (!synth.isPlaying) return false
        droneHz = frequencyHz
        val private = synth.isOutputPrivate()
        cancelDrone = !private
        _drone.value = DroneState(playing = true, frequencyHz = frequencyHz, onSpeaker = !private)
        return true
    }

    fun updateDrone(frequencyHz: Double, waveform: Waveform, volume: Float) {
        if (!_drone.value.playing) return
        synth.set(frequencyHz, waveform, volume)
        droneHz = frequencyHz
        _drone.value = _drone.value.copy(frequencyHz = frequencyHz)
    }

    fun stopDrone() {
        synth.stop()
        droneHz = 0.0
        cancelDrone = false
        _drone.value = DroneState(playing = false)
    }

    /** A short tone for auditioning one note of a temperament. Leaves a running drone alone. */
    fun preview(frequencyHz: Double, waveform: Waveform) {
        if (_drone.value.playing) return
        synth.set(frequencyHz, waveform, 0.6f)
        synth.start(durationMs = 1_200)
    }

    private fun loop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        val source = sourceFactory()
        val result = source.start()
        if (result != StartResult.OK) {
            _status.value = if (result == StartResult.NO_PERMISSION) ListenStatus.NO_PERMISSION else ListenStatus.BUSY
            running = false
            return
        }
        val rate = source.sampleRate
        val blockSize = rate / 100
        val input = FloatArray(blockSize)
        val cancelled = FloatArray(blockSize)
        val estimator = PitchEstimator(rate)
        // Every start builds a fresh estimator, so it takes the current profile now rather than waiting for the next change.
        configDirty = false
        estimator.configure(config)
        val canceller = DroneCanceller(rate)
        val beatMeter = BeatMeter(rate)
        var blocks = 0
        var silencedBlocks = 0
        _status.value = ListenStatus.LISTENING
        try {
            while (running) {
                val n = source.read(input, blockSize)
                if (n < blockSize) {
                    if (n < 0) {
                        _status.value = ListenStatus.BUSY
                        break
                    }
                    continue
                }
                if (configDirty) {
                    configDirty = false
                    estimator.configure(config)
                }
                val drone = droneHz
                canceller.setDrone(if (cancelDrone) drone else 0.0)
                canceller.process(input, cancelled, blockSize)
                val estimate = estimator.process(cancelled, blockSize)

                var beats: Double? = null
                if (drone > 0 && estimate.hasPitch) {
                    val k = (estimate.hz / drone).roundToInt().coerceAtLeast(1)
                    val partial = drone * k
                    if (abs(estimate.hz - partial) <= BeatMeter.UNISON_WINDOW_HZ) {
                        beatMeter.setReference(partial)
                        beatMeter.process(cancelled, blockSize)
                        beats = beatMeter.beatsPerSecond()
                    }
                }
                if (beats == null && drone > 0 && !estimate.hasPitch) beatMeter.setReference(0.0)

                blocks++
                if (blocks % 3 == 0) {
                    _reading.value = Reading(estimate, beats, System.nanoTime())
                }
                if (blocks % 50 == 0) {
                    silencedBlocks = if (source.isSilencedByOtherApp()) silencedBlocks + 1 else 0
                    if (silencedBlocks >= 2) {
                        _status.value = ListenStatus.BUSY
                        break
                    }
                    if (_status.value != ListenStatus.LISTENING) _status.value = ListenStatus.LISTENING
                }
            }
        } finally {
            source.stop()
            running = false
            _reading.value = Reading.EMPTY
        }
    }
}
