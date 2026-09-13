package com.mohdshayan.beatwheel.data.repo

import com.mohdshayan.beatwheel.audio.AudioEngine
import com.mohdshayan.beatwheel.core.drift.DriftPoint
import com.mohdshayan.beatwheel.core.drift.DriftSummary
import com.mohdshayan.beatwheel.data.db.DriftReading
import com.mohdshayan.beatwheel.data.db.DriftSession
import com.mohdshayan.beatwheel.data.db.SessionDao
import com.mohdshayan.beatwheel.data.prefs.AppPrefs
import com.mohdshayan.beatwheel.data.prefs.Counter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.format.TextStyle
import java.util.Locale

data class RecorderState(
    val active: Boolean = false,
    val sessionId: Long = 0,
    val startedAt: Long = 0,
    val elapsedMs: Long = 0,
    val trace: List<DriftPoint> = emptyList(),
    val readingCount: Int = 0,
    val paused: Boolean = false,
    val defaultName: String = "",
)

/**
 * Records a drift session: ten readings a second while a note sounds, written to Room once a second,
 * for up to 60 minutes. Recording happens only while the tuner is on screen; time spent in the
 * background becomes a gap marker instead of silence pretending to be data.
 */
class DriftRecorder(
    private val engine: AudioEngine,
    private val tuner: TunerController,
    private val dao: SessionDao,
    private val prefs: AppPrefs,
    private val usage: UsageRepository,
    private val messages: UiMessages,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(RecorderState())
    val state: StateFlow<RecorderState> = _state.asStateFlow()

    private val lock = Mutex()
    private var job: Job? = null
    private val buffer = ArrayList<DriftReading>()
    private val trace = ArrayList<DriftPoint>()

    /** Offsets and duration come from the monotonic clock, so a wall-clock or time zone change mid-session cannot bend the chart. */
    @Volatile private var startNanos = 0L

    init {
        scope.launch { recoverUnfinished() }
    }

    fun start() {
        if (_state.value.active) return
        val tuning = tuner.active.value ?: return
        scope.launch {
            lock.withLock {
                // A second tap that raced the first finds the session already started.
                if (_state.value.active) return@withLock
                val now = System.currentTimeMillis()
                startNanos = System.nanoTime()
                val day = java.time.LocalDate.now().dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
                val defaultName = "$day ${tuning.profile.name.replaceFirstChar { it.lowercase() }}"
                val id = dao.insert(
                    DriftSession(
                        name = defaultName,
                        profileName = tuning.profile.name,
                        referenceAHz = tuning.profile.referenceAHz,
                        temperamentName = tuning.temperamentName,
                        transpositionSemitones = tuning.profile.transpositionSemitones,
                        startedAt = now,
                    ),
                )
                buffer.clear()
                trace.clear()
                _state.value = RecorderState(active = true, sessionId = id, startedAt = now, defaultName = defaultName)
                job = scope.launch { loop(id) }
            }
        }
    }

    private fun elapsedMs() = (System.nanoTime() - startNanos) / 1_000_000

    private suspend fun loop(sessionId: Long) {
        var ticks = 0
        var paused = false
        var count = 0
        while (scope.isActive) {
            delay(100)
            val offset = elapsedMs()
            if (offset >= MAX_MS) {
                scope.launch { stopAndSave(null) }
                return
            }
            val listening = engine.isListening
            lock.withLock {
                if (!listening) {
                    if (!paused) {
                        paused = true
                        buffer += DriftReading(sessionId = sessionId, offsetMs = offset, midi = 0, hz = 0f, cents = 0f, levelDb = 0f, gap = true)
                        trace += DriftPoint(offset, 0f, gap = true)
                    }
                } else {
                    paused = false
                    val e = engine.reading.value.estimate
                    if (e.hasPitch && e.strobeLocked) {
                        buffer += DriftReading(
                            sessionId = sessionId, offsetMs = offset, midi = e.midi, hz = e.hz.toFloat(),
                            cents = e.cents.toFloat(), levelDb = e.levelDb,
                        )
                        trace += DriftPoint(offset, e.cents.toFloat())
                        count++
                    }
                }
                ticks++
                if (ticks % 10 == 0) flush()
                if (ticks % 3 == 0) {
                    _state.value = _state.value.copy(
                        elapsedMs = offset,
                        trace = ArrayList(trace),
                        readingCount = count,
                        paused = paused,
                    )
                }
            }
        }
    }

    private suspend fun flush() {
        if (buffer.isEmpty()) return
        val batch = ArrayList(buffer)
        buffer.clear()
        // Stop and save cancels the loop; a batch already taken out of the buffer must still reach the database.
        withContext(NonCancellable) { dao.insertReadings(batch) }
    }

    /** Stops, writes the summary and returns the saved session id. A null name keeps the prefilled one. */
    suspend fun stopAndSave(name: String?): Long? {
        val current = _state.value
        if (!current.active) return null
        job?.cancel()
        job = null
        val id = current.sessionId
        lock.withLock {
            if (_state.value.sessionId != id || !_state.value.active) return null
            flush()
            val session = dao.get(id) ?: return null
            val finalName = name?.trim()?.takeIf { it.isNotEmpty() } ?: current.defaultName
            dao.update(finalize(session, elapsedMs()).copy(name = finalName))
            trace.clear()
            _state.value = RecorderState()
        }
        prefs.increment(Counter.SESSIONS_SAVED)
        usage.recordSessionSaved()
        messages.show("Session saved")
        return id
    }

    fun discard() {
        val current = _state.value
        if (!current.active) return
        job?.cancel()
        job = null
        scope.launch {
            lock.withLock {
                buffer.clear()
                trace.clear()
                dao.delete(current.sessionId)
                _state.value = RecorderState()
            }
        }
    }

    private suspend fun finalize(session: DriftSession, durationMs: Long): DriftSession {
        val readings = dao.readings(session.id)
        val stats = DriftSummary.summarize(readings.map { DriftPoint(it.offsetMs, it.cents, it.gap) })
        return session.copy(
            durationMs = durationMs.coerceAtLeast(1),
            readingCount = readings.count { !it.gap },
            startCents = stats?.startCents,
            endCents = stats?.endCents,
            minCents = stats?.minCents,
            maxCents = stats?.maxCents,
            driftCentsPerMinute = stats?.centsPerMinute,
        )
    }

    /** A recording cut short by the process dying is kept up to its last reading. */
    private suspend fun recoverUnfinished() = lock.withLock {
        // Under the lock, so a recording started in the first moments after launch is never mistaken for a dead one.
        for (s in dao.unfinished()) {
            if (s.id == _state.value.sessionId) continue
            val readings = dao.readings(s.id)
            if (readings.none { !it.gap }) dao.delete(s.id)
            else dao.update(finalize(s, readings.last().offsetMs))
        }
    }

    companion object {
        const val MAX_MS = 60 * 60 * 1000L
    }
}
