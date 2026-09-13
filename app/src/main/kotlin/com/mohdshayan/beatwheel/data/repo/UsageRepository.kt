package com.mohdshayan.beatwheel.data.repo

import com.mohdshayan.beatwheel.core.drift.ReviewPolicy
import com.mohdshayan.beatwheel.data.prefs.AppPrefs
import com.mohdshayan.beatwheel.data.prefs.ReviewState
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/** Successful-use bookkeeping for the one review prompt. Stored locally, never sent. */
class UsageRepository(private val prefs: AppPrefs) {
    private fun ReviewState.policy() = ReviewPolicy.State(successfulUses, lastSuccessDay, prompted)
    private fun ReviewPolicy.State.prefs() = ReviewState(successfulUses, lastSuccessDay, prompted)

    suspend fun recordHeldNote() {
        val s = prefs.review.first().policy()
        prefs.setReview(ReviewPolicy.afterHeldNote(s, LocalDate.now().toEpochDay()).prefs())
    }

    suspend fun recordSessionSaved() {
        prefs.setReview(ReviewPolicy.afterSessionSaved(prefs.review.first().policy()).prefs())
    }

    suspend fun shouldPrompt(audioRunning: Boolean): Boolean =
        ReviewPolicy.shouldPrompt(prefs.review.first().policy(), audioRunning)

    suspend fun markPrompted() {
        prefs.setReview(prefs.review.first().copy(prompted = true))
    }
}
