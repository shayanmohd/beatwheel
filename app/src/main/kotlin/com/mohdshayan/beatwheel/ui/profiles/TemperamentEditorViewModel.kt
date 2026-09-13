package com.mohdshayan.beatwheel.ui.profiles

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.mohdshayan.beatwheel.core.drone.Waveform
import com.mohdshayan.beatwheel.core.music.Temperaments
import com.mohdshayan.beatwheel.core.music.TuningMath
import com.mohdshayan.beatwheel.data.db.CustomTemperament
import com.mohdshayan.beatwheel.di.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale

data class TemperamentForm(
    val loaded: Boolean = false,
    val id: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val name: String = "",
    val offsets: List<String> = List(12) { "0.0" },
    val showErrors: Boolean = false,
) {
    fun offsetError(i: Int): String? =
        offsets[i].toDoubleOrNull().let { if (it == null || !it.isFinite() || it < -50.0 || it > 50.0) "-50.0 to +50.0" else null }

    val nameError get() = if (name.isBlank()) "Enter a name" else null
    val valid get() = nameError == null && (0 until 12).all { offsetError(it) == null }
}

class TemperamentEditorViewModel(app: Application, handle: SavedStateHandle) : AndroidViewModel(app) {
    private val id: Long = handle.get<Long>("id") ?: 0L
    private val repo = ServiceLocator.profiles
    private val messages = ServiceLocator.messages
    private val engine = ServiceLocator.audioEngine

    private val _form = MutableStateFlow(TemperamentForm())
    val form: StateFlow<TemperamentForm> = _form.asStateFlow()

    init {
        viewModelScope.launch {
            val t = if (id > 0) repo.getTemperament(id) else null
            _form.value = if (t == null) {
                TemperamentForm(loaded = true, name = "My temperament")
            } else {
                val parsed = Temperaments.parseOffsets(t.offsetsCents) ?: DoubleArray(12)
                TemperamentForm(true, t.id, t.createdAt, t.name, parsed.map { String.format(Locale.US, "%.1f", it) })
            }
        }
    }

    fun setName(name: String) {
        _form.value = _form.value.copy(name = name.take(40))
    }

    fun setOffset(i: Int, text: String) {
        val list = _form.value.offsets.toMutableList()
        list[i] = text.take(6)
        _form.value = _form.value.copy(offsets = list)
    }

    /** Starts from a built-in table so a variant takes a few edits rather than twelve. */
    fun startFrom(key: String) {
        val preset = Temperaments.preset(key) ?: return
        _form.value = _form.value.copy(offsets = preset.offsetsCents.map { String.format(Locale.US, "%.1f", it) })
    }

    /** Plays one note of the temperament at octave 4 on the active profile's reference. */
    fun play(pitchClass: Int) = viewModelScope.launch {
        if (engine.drone.value.playing) {
            messages.show("Stop the drone on Tune to play single notes")
            return@launch
        }
        val cents = _form.value.offsets[pitchClass].toDoubleOrNull()?.takeIf { it.isFinite() }?.coerceIn(-50.0, 50.0) ?: 0.0
        val reference = ServiceLocator.tuner.active.value?.profile?.referenceAHz ?: 440.0
        val waveform = Waveform.fromKey(ServiceLocator.appPrefs.drone.first().waveform)
        engine.preview(TuningMath.applyCents(TuningMath.equalHz(60.0 + pitchClass, reference), cents), waveform)
    }

    fun save(onSaved: () -> Unit) {
        val f = _form.value
        if (!f.valid) {
            _form.value = f.copy(showErrors = true)
            return
        }
        viewModelScope.launch {
            val offsets = f.offsets.map { it.toDouble() }.toDoubleArray()
            repo.saveTemperament(CustomTemperament(id = f.id, name = f.name.trim(), offsetsCents = Temperaments.formatOffsets(offsets), createdAt = f.createdAt))
            messages.show("Temperament saved")
            onSaved()
        }
    }
}
