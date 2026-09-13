package com.mohdshayan.beatwheel.core.drift

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class BackupProfile(
    val id: Long,
    val name: String,
    val builtInKey: String? = null,
    val transpositionSemitones: Int = 0,
    val referenceAHz: Double = 440.0,
    val temperamentKey: String = "equal",
    val tonicPitchClass: Int = 0,
    val keepReferenceA: Boolean = true,
    val minHz: Double = 27.5,
    val maxHz: Double = 2500.0,
    val stringTargetsMidi: String = "",
    val heldToneMode: Boolean = false,
    val noiseGateDb: Float = -52f,
    val sortOrder: Int = 0,
)

@Serializable
data class BackupTemperament(
    val id: Long,
    val name: String,
    val offsetsCents: String,
    val createdAt: Long = 0,
)

@Serializable
data class BackupReading(
    val offsetMs: Long,
    val midi: Int,
    val hz: Float,
    val cents: Float,
    val levelDb: Float = 0f,
    val gap: Boolean = false,
)

@Serializable
data class BackupSession(
    val id: Long = 0,
    val name: String,
    val note: String = "",
    val profileName: String = "",
    val referenceAHz: Double = 440.0,
    val temperamentName: String = "Equal",
    val transpositionSemitones: Int = 0,
    val startedAt: Long = 0,
    val durationMs: Long = 0,
    val readingCount: Int = 0,
    val startCents: Float? = null,
    val endCents: Float? = null,
    val minCents: Float? = null,
    val maxCents: Float? = null,
    val driftCentsPerMinute: Float? = null,
    val readings: List<BackupReading> = emptyList(),
)

@Serializable
data class BackupFile(
    @SerialName("format") val format: Int? = null,
    val exportedAt: Long = 0,
    val profiles: List<BackupProfile> = emptyList(),
    val customTemperaments: List<BackupTemperament> = emptyList(),
    val sessions: List<BackupSession> = emptyList(),
)

sealed interface BackupParse {
    data class Ok(val file: BackupFile) : BackupParse
    data object NotABackup : BackupParse
}

object BackupCodec {
    const val FORMAT = 1

    @PublishedApi internal val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    fun encode(file: BackupFile): String = json.encodeToString(BackupFile.serializer(), file.copy(format = FORMAT))

    /**
     * Writes the same document as [encode], one session at a time: [head] carries everything but the
     * sessions, and [nextSession] is called until it returns null. A year of rehearsals never sits in memory at once.
     */
    inline fun write(out: java.io.Writer, head: BackupFile, nextSession: () -> BackupSession?) {
        out.write("{\"format\":$FORMAT,\"exportedAt\":${head.exportedAt},\"profiles\":")
        out.write(json.encodeToString(ListSerializer(BackupProfile.serializer()), head.profiles))
        out.write(",\"customTemperaments\":")
        out.write(json.encodeToString(ListSerializer(BackupTemperament.serializer()), head.customTemperaments))
        out.write(",\"sessions\":[")
        var first = true
        while (true) {
            val session = nextSession() ?: break
            if (!first) out.write(",")
            first = false
            out.write(json.encodeToString(BackupSession.serializer(), session))
        }
        out.write("]}")
    }

    fun decode(text: String): BackupParse = check { json.decodeFromString(BackupFile.serializer(), text) }

    /** Parses straight from the file, without first copying it into a string twice its size. */
    @OptIn(ExperimentalSerializationApi::class)
    fun decode(input: java.io.InputStream): BackupParse = check { json.decodeFromStream(BackupFile.serializer(), input) }

    private inline fun check(parse: () -> BackupFile): BackupParse {
        val file = try {
            parse()
        } catch (e: java.io.IOException) {
            throw e
        } catch (e: Exception) {
            return BackupParse.NotABackup
        } catch (e: StackOverflowError) {
            // Pathologically nested JSON is not a backup either.
            return BackupParse.NotABackup
        }
        val format = file.format ?: return BackupParse.NotABackup
        if (format < 1 || format > FORMAT) return BackupParse.NotABackup
        return BackupParse.Ok(file)
    }

    fun fileName(date: java.time.LocalDate): String = "beatwheel-backup-$date.json"
}

/**
 * A backup file is user input: it may be hand-edited or come from a newer or broken exporter. Values the
 * meter divides by or takes logarithms of are pulled back into the ranges the editors allow, so an
 * imported profile can never crash the analysis thread or read nonsense.
 */
object ImportSanitizer {
    private const val MIN_RANGE_HZ = 20.0
    private const val MAX_RANGE_HZ = 5000.0

    fun profile(p: BackupProfile): BackupProfile {
        val reference = p.referenceAHz.takeIf { it.isFinite() && it in 415.0..466.0 }
            ?.let { Math.round(it * 10) / 10.0 } ?: 440.0
        val rangeOk = p.minHz.isFinite() && p.maxHz.isFinite() &&
            p.minHz >= MIN_RANGE_HZ && p.maxHz <= MAX_RANGE_HZ && p.minHz * 1.5 <= p.maxHz
        val strings = p.stringTargetsMidi.split(',')
            .mapNotNull { it.trim().toIntOrNull()?.takeIf { m -> m in 0..127 } }
            .joinToString(",")
        return p.copy(
            name = p.name.trim().take(40).ifEmpty { "Imported profile" },
            transpositionSemitones = p.transpositionSemitones.coerceIn(-36, 36),
            referenceAHz = reference,
            tonicPitchClass = ((p.tonicPitchClass % 12) + 12) % 12,
            minHz = if (rangeOk) p.minHz else 27.5,
            maxHz = if (rangeOk) p.maxHz else 2500.0,
            stringTargetsMidi = strings,
            noiseGateDb = if (p.noiseGateDb.isFinite()) p.noiseGateDb.coerceIn(-70f, -30f) else -52f,
        )
    }

    fun temperament(t: BackupTemperament): BackupTemperament =
        t.copy(name = t.name.trim().take(40).ifEmpty { "Imported temperament" })

    /**
     * Drops readings that carry no usable number and keeps them in time order. A session without a real start
     * time (zero or negative) takes [fallbackStartedAt], so it is not filed under January 1970.
     */
    fun session(s: BackupSession, fallbackStartedAt: Long = System.currentTimeMillis()): BackupSession {
        val readings = s.readings
            .filter { it.offsetMs >= 0 && (it.gap || (it.hz.isFinite() && it.hz > 0f && it.cents.isFinite())) }
            .sortedBy { it.offsetMs }
        fun Float?.finite() = this?.takeIf { it.isFinite() }
        // A file without a usable summary gets one worked out from its readings, so the list never says "No data" beside a chart.
        val stats = if (s.startCents.finite() == null || s.driftCentsPerMinute.finite() == null) {
            DriftSummary.summarize(readings.map { DriftPoint(it.offsetMs, it.cents, it.gap) })
        } else null
        return s.copy(
            name = s.name.trim().take(60).ifEmpty { "Imported session" },
            note = s.note.take(500),
            profileName = s.profileName.trim().ifEmpty { "Unknown profile" },
            startedAt = if (s.startedAt > 0) s.startedAt else fallbackStartedAt,
            referenceAHz = s.referenceAHz.takeIf { it.isFinite() && it > 0 } ?: 440.0,
            transpositionSemitones = s.transpositionSemitones.coerceIn(-36, 36),
            durationMs = maxOf(s.durationMs, readings.lastOrNull()?.offsetMs ?: 0L, 1L),
            readingCount = readings.count { !it.gap },
            startCents = if (stats != null) stats.startCents else s.startCents.finite(),
            endCents = if (stats != null) stats.endCents else s.endCents.finite(),
            minCents = if (stats != null) stats.minCents else s.minCents.finite(),
            maxCents = if (stats != null) stats.maxCents else s.maxCents.finite(),
            driftCentsPerMinute = if (stats != null) stats.centsPerMinute else s.driftCentsPerMinute.finite(),
            readings = readings,
        )
    }
}

/** Name handling for imports: a clash never overwrites, it gets a suffix. */
object ImportNames {
    const val SUFFIX = " (imported)"

    fun uniqueName(wanted: String, taken: Set<String>): String {
        if (wanted !in taken) return wanted
        var candidate = wanted + SUFFIX
        var n = 2
        while (candidate in taken) {
            candidate = "$wanted (imported $n)"
            n++
        }
        return candidate
    }
}
