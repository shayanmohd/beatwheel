package com.mohdshayan.beatwheel.ui.sessions

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mohdshayan.beatwheel.core.drift.WeekGroup
import com.mohdshayan.beatwheel.core.drift.WeekGrouping
import com.mohdshayan.beatwheel.data.db.DriftSession
import com.mohdshayan.beatwheel.data.repo.ImportResult
import com.mohdshayan.beatwheel.di.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

sealed interface SessionsUi {
    data object Loading : SessionsUi
    data class Loaded(val groups: List<WeekGroup<DriftSession>>) : SessionsUi
}

class SessionsViewModel(app: Application) : AndroidViewModel(app) {
    private val backup = ServiceLocator.backup
    private val messages = ServiceLocator.messages

    val ui: StateFlow<SessionsUi> = ServiceLocator.sessionDao.observeSaved()
        .map { list -> SessionsUi.Loaded(WeekGrouping.group(list, Instant.now(), ZoneId.systemDefault()) { it.startedAt }) as SessionsUi }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SessionsUi.Loading)

    private val _importError = MutableStateFlow<String?>(null)
    val importError: StateFlow<String?> = _importError.asStateFlow()

    fun import(uri: Uri) = viewModelScope.launch {
        when (val r = backup.importBackup(uri)) {
            is ImportResult.Imported -> {
                _importError.value = null
                messages.show("Backup imported")
            }
            ImportResult.NotABackup -> _importError.value = "This file is not a Beatwheel backup."
            ImportResult.Unreadable -> _importError.value = "Could not read that file. Choose it again from its folder."
            ImportResult.TooLarge -> _importError.value = "This backup is too large to import on this device."
        }
    }

    fun clearError() {
        _importError.value = null
    }
}
