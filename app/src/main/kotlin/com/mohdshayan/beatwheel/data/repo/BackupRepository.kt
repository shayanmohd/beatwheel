package com.mohdshayan.beatwheel.data.repo

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.room.withTransaction
import com.mohdshayan.beatwheel.core.drift.BackupCodec
import com.mohdshayan.beatwheel.core.drift.BackupFile
import com.mohdshayan.beatwheel.core.drift.BackupParse
import com.mohdshayan.beatwheel.core.drift.BackupProfile
import com.mohdshayan.beatwheel.core.drift.BackupReading
import com.mohdshayan.beatwheel.core.drift.BackupSession
import com.mohdshayan.beatwheel.core.drift.BackupTemperament
import com.mohdshayan.beatwheel.core.drift.ImportNames
import com.mohdshayan.beatwheel.core.drift.ImportSanitizer
import com.mohdshayan.beatwheel.core.drift.SessionCsv
import com.mohdshayan.beatwheel.core.music.Temperaments
import com.mohdshayan.beatwheel.data.db.AppDatabase
import com.mohdshayan.beatwheel.data.db.CustomTemperament
import com.mohdshayan.beatwheel.data.db.DriftReading
import com.mohdshayan.beatwheel.data.db.DriftSession
import com.mohdshayan.beatwheel.data.db.InstrumentProfile
import com.mohdshayan.beatwheel.data.prefs.AppPrefs
import com.mohdshayan.beatwheel.data.prefs.Counter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

sealed interface ImportResult {
    data class Imported(val profiles: Int, val temperaments: Int, val sessions: Int) : ImportResult
    data object NotABackup : ImportResult
    data object Unreadable : ImportResult
    data object TooLarge : ImportResult
}

/** Everything the user made, out to a file and back in. Import never overwrites: it appends and renames. */
class BackupRepository(
    private val context: Context,
    private val db: AppDatabase,
    private val prefs: AppPrefs,
) {
    private val profileDao = db.profileDao()
    private val temperamentDao = db.temperamentDao()
    private val sessionDao = db.sessionDao()

    suspend fun exportBackup(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val head = BackupFile(
            exportedAt = System.currentTimeMillis(),
            profiles = profileDao.all().map { it.toBackup() },
            customTemperaments = temperamentDao.all().map { BackupTemperament(it.id, it.name, it.offsetsCents, it.createdAt) },
        )
        val sessions = sessionDao.all().filter { it.durationMs > 0 }.iterator()
        val ok = try {
            openForWriting(uri)?.bufferedWriter(Charsets.UTF_8)?.use { w ->
                // Sessions are read and written one at a time, so an export of a year of rehearsals stays small in memory.
                BackupCodec.write(w, head) {
                    if (sessions.hasNext()) sessions.next().let { it.toBackup(sessionDao.readings(it.id)) } else null
                }
            } != null
        } catch (e: Exception) {
            false
        }
        if (ok) prefs.increment(Counter.EXPORTS)
        ok
    }

    suspend fun importBackup(uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        val parsed = try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                BackupCodec.decode(CappedInputStream(input.buffered(), MAX_IMPORT_BYTES))
            }
        } catch (e: CappedInputStream.TooLarge) {
            return@withContext ImportResult.TooLarge
        } catch (e: OutOfMemoryError) {
            return@withContext ImportResult.TooLarge
        } catch (e: Exception) {
            null
        } ?: return@withContext ImportResult.Unreadable
        when (parsed) {
            BackupParse.NotABackup -> ImportResult.NotABackup
            is BackupParse.Ok -> try {
                merge(parsed.file)
            } catch (e: android.database.SQLException) {
                ImportResult.Unreadable
            }
        }
    }

    private suspend fun merge(file: BackupFile): ImportResult = db.withTransaction {
        val tempIds = HashMap<Long, Long>()
        val tempNames = temperamentDao.all().map { it.name }.toMutableSet()
        for (t in file.customTemperaments.map(ImportSanitizer::temperament)) {
            if (Temperaments.parseOffsets(t.offsetsCents) == null) continue
            val name = ImportNames.uniqueName(t.name, tempNames)
            tempNames += name
            tempIds[t.id] = temperamentDao.insert(CustomTemperament(name = name, offsetsCents = t.offsetsCents, createdAt = t.createdAt))
        }

        val existing = profileDao.all()
        val profileNames = existing.map { it.name }.toMutableSet()
        var profiles = 0
        for (p in file.profiles.map(ImportSanitizer::profile)) {
            val key = Temperaments.customId(p.temperamentKey)?.let { old ->
                tempIds[old]?.let { Temperaments.customKey(it) } ?: Temperaments.EQUAL
            } ?: p.temperamentKey.takeIf { Temperaments.preset(it) != null } ?: Temperaments.EQUAL
            val incoming = p.toEntity().copy(temperamentKey = key)
            val local = p.builtInKey?.let { k -> existing.firstOrNull { it.builtInKey == k } }
            if (local != null && sameSettings(local, incoming)) continue
            if (existing.any { sameSettings(it, incoming) && it.name == incoming.name }) continue
            val name = ImportNames.uniqueName(p.name, profileNames)
            profileNames += name
            profileDao.insert(incoming.copy(id = 0, name = name, builtInKey = null, sortOrder = 1_000))
            profiles++
        }

        val fallbackStart = if (file.exportedAt > 0) file.exportedAt else System.currentTimeMillis()
        val sessions = file.sessions.map { ImportSanitizer.session(it, fallbackStart) }
        for (s in sessions) {
            val id = sessionDao.insert(s.toEntity())
            sessionDao.insertReadings(
                s.readings.map { DriftReading(sessionId = id, offsetMs = it.offsetMs, midi = it.midi, hz = it.hz, cents = it.cents, levelDb = it.levelDb, gap = it.gap) },
            )
        }
        ImportResult.Imported(profiles, tempIds.size, sessions.size)
    }

    suspend fun exportCsv(sessionId: Long, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val csv = csvFor(sessionId) ?: return@withContext false
        val ok = writeText(uri, csv)
        if (ok) prefs.increment(Counter.EXPORTS)
        ok
    }

    /** Writes the CSV to the app cache and returns a share-sheet intent for it. */
    suspend fun shareCsvIntent(sessionId: Long): Intent? = withContext(Dispatchers.IO) {
        val session = sessionDao.get(sessionId) ?: return@withContext null
        val csv = csvFor(sessionId) ?: return@withContext null
        val uri = try {
            val dir = File(context.cacheDir, "exports").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val file = File(dir, SessionCsv.fileName(session.name))
            file.writeText(csv)
            FileProvider.getUriForFile(context, context.packageName + ".files", file)
        } catch (e: Exception) {
            return@withContext null
        }
        prefs.increment(Counter.EXPORTS)
        Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, session.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private suspend fun csvFor(sessionId: Long): String? {
        val session = sessionDao.get(sessionId) ?: return null
        return SessionCsv.write(session.toBackup(sessionDao.readings(sessionId)))
    }

    private fun writeText(uri: Uri, text: String): Boolean = try {
        openForWriting(uri)?.use { it.write(text.toByteArray(Charsets.UTF_8)) } != null
    } catch (e: Exception) {
        false
    }

    private fun sameSettings(a: InstrumentProfile, b: InstrumentProfile) =
        a.transpositionSemitones == b.transpositionSemitones &&
            a.referenceAHz == b.referenceAHz &&
            a.temperamentKey == b.temperamentKey &&
            a.tonicPitchClass == b.tonicPitchClass &&
            a.keepReferenceA == b.keepReferenceA &&
            a.minHz == b.minHz &&
            a.maxHz == b.maxHz &&
            a.stringTargetsMidi == b.stringTargetsMidi &&
            a.heldToneMode == b.heldToneMode &&
            a.noiseGateDb == b.noiseGateDb

    /** Some storage providers refuse truncate mode on a new document; plain write mode is the same for an empty file. */
    private fun openForWriting(uri: Uri): java.io.OutputStream? = try {
        context.contentResolver.openOutputStream(uri, "wt")
    } catch (e: java.io.FileNotFoundException) {
        context.contentResolver.openOutputStream(uri, "w")
    } catch (e: IllegalArgumentException) {
        context.contentResolver.openOutputStream(uri, "w")
    }

    companion object {
        private const val MAX_IMPORT_BYTES = 256L * 1024 * 1024
    }
}

/** Refuses to read past [limit] bytes, so an endless or enormous file fails fast instead of exhausting memory. */
private class CappedInputStream(input: java.io.InputStream, private val limit: Long) : java.io.FilterInputStream(input) {
    class TooLarge : java.io.IOException("file larger than the import limit")

    private var count = 0L

    private fun add(n: Int): Int {
        if (n > 0) {
            count += n
            if (count > limit) throw TooLarge()
        }
        return n
    }

    override fun read(): Int = super.read().also { if (it >= 0) add(1) }

    override fun read(b: ByteArray, off: Int, len: Int): Int = add(super.read(b, off, len))
}

private fun InstrumentProfile.toBackup() = BackupProfile(
    id, name, builtInKey, transpositionSemitones, referenceAHz, temperamentKey, tonicPitchClass,
    keepReferenceA, minHz, maxHz, stringTargetsMidi, heldToneMode, noiseGateDb, sortOrder,
)

private fun BackupProfile.toEntity() = InstrumentProfile(
    id, name, builtInKey, transpositionSemitones, referenceAHz, temperamentKey, tonicPitchClass,
    keepReferenceA, minHz, maxHz, stringTargetsMidi, heldToneMode, noiseGateDb, sortOrder,
)

fun DriftSession.toBackup(readings: List<DriftReading>) = BackupSession(
    id, name, note, profileName, referenceAHz, temperamentName, transpositionSemitones, startedAt, durationMs,
    readingCount, startCents, endCents, minCents, maxCents, driftCentsPerMinute,
    readings.map { BackupReading(it.offsetMs, it.midi, it.hz, it.cents, it.levelDb, it.gap) },
)

private fun BackupSession.toEntity() = DriftSession(
    name = name, note = note, profileName = profileName, referenceAHz = referenceAHz, temperamentName = temperamentName,
    transpositionSemitones = transpositionSemitones, startedAt = startedAt, durationMs = durationMs.coerceAtLeast(1),
    readingCount = readingCount, startCents = startCents, endCents = endCents, minCents = minCents, maxCents = maxCents,
    driftCentsPerMinute = driftCentsPerMinute,
)
