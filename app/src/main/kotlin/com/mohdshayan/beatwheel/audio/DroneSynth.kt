package com.mohdshayan.beatwheel.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Process
import com.mohdshayan.beatwheel.core.drone.Waveform
import com.mohdshayan.beatwheel.core.drone.WavetableOscillator
import com.mohdshayan.beatwheel.core.drone.Wavetables

/**
 * Streams a wavetable drone through a low-latency AudioTrack on its own urgent-audio thread.
 * Frequency, waveform and volume change without restarting, and the gain ramps so nothing clicks.
 */
class DroneSynth(private val context: Context, private val onFocusLost: () -> Unit) {
    private val sampleRate = 48_000
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val tables = Waveform.entries.associateWith { Wavetables.build(it) }
    private val oscillator = WavetableOscillator(sampleRate, tables.getValue(Waveform.REED))

    @Volatile private var targetGain = 0f
    @Volatile private var running = false
    @Volatile private var stopAfterNanos = Long.MAX_VALUE
    private var thread: Thread? = null
    private var focusRequest: AudioFocusRequest? = null

    @Volatile var frequencyHz: Double = 220.0
        private set

    val isPlaying: Boolean get() = running

    /** Tables with their upper harmonics dropped, for drones high enough that the full table would alias. */
    private val limitedTables = HashMap<Pair<Waveform, Int>, FloatArray>()

    fun set(frequencyHz: Double, waveform: Waveform, volume: Float) {
        this.frequencyHz = frequencyHz
        oscillator.frequencyHz = frequencyHz
        val limit = Wavetables.harmonicLimit(frequencyHz, sampleRate)
        oscillator.table = if (limit >= Wavetables.harmonics(waveform).size) tables.getValue(waveform)
        else limitedTables.getOrPut(waveform to limit) { Wavetables.build(waveform, maxHarmonics = limit) }
        targetGain = volume.coerceIn(0f, 1f) * 0.8f
    }

    /** Starts streaming; [durationMs] stops it by itself, for the temperament editor's short previews. */
    fun start(durationMs: Long? = null) {
        stopAfterNanos = if (durationMs != null) System.nanoTime() + durationMs * 1_000_000 else Long.MAX_VALUE
        if (running) return
        // A preview that just ran out may still be fading; its exit would otherwise stop the new stream.
        thread?.join(500)
        thread = null
        if (!requestFocus()) return
        running = true
        thread = Thread({ loop() }, "beatwheel-drone").also { it.start() }
    }

    fun stop() {
        running = false
        thread?.join(500)
        thread = null
        focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        focusRequest = null
    }

    /** Headphones or a headset keep the drone out of the microphone, so there is nothing to cancel. */
    fun isOutputPrivate(): Boolean = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any {
        it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
            it.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
            (android.os.Build.VERSION.SDK_INT >= 31 && it.type == AudioDeviceInfo.TYPE_BLE_HEADSET)
    }

    private fun requestFocus(): Boolean {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attrs)
            .setOnAudioFocusChangeListener { change ->
                if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                    onFocusLost()
                }
            }
            .build()
        focusRequest = request
        return audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun loop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        val request = focusRequest
        val frames = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)?.toIntOrNull() ?: 480
        val minBuffer = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT)
        val track = try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                        .build(),
                )
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                .setBufferSizeInBytes(maxOf(minBuffer, frames * 4 * 4))
                .build()
        } catch (e: Exception) {
            running = false
            request?.let { audioManager.abandonAudioFocusRequest(it) }
            return
        }
        val block = FloatArray(frames)
        var gain = 0f
        track.play()
        while (running || gain > 0.0005f) {
            if (System.nanoTime() > stopAfterNanos) running = false
            val goal = if (running) targetGain else 0f
            oscillator.render(block, frames, 1f)
            val step = (goal - gain) / frames
            for (i in 0 until frames) {
                gain += step
                block[i] *= gain
            }
            gain = goal.let { if (kotlin.math.abs(gain - it) < 1e-4f) it else gain }
            if (track.write(block, 0, frames, AudioTrack.WRITE_BLOCKING) < 0) break
        }
        track.pause()
        track.flush()
        track.release()
        running = false
        // A preview that ran out by itself hands audio focus back, so other players resume.
        request?.let { audioManager.abandonAudioFocusRequest(it) }
    }
}
