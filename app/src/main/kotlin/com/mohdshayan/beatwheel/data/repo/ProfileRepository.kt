package com.mohdshayan.beatwheel.data.repo

import com.mohdshayan.beatwheel.core.music.BuiltInProfiles
import com.mohdshayan.beatwheel.core.music.NoteNames
import com.mohdshayan.beatwheel.core.music.ProfileSpec
import com.mohdshayan.beatwheel.core.music.Temperaments
import com.mohdshayan.beatwheel.data.db.CustomTemperament
import com.mohdshayan.beatwheel.data.db.InstrumentProfile
import com.mohdshayan.beatwheel.data.db.ProfileDao
import com.mohdshayan.beatwheel.data.db.TemperamentDao
import com.mohdshayan.beatwheel.data.prefs.AppPrefs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

fun ProfileSpec.toEntity(sortOrder: Int) = InstrumentProfile(
    name = name,
    builtInKey = builtInKey,
    transpositionSemitones = transpositionSemitones,
    referenceAHz = referenceAHz,
    temperamentKey = temperamentKey,
    tonicPitchClass = tonicPitchClass,
    keepReferenceA = keepReferenceA,
    minHz = minHz,
    maxHz = maxHz,
    stringTargetsMidi = stringTargetsMidi,
    heldToneMode = heldToneMode,
    noiseGateDb = noiseGateDb,
    sortOrder = sortOrder,
)

/** A temperament as the tuner uses it: a display name and twelve base offsets with C as tonic. */
data class TemperamentChoice(val key: String, val name: String, val baseOffsets: DoubleArray)

class ProfileRepository(
    private val profileDao: ProfileDao,
    private val temperamentDao: TemperamentDao,
    private val prefs: AppPrefs,
) {
    private val seedLock = Mutex()

    val profiles: Flow<List<InstrumentProfile>> = profileDao.observeAll()
    val temperaments: Flow<List<CustomTemperament>> = temperamentDao.observeAll()

    /** Seeds the fifteen built-in profiles on first open and makes Chromatic active. */
    suspend fun ensureSeeded() = seedLock.withLock {
        if (profileDao.builtInCount() == 0) {
            val ids = profileDao.insertAll(BuiltInProfiles.all.mapIndexed { i, spec -> spec.toEntity(i) })
            if (prefs.activeProfileId.first() < 0) prefs.setActiveProfileId(ids.first())
        }
    }

    suspend fun get(id: Long) = profileDao.get(id)

    suspend fun select(id: Long) = prefs.setActiveProfileId(id)

    suspend fun save(profile: InstrumentProfile): Long =
        if (profile.id == 0L) profileDao.insert(profile) else {
            profileDao.update(profile)
            profile.id
        }

    suspend fun copy(profile: InstrumentProfile): Long {
        val names = profileDao.all().map { it.name }.toSet()
        var name = "${profile.name} copy"
        var n = 2
        while (name in names) name = "${profile.name} copy ${n++}"
        return profileDao.insert(profile.copy(id = 0, name = name, builtInKey = null, sortOrder = 1_000))
    }

    suspend fun delete(profile: InstrumentProfile) {
        if (profile.builtInKey != null) return
        if (prefs.activeProfileId.first() == profile.id) {
            profileDao.all().firstOrNull { it.builtInKey == "chromatic" }?.let { prefs.setActiveProfileId(it.id) }
        }
        profileDao.delete(profile)
    }

    suspend fun getTemperament(id: Long) = temperamentDao.get(id)

    suspend fun saveTemperament(t: CustomTemperament): Long =
        if (t.id == 0L) temperamentDao.insert(t) else {
            temperamentDao.update(t)
            t.id
        }

    /** Deletes a custom temperament; profiles that used it fall back to equal temperament. */
    suspend fun deleteTemperament(id: Long) {
        val key = Temperaments.customKey(id)
        profileDao.all().filter { it.temperamentKey == key }.forEach {
            profileDao.update(it.copy(temperamentKey = Temperaments.EQUAL))
        }
        temperamentDao.delete(id)
    }

    companion object {
        fun choices(custom: List<CustomTemperament>): List<TemperamentChoice> =
            Temperaments.presets.map { TemperamentChoice(it.key, it.name, it.offsetsCents) } +
                custom.mapNotNull { t ->
                    Temperaments.parseOffsets(t.offsetsCents)?.let {
                        TemperamentChoice(Temperaments.customKey(t.id), t.name, it)
                    }
                }

        fun resolve(key: String, custom: List<CustomTemperament>): TemperamentChoice =
            choices(custom).firstOrNull { it.key == key }
                ?: TemperamentChoice(Temperaments.EQUAL, "Equal", DoubleArray(12))

        /** The temperament as a player names it: "Just on D", or plain "Equal" where the tonic changes nothing. */
        fun describe(profile: InstrumentProfile, custom: List<CustomTemperament>): String {
            val choice = resolve(profile.temperamentKey, custom)
            return if (choice.key == Temperaments.EQUAL || profile.tonicPitchClass == 0) choice.name
            else choice.name + " on " + NoteNames.pitchClassSpoken(profile.tonicPitchClass)
        }
    }
}
