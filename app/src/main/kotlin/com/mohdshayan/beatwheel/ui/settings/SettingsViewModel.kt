package com.mohdshayan.beatwheel.ui.settings

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mohdshayan.beatwheel.core.drone.Waveform
import com.mohdshayan.beatwheel.data.prefs.Counter
import com.mohdshayan.beatwheel.data.prefs.DisplayMode
import com.mohdshayan.beatwheel.data.prefs.ThemeChoice
import com.mohdshayan.beatwheel.data.repo.ImportResult
import com.mohdshayan.beatwheel.di.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUi(
    val loaded: Boolean = false,
    val displayMode: DisplayMode = DisplayMode.STROBE,
    val rotateReduced: Boolean = false,
    val theme: ThemeChoice = ThemeChoice.SYSTEM,
    val sensitivity: Float = 0.5f,
    val waveform: Waveform = Waveform.REED,
    val countsEnabled: Boolean = false,
    val counts: Map<Counter, Int> = emptyMap(),
    val profileCount: Int = 0,
    val sessionCount: Int = 0,
)

private data class A(val mode: DisplayMode, val rotate: Boolean, val theme: ThemeChoice, val sensitivity: Float, val waveform: Waveform)

class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = ServiceLocator.appPrefs
    private val backup = ServiceLocator.backup
    private val messages = ServiceLocator.messages

    val ui: StateFlow<SettingsUi> = combine(
        combine(prefs.displayMode, prefs.rotateDiscReducedMotion, prefs.theme, prefs.inputSensitivity, prefs.drone) { m, r, t, s, d ->
            A(m, r, t, s, Waveform.fromKey(d.waveform))
        },
        prefs.localCountsEnabled,
        prefs.counts,
        ServiceLocator.database.profileDao().observeCount(),
        ServiceLocator.sessionDao.observeCount(),
    ) { a, enabled, counts, profiles, sessions ->
        SettingsUi(true, a.mode, a.rotate, a.theme, a.sensitivity, a.waveform, enabled, counts, profiles, sessions)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUi())

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun setDisplayMode(m: DisplayMode) = viewModelScope.launch { prefs.setDisplayMode(m) }
    fun setRotateReduced(on: Boolean) = viewModelScope.launch { prefs.setRotateDiscReducedMotion(on) }
    fun setTheme(t: ThemeChoice) = viewModelScope.launch { prefs.setTheme(t) }
    fun setSensitivity(v: Float) = viewModelScope.launch { prefs.setInputSensitivity(v) }
    fun setWaveform(w: Waveform) = viewModelScope.launch { prefs.setDroneWaveform(w.key) }
    fun setCountsEnabled(on: Boolean) = viewModelScope.launch { prefs.setLocalCountsEnabled(on) }

    fun export(uri: Uri) = viewModelScope.launch {
        if (backup.exportBackup(uri)) messages.show("Backup exported")
        else _error.value = WRITE_FAILED
    }

    fun import(uri: Uri) = viewModelScope.launch {
        when (backup.importBackup(uri)) {
            is ImportResult.Imported -> messages.show("Backup imported")
            ImportResult.NotABackup -> _error.value = "This file is not a Beatwheel backup."
            ImportResult.Unreadable -> _error.value = "Could not read that file. Choose it again from its folder."
            ImportResult.TooLarge -> _error.value = "This backup is too large to import on this device."
        }
    }

    fun showMessage(text: String) = messages.show(text)

    fun clearError() {
        _error.value = null
    }

    companion object {
        const val WRITE_FAILED = "Could not write the file. Choose another location."
    }
}
