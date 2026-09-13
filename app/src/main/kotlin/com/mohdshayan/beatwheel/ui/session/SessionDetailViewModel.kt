package com.mohdshayan.beatwheel.ui.session

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.mohdshayan.beatwheel.core.drift.DriftPoint
import com.mohdshayan.beatwheel.core.drift.SessionCsv
import com.mohdshayan.beatwheel.data.db.DriftSession
import com.mohdshayan.beatwheel.di.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface SessionDetailUi {
    data object Loading : SessionDetailUi
    data object Missing : SessionDetailUi
    data class Loaded(val session: DriftSession, val points: List<DriftPoint>) : SessionDetailUi
}

class SessionDetailViewModel(app: Application, handle: SavedStateHandle) : AndroidViewModel(app) {
    private val id: Long = handle.get<Long>("id") ?: 0L
    private val dao = ServiceLocator.sessionDao
    private val backup = ServiceLocator.backup
    private val messages = ServiceLocator.messages

    val ui: StateFlow<SessionDetailUi> = combine(dao.observe(id), dao.observeReadings(id)) { s, readings ->
        if (s == null) SessionDetailUi.Missing
        else SessionDetailUi.Loaded(s, readings.map { DriftPoint(it.offsetMs, it.cents, it.gap) })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SessionDetailUi.Loading)

    private val _fileError = MutableStateFlow(false)
    val fileError: StateFlow<Boolean> = _fileError.asStateFlow()

    fun csvFileName(): String = (ui.value as? SessionDetailUi.Loaded)?.session?.name?.let(SessionCsv::fileName) ?: "session.csv"

    fun exportCsv(uri: Uri) = viewModelScope.launch {
        if (backup.exportCsv(id, uri)) {
            _fileError.value = false
            messages.show("CSV exported")
        } else {
            _fileError.value = true
        }
    }

    fun share(onIntent: (Intent) -> Unit) = viewModelScope.launch {
        backup.shareCsvIntent(id)?.let(onIntent) ?: run { _fileError.value = true }
    }

    fun clearError() {
        _fileError.value = false
    }

    /** Runs on the app scope: it is called as the screen closes, when this ViewModel's scope is about to be cancelled. */
    fun rename(name: String, note: String) = ServiceLocator.appScope.launch {
        val s = dao.get(id) ?: return@launch
        val trimmed = name.trim()
        if (trimmed.isEmpty() || (trimmed == s.name && note == s.note)) return@launch
        dao.update(s.copy(name = trimmed, note = note))
    }

    fun delete(onDone: () -> Unit) = viewModelScope.launch {
        dao.delete(id)
        messages.show("Session deleted")
        onDone()
    }
}
