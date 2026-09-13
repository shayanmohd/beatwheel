package com.mohdshayan.beatwheel.audio

import android.content.Context

/**
 * Debug builds can be fed a synthetic instrument for emulator runs and store screenshots, so the
 * readings on screen are real DSP output. Set it with
 *   adb shell setprop debug.beatwheel.synth 415.0
 *   adb shell setprop debug.beatwheel.synth 466.16:-0.3     (Hz, then cents of drift per minute)
 * and clear it with an empty value to use the microphone.
 */
object SignalSources {
    fun create(context: Context): SignalSource {
        val spec = systemProperty("debug.beatwheel.synth").trim()
        if (spec.isEmpty()) return MicSource(context)
        val parts = spec.split(':')
        val hz = parts[0].toDoubleOrNull() ?: return MicSource(context)
        val drift = parts.getOrNull(1)?.toDoubleOrNull() ?: 0.0
        return SyntheticSignalSource(hz, drift)
    }

    private fun systemProperty(key: String): String = try {
        Class.forName("android.os.SystemProperties")
            .getMethod("get", String::class.java)
            .invoke(null, key) as String
    } catch (e: Exception) {
        ""
    }
}
