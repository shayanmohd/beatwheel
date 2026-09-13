package com.mohdshayan.beatwheel.audio

import android.content.Context

/** Release builds always measure the microphone. */
object SignalSources {
    fun create(context: Context): SignalSource = MicSource(context)
}
