package com.mohdshayan.beatwheel.ui.profiles

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mohdshayan.beatwheel.core.music.Transposition
import com.mohdshayan.beatwheel.data.db.CustomTemperament
import com.mohdshayan.beatwheel.data.db.InstrumentProfile
import com.mohdshayan.beatwheel.data.repo.ProfileRepository
import com.mohdshayan.beatwheel.di.ServiceLocator
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale

data class ProfileRow(val profile: InstrumentProfile, val subtitle: String, val active: Boolean)

sealed interface ProfilesUi {
    data object Loading : ProfilesUi
    data class Loaded(
        val builtIn: List<ProfileRow>,
        val custom: List<ProfileRow>,
        val temperaments: List<CustomTemperament>,
    ) : ProfilesUi
}

class ProfilesViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = ServiceLocator.profiles
    private val prefs = ServiceLocator.appPrefs
    private val messages = ServiceLocator.messages

    val ui: StateFlow<ProfilesUi> = combine(repo.profiles, repo.temperaments, prefs.activeProfileId) { profiles, temps, activeId ->
        if (profiles.isEmpty()) return@combine ProfilesUi.Loading
        val effectiveActive = profiles.firstOrNull { it.id == activeId }?.id ?: profiles.firstOrNull { it.builtInKey == "chromatic" }?.id
        val rows = profiles.map { p ->
            val parts = buildList {
                if (p.transpositionSemitones != 0) add(Transposition.label(p.transpositionSemitones))
                add("A " + String.format(Locale.US, "%.1f", p.referenceAHz))
                add(ProfileRepository.describe(p, temps))
                if (p.heldToneMode) add("held tone")
            }
            ProfileRow(p, parts.joinToString(", "), p.id == effectiveActive)
        }
        ProfilesUi.Loaded(rows.filter { it.profile.builtInKey != null }, rows.filter { it.profile.builtInKey == null }, temps)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfilesUi.Loading)

    fun select(p: InstrumentProfile) = viewModelScope.launch {
        repo.select(p.id)
        messages.show("${p.name} selected")
    }

    fun copy(p: InstrumentProfile, onCopied: (Long) -> Unit) = viewModelScope.launch {
        val id = repo.copy(p)
        messages.show("Profile copied")
        onCopied(id)
    }

    fun delete(p: InstrumentProfile) = viewModelScope.launch {
        repo.delete(p)
        messages.show("Profile deleted")
    }

    fun deleteTemperament(t: CustomTemperament) = viewModelScope.launch {
        repo.deleteTemperament(t.id)
        messages.show("Temperament deleted")
    }
}
