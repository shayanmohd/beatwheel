package com.mohdshayan.beatwheel.core.drift

/**
 * When to ask for a Play review: once, after the third successful use, where a held in-tune note
 * counts at most once per day and every saved session counts. Never while audio runs.
 */
object ReviewPolicy {
    const val USES_BEFORE_PROMPT = 3

    data class State(val successfulUses: Int, val lastSuccessDay: Long, val prompted: Boolean)

    fun afterHeldNote(state: State, epochDay: Long): State =
        if (state.lastSuccessDay == epochDay) state
        else state.copy(successfulUses = state.successfulUses + 1, lastSuccessDay = epochDay)

    fun afterSessionSaved(state: State): State = state.copy(successfulUses = state.successfulUses + 1)

    fun shouldPrompt(state: State, audioRunning: Boolean): Boolean =
        !state.prompted && !audioRunning && state.successfulUses >= USES_BEFORE_PROMPT
}
