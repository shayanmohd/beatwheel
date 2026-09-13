package com.mohdshayan.beatwheel.data.repo

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/** One app-wide snackbar channel, so a message survives the navigation that follows its action. */
class UiMessages {
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages

    fun show(text: String) {
        _messages.tryEmit(text)
    }
}
