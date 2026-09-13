package com.mohdshayan.beatwheel.data.repo

import com.mohdshayan.beatwheel.audio.AudioEngine
import com.mohdshayan.beatwheel.core.music.Temperaments
import com.mohdshayan.beatwheel.core.pitch.EstimatorConfig
import com.mohdshayan.beatwheel.data.db.InstrumentProfile
import com.mohdshayan.beatwheel.data.prefs.AppPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The profile in use, resolved to the numbers the meter and the drone need. */
data class ActiveTuning(
    val profile: InstrumentProfile,
    val temperamentName: String,
    val offsets: DoubleArray,
    val profileCount: Int,
)

/** Keeps the audio engine configured from the active profile, its temperament and the sensitivity setting. */
class TunerController(
    repo: ProfileRepository,
    prefs: AppPrefs,
    engine: AudioEngine,
    scope: CoroutineScope,
) {
    val active: StateFlow<ActiveTuning?> = combine(
        prefs.activeProfileId,
        repo.profiles,
        repo.temperaments,
    ) { id, profiles, temps ->
        val profile = profiles.firstOrNull { it.id == id }
            ?: profiles.firstOrNull { it.builtInKey == "chromatic" }
            ?: return@combine null
        val choice = ProfileRepository.resolve(profile.temperamentKey, temps)
        ActiveTuning(
            profile = profile,
            temperamentName = ProfileRepository.describe(profile, temps),
            offsets = Temperaments.offsetsFor(choice.baseOffsets, profile.tonicPitchClass, profile.keepReferenceA),
            profileCount = profiles.size,
        )
    }.stateIn(scope, SharingStarted.Eagerly, null)

    init {
        scope.launch {
            combine(active.filterNotNull(), prefs.inputSensitivity) { a, sensitivity ->
                EstimatorConfig(
                    referenceAHz = a.profile.referenceAHz,
                    offsets = a.offsets,
                    minHz = a.profile.minHz,
                    maxHz = a.profile.maxHz,
                    heldTone = a.profile.heldToneMode,
                    noiseGateDb = a.profile.noiseGateDb,
                    sensitivity = sensitivity,
                )
            }.collect { engine.configure(it) }
        }
    }
}
