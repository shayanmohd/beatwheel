package com.mohdshayan.beatwheel.audio

enum class StartResult { OK, NO_PERMISSION, BUSY }

/** A mono 48 kHz float input. The microphone in release builds; a debug build may substitute a synthetic tone. */
interface SignalSource {
    val sampleRate: Int
    fun start(): StartResult
    /** Blocks until [len] samples are read; returns the count, or a negative value on failure. */
    fun read(buffer: FloatArray, len: Int): Int
    /** True when the system has silenced this input because another app took the microphone. */
    fun isSilencedByOtherApp(): Boolean
    fun stop()
}
