package com.mohdshayan.beatwheel.ui.profiles

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.mohdshayan.beatwheel.core.music.NoteNames
import com.mohdshayan.beatwheel.core.music.TuningMath
import com.mohdshayan.beatwheel.data.db.InstrumentProfile
import com.mohdshayan.beatwheel.data.repo.ProfileRepository
import com.mohdshayan.beatwheel.data.repo.TemperamentChoice
import com.mohdshayan.beatwheel.di.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale

data class ProfileForm(
    val loaded: Boolean = false,
    val missing: Boolean = false,
    val original: InstrumentProfile? = null,
    val name: String = "",
    val transposition: Int = 0,
    val reference: String = "440.0",
    val temperamentKey: String = "equal",
    val tonic: Int = 0,
    val keepReferenceA: Boolean = true,
    val minHz: String = "",
    val maxHz: String = "",
    val strings: String = "",
    val heldTone: Boolean = false,
    val gateDb: Float = -52f,
    val showErrors: Boolean = false,
) {
    val nameError get() = if (name.isBlank()) "Enter a name" else null
    val referenceError get() = reference.toDoubleOrNull().let { if (it == null || !TuningMath.isValidReference(it)) "Enter a value from 415.0 to 466.0" else null }
    val rangeError: String?
        get() {
            val lo = minHz.toDoubleOrNull()
            val hi = maxHz.toDoubleOrNull()
            return if (lo == null || hi == null || !lo.isFinite() || !hi.isFinite() || lo < 20.0 || hi > 5000.0 || lo * 1.5 > hi) "Enter a range from 20 to 5000 Hz, highest at least half again the lowest" else null
        }
    val stringsError get() = if (strings.isBlank() || NoteNames.parseNoteList(strings) != null) null else "Write notes with octaves, like E2 A2 D3"
    val valid get() = nameError == null && referenceError == null && rangeError == null && stringsError == null
}

class ProfileEditorViewModel(app: Application, handle: SavedStateHandle) : AndroidViewModel(app) {
    private val id: Long = handle.get<Long>("id") ?: 0L
    private val repo = ServiceLocator.profiles
    private val messages = ServiceLocator.messages

    private val _form = MutableStateFlow(ProfileForm())
    val form: StateFlow<ProfileForm> = _form.asStateFlow()

    val temperaments: StateFlow<List<TemperamentChoice>> = repo.temperaments
        .map { ProfileRepository.choices(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileRepository.choices(emptyList()))

    init {
        viewModelScope.launch {
            val p = repo.get(id)
            _form.value = if (p == null) ProfileForm(loaded = true, missing = true) else ProfileForm(
                loaded = true,
                original = p,
                name = p.name,
                transposition = p.transpositionSemitones,
                reference = String.format(Locale.US, "%.1f", p.referenceAHz),
                temperamentKey = p.temperamentKey,
                tonic = p.tonicPitchClass,
                keepReferenceA = p.keepReferenceA,
                minHz = trim(p.minHz),
                maxHz = trim(p.maxHz),
                strings = NoteNames.formatMidiList(p.stringTargetsMidi),
                heldTone = p.heldToneMode,
                gateDb = p.noiseGateDb,
            )
        }
    }

    private fun trim(v: Double) = if (v == Math.floor(v)) v.toInt().toString() else String.format(Locale.US, "%.1f", v)

    fun update(block: (ProfileForm) -> ProfileForm) {
        _form.value = block(_form.value)
    }

    fun save(onSaved: () -> Unit) {
        val f = _form.value
        if (!f.valid) {
            _form.value = f.copy(showErrors = true)
            return
        }
        val base = f.original ?: return
        viewModelScope.launch {
            repo.save(
                base.copy(
                    name = f.name.trim(),
                    transpositionSemitones = f.transposition,
                    referenceAHz = Math.round(f.reference.toDouble() * 10) / 10.0,
                    temperamentKey = f.temperamentKey,
                    tonicPitchClass = f.tonic,
                    keepReferenceA = f.keepReferenceA,
                    minHz = f.minHz.toDouble(),
                    maxHz = f.maxHz.toDouble(),
                    stringTargetsMidi = NoteNames.parseNoteList(f.strings) ?: "",
                    heldToneMode = f.heldTone,
                    noiseGateDb = f.gateDb,
                ),
            )
            messages.show("Profile saved")
            onSaved()
        }
    }
}
