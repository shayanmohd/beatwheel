package com.mohdshayan.beatwheel.ui.tuner

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mohdshayan.beatwheel.audio.DroneState
import com.mohdshayan.beatwheel.audio.ListenStatus
import com.mohdshayan.beatwheel.audio.Reading
import com.mohdshayan.beatwheel.core.drone.Waveform
import com.mohdshayan.beatwheel.core.music.Transposition
import com.mohdshayan.beatwheel.core.music.TuningMath
import com.mohdshayan.beatwheel.data.db.InstrumentProfile
import com.mohdshayan.beatwheel.data.prefs.Counter
import com.mohdshayan.beatwheel.data.prefs.DisplayMode
import com.mohdshayan.beatwheel.data.prefs.DroneSettings
import com.mohdshayan.beatwheel.data.repo.ActiveTuning
import com.mohdshayan.beatwheel.data.repo.RecorderState
import com.mohdshayan.beatwheel.di.ServiceLocator
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.abs

data class TunerUi(
    val loading: Boolean = true,
    val tuning: ActiveTuning? = null,
    val profiles: List<InstrumentProfile> = emptyList(),
    val displayMode: DisplayMode = DisplayMode.STROBE,
    val status: ListenStatus = ListenStatus.IDLE,
    val drone: DroneState = DroneState(),
    val droneSettings: DroneSettings = DroneSettings("reed", 0.6f, 3, 9),
    val recorder: RecorderState = RecorderState(),
    val micAsked: Boolean = false,
    val firstRunDiscShown: Boolean = true,
    val rotateReduced: Boolean = false,
    val droneHintShown: Boolean = true,
    val sensitivity: Float = 0.5f,
)

private data class Prefs1(val mode: DisplayMode, val drone: DroneSettings, val asked: Boolean, val firstRun: Boolean, val rotate: Boolean)

@OptIn(FlowPreview::class)
class TunerViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = ServiceLocator.appPrefs
    val engine = ServiceLocator.audioEngine
    private val tuner = ServiceLocator.tuner
    private val recorder = ServiceLocator.recorder
    private val profileRepo = ServiceLocator.profiles
    private val usage = ServiceLocator.usage

    /** Full-rate readings for the disc, which reads them at draw time. */
    val readings: StateFlow<Reading> = engine.reading

    /** The numbers under the disc, ten times a second so they can be read. */
    val readout: StateFlow<Reading> = engine.reading.sample(100)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Reading.EMPTY)

    private val prefsFlow = combine(
        prefs.displayMode, prefs.drone, prefs.onboardingDone, prefs.firstRunDiscShown, prefs.rotateDiscReducedMotion,
    ) { mode, drone, asked, firstRun, rotate -> Prefs1(mode, drone, asked, firstRun, rotate) }

    val ui: StateFlow<TunerUi> = combine(
        combine(tuner.active, profileRepo.profiles) { a, p -> a to p },
        prefsFlow,
        combine(engine.status, engine.drone) { s, d -> s to d },
        recorder.state,
        combine(prefs.droneHintShown, prefs.inputSensitivity) { h, s -> h to s },
    ) { (tuning, profiles), p, (status, drone), rec, (hint, sensitivity) ->
        TunerUi(
            loading = tuning == null,
            tuning = tuning,
            profiles = profiles,
            displayMode = p.mode,
            status = status,
            drone = drone,
            droneSettings = p.drone,
            recorder = rec,
            micAsked = p.asked,
            firstRunDiscShown = p.firstRun,
            rotateReduced = p.rotate,
            droneHintShown = hint,
            sensitivity = sensitivity,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TunerUi())

    init {
        // A note held within 2 cents for two seconds is a successful use, counted once a day.
        viewModelScope.launch {
            var since = 0L
            var counted = false
            readout.collect { r ->
                val e = r.estimate
                if (e.hasPitch && e.strobeLocked && abs(e.cents) <= 2.0) {
                    val now = System.currentTimeMillis()
                    if (since == 0L) since = now
                    if (!counted && now - since >= 2_000) {
                        counted = true
                        usage.recordHeldNote()
                    }
                } else {
                    since = 0L
                    counted = false
                }
            }
        }
        viewModelScope.launch {
            engine.status.collect {
                if (it == ListenStatus.LISTENING) prefs.increment(Counter.TUNING_SESSIONS)
            }
        }
        // Keep a sounding drone in tune with the profile and the drone settings.
        viewModelScope.launch {
            combine(tuner.active, prefs.drone) { a, d -> a to d }.collect { (a, d) ->
                if (a != null && engine.drone.value.playing) {
                    engine.updateDrone(droneHz(a, d), Waveform.fromKey(d.waveform), d.volume)
                }
            }
        }
    }

    fun selectProfile(id: Long) = viewModelScope.launch { profileRepo.select(id) }

    fun setDisplayMode(mode: DisplayMode) = viewModelScope.launch { prefs.setDisplayMode(mode) }

    fun markMicAsked() = viewModelScope.launch { prefs.setOnboardingDone(true) }

    fun markFirstRunDiscShown() = viewModelScope.launch { prefs.setFirstRunDiscShown() }

    fun retryMic() = engine.retry()

    fun startDrone() = viewModelScope.launch {
        val a = tuner.active.value ?: return@launch
        val d = prefs.drone.first()
        if (engine.startDrone(droneHz(a, d), Waveform.fromKey(d.waveform), d.volume)) {
            prefs.increment(Counter.DRONE_STARTS)
        } else {
            ServiceLocator.messages.show("Another app is holding the audio. Start the drone again when it stops.")
        }
    }

    fun stopDrone() = engine.stopDrone()

    fun dismissDroneHint() = viewModelScope.launch { prefs.setDroneHintShown() }

    fun setDronePitchClass(pc: Int) = viewModelScope.launch { prefs.setDronePitchClass(pc) }
    fun setDroneOctave(octave: Int) = viewModelScope.launch { prefs.setDroneOctave(octave) }
    fun setDroneWaveform(w: Waveform) = viewModelScope.launch { prefs.setDroneWaveform(w.key) }
    fun setDroneVolume(v: Float) = viewModelScope.launch { prefs.setDroneVolume(v) }

    fun startRecording() = recorder.start()

    fun stopAndSave(name: String, onSaved: (Long) -> Unit) = viewModelScope.launch {
        recorder.stopAndSave(name)?.let(onSaved)
    }

    fun discardRecording() = recorder.discard()

    companion object {
        /** The drone plays the written note the user picked, sounding through the profile's transposition and temperament. */
        fun droneWrittenMidi(d: DroneSettings) = (d.octave + 1) * 12 + d.pitchClass

        fun droneHz(a: ActiveTuning, d: DroneSettings): Double {
            val sounding = Transposition.sounding(droneWrittenMidi(d), a.profile.transpositionSemitones)
            return TuningMath.targetHz(sounding, a.profile.referenceAHz, a.offsets)
        }
    }
}
