package com.mohdshayan.beatwheel.review

import android.app.Activity
import com.google.android.play.core.review.ReviewManagerFactory
import com.google.android.play.core.ktx.launchReview
import com.google.android.play.core.ktx.requestReview
import com.mohdshayan.beatwheel.di.ServiceLocator

/** Asks for a Play review once, after the third successful use, never on first launch and never while audio runs. */
object ReviewPrompter {
    suspend fun maybePrompt(activity: Activity) {
        val engine = ServiceLocator.audioEngine
        val audioRunning = engine.isListening || engine.drone.value.playing
        val usage = ServiceLocator.usage
        if (!usage.shouldPrompt(audioRunning)) return
        try {
            val manager = ReviewManagerFactory.create(activity)
            val info = manager.requestReview()
            usage.markPrompted()
            manager.launchReview(activity, info)
        } catch (_: Exception) {
            // Without the Play Store there is nothing to show; the app carries on.
        }
    }
}
