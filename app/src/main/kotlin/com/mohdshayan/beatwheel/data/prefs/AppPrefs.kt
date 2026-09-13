package com.mohdshayan.beatwheel.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_prefs")

enum class DisplayMode(val key: String) { STROBE("strobe"), NEEDLE("needle") }

enum class ThemeChoice(val key: String) { SYSTEM("system"), LIGHT("light"), DARK("dark") }

enum class Counter(val key: String) {
    TUNING_SESSIONS("count_tuning_sessions"),
    DRONE_STARTS("count_drone_starts"),
    SESSIONS_SAVED("count_sessions_saved"),
    EXPORTS("count_exports"),
}

data class DroneSettings(val waveform: String, val volume: Float, val octave: Int, val pitchClass: Int)

data class ReviewState(val successfulUses: Int, val lastSuccessDay: Long, val prompted: Boolean)

class AppPrefs(private val context: Context) {

    private object Keys {
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val FIRST_RUN_DISC_SHOWN = booleanPreferencesKey("first_run_disc_shown")
        val ACTIVE_PROFILE_ID = longPreferencesKey("active_profile_id")
        val DISPLAY_MODE = stringPreferencesKey("display_mode")
        val ROTATE_DISC_REDUCED_MOTION = booleanPreferencesKey("rotate_disc_reduced_motion")
        val THEME = stringPreferencesKey("theme")
        val INPUT_SENSITIVITY = floatPreferencesKey("input_sensitivity")
        val DRONE_WAVEFORM = stringPreferencesKey("drone_waveform")
        val DRONE_VOLUME = floatPreferencesKey("drone_volume")
        val DRONE_OCTAVE = intPreferencesKey("drone_octave")
        val DRONE_PITCH_CLASS = intPreferencesKey("drone_pitch_class")
        val SUCCESSFUL_USES = intPreferencesKey("successful_uses")
        val LAST_SUCCESS_DAY = longPreferencesKey("last_success_day")
        val REVIEW_PROMPTED = booleanPreferencesKey("review_prompted")
        val LOCAL_COUNTS_ENABLED = booleanPreferencesKey("local_counts_enabled")
        val DRONE_HINT_SHOWN = booleanPreferencesKey("drone_hint_shown")
    }

    private val data = context.dataStore.data

    private suspend fun edit(block: (MutablePreferences) -> Unit) {
        context.dataStore.edit { block(it) }
    }

    /** True once the microphone has been requested at least once. */
    val onboardingDone: Flow<Boolean> = data.map { it[Keys.ONBOARDING_DONE] ?: false }
    suspend fun setOnboardingDone(done: Boolean) = edit { it[Keys.ONBOARDING_DONE] = done }

    val firstRunDiscShown: Flow<Boolean> = data.map { it[Keys.FIRST_RUN_DISC_SHOWN] ?: false }
    suspend fun setFirstRunDiscShown() = edit { it[Keys.FIRST_RUN_DISC_SHOWN] = true }

    val activeProfileId: Flow<Long> = data.map { it[Keys.ACTIVE_PROFILE_ID] ?: -1L }
    suspend fun setActiveProfileId(id: Long) = edit { it[Keys.ACTIVE_PROFILE_ID] = id }

    val displayMode: Flow<DisplayMode> = data.map { p ->
        DisplayMode.entries.firstOrNull { it.key == p[Keys.DISPLAY_MODE] } ?: DisplayMode.STROBE
    }
    suspend fun setDisplayMode(mode: DisplayMode) = edit { it[Keys.DISPLAY_MODE] = mode.key }

    val rotateDiscReducedMotion: Flow<Boolean> = data.map { it[Keys.ROTATE_DISC_REDUCED_MOTION] ?: false }
    suspend fun setRotateDiscReducedMotion(on: Boolean) = edit { it[Keys.ROTATE_DISC_REDUCED_MOTION] = on }

    val theme: Flow<ThemeChoice> = data.map { p ->
        ThemeChoice.entries.firstOrNull { it.key == p[Keys.THEME] } ?: ThemeChoice.SYSTEM
    }
    suspend fun setTheme(choice: ThemeChoice) = edit { it[Keys.THEME] = choice.key }

    val inputSensitivity: Flow<Float> = data.map { it[Keys.INPUT_SENSITIVITY] ?: 0.5f }
    suspend fun setInputSensitivity(value: Float) = edit { it[Keys.INPUT_SENSITIVITY] = value.coerceIn(0f, 1f) }

    val drone: Flow<DroneSettings> = data.map {
        DroneSettings(
            waveform = it[Keys.DRONE_WAVEFORM] ?: "reed",
            volume = it[Keys.DRONE_VOLUME] ?: 0.6f,
            octave = it[Keys.DRONE_OCTAVE] ?: 3,
            pitchClass = it[Keys.DRONE_PITCH_CLASS] ?: 9,
        )
    }
    suspend fun setDroneWaveform(key: String) = edit { it[Keys.DRONE_WAVEFORM] = key }
    suspend fun setDroneVolume(v: Float) = edit { it[Keys.DRONE_VOLUME] = v.coerceIn(0f, 1f) }
    suspend fun setDroneOctave(o: Int) = edit { it[Keys.DRONE_OCTAVE] = o.coerceIn(2, 5) }
    suspend fun setDronePitchClass(pc: Int) = edit { it[Keys.DRONE_PITCH_CLASS] = ((pc % 12) + 12) % 12 }

    val droneHintShown: Flow<Boolean> = data.map { it[Keys.DRONE_HINT_SHOWN] ?: false }
    suspend fun setDroneHintShown() = edit { it[Keys.DRONE_HINT_SHOWN] = true }

    val review: Flow<ReviewState> = data.map {
        ReviewState(it[Keys.SUCCESSFUL_USES] ?: 0, it[Keys.LAST_SUCCESS_DAY] ?: -1L, it[Keys.REVIEW_PROMPTED] ?: false)
    }
    suspend fun setReview(state: ReviewState) = edit {
        it[Keys.SUCCESSFUL_USES] = state.successfulUses
        it[Keys.LAST_SUCCESS_DAY] = state.lastSuccessDay
        it[Keys.REVIEW_PROMPTED] = state.prompted
    }

    val localCountsEnabled: Flow<Boolean> = data.map { it[Keys.LOCAL_COUNTS_ENABLED] ?: false }
    suspend fun setLocalCountsEnabled(on: Boolean) = edit { it[Keys.LOCAL_COUNTS_ENABLED] = on }

    val counts: Flow<Map<Counter, Int>> = data.map { p ->
        Counter.entries.associateWith { p[intPreferencesKey(it.key)] ?: 0 }
    }

    /** Counts only while the user has turned local counts on. Nothing leaves the device. */
    suspend fun increment(counter: Counter) {
        if (!localCountsEnabled.first()) return
        edit {
            val key = intPreferencesKey(counter.key)
            it[key] = (it[key] ?: 0) + 1
        }
    }
}
