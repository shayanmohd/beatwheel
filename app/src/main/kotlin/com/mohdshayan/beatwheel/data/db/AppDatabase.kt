package com.mohdshayan.beatwheel.data.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM instrument_profiles ORDER BY (builtInKey IS NULL), sortOrder, name COLLATE NOCASE")
    fun observeAll(): Flow<List<InstrumentProfile>>

    @Query("SELECT * FROM instrument_profiles ORDER BY (builtInKey IS NULL), sortOrder, name COLLATE NOCASE")
    suspend fun all(): List<InstrumentProfile>

    @Query("SELECT * FROM instrument_profiles WHERE id = :id")
    fun observe(id: Long): Flow<InstrumentProfile?>

    @Query("SELECT * FROM instrument_profiles WHERE id = :id")
    suspend fun get(id: Long): InstrumentProfile?

    @Query("SELECT COUNT(*) FROM instrument_profiles WHERE builtInKey IS NOT NULL")
    suspend fun builtInCount(): Int

    @Query("SELECT COUNT(*) FROM instrument_profiles")
    fun observeCount(): Flow<Int>

    @Insert
    suspend fun insert(profile: InstrumentProfile): Long

    @Insert
    suspend fun insertAll(profiles: List<InstrumentProfile>): List<Long>

    @Update
    suspend fun update(profile: InstrumentProfile)

    @Delete
    suspend fun delete(profile: InstrumentProfile)
}

@Dao
interface TemperamentDao {
    @Query("SELECT * FROM custom_temperaments ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<CustomTemperament>>

    @Query("SELECT * FROM custom_temperaments ORDER BY name COLLATE NOCASE")
    suspend fun all(): List<CustomTemperament>

    @Query("SELECT * FROM custom_temperaments WHERE id = :id")
    suspend fun get(id: Long): CustomTemperament?

    @Insert
    suspend fun insert(temperament: CustomTemperament): Long

    @Update
    suspend fun update(temperament: CustomTemperament)

    @Query("DELETE FROM custom_temperaments WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface SessionDao {
    @Query("SELECT * FROM drift_sessions WHERE durationMs > 0 OR readingCount > 0 ORDER BY startedAt DESC")
    fun observeSaved(): Flow<List<DriftSession>>

    @Query("SELECT * FROM drift_sessions ORDER BY startedAt DESC")
    suspend fun all(): List<DriftSession>

    @Query("SELECT * FROM drift_sessions WHERE id = :id")
    fun observe(id: Long): Flow<DriftSession?>

    @Query("SELECT * FROM drift_sessions WHERE id = :id")
    suspend fun get(id: Long): DriftSession?

    @Query("SELECT * FROM drift_readings WHERE sessionId = :sessionId ORDER BY offsetMs")
    fun observeReadings(sessionId: Long): Flow<List<DriftReading>>

    @Query("SELECT * FROM drift_readings WHERE sessionId = :sessionId ORDER BY offsetMs")
    suspend fun readings(sessionId: Long): List<DriftReading>

    @Query("SELECT COUNT(*) FROM drift_sessions WHERE durationMs > 0 OR readingCount > 0")
    fun observeCount(): Flow<Int>

    @Insert
    suspend fun insert(session: DriftSession): Long

    @Update
    suspend fun update(session: DriftSession)

    @Insert
    suspend fun insertReadings(readings: List<DriftReading>)

    @Query("DELETE FROM drift_sessions WHERE id = :id")
    suspend fun delete(id: Long)

    /** Sessions left unfinished when the process died mid-recording. */
    @Query("SELECT * FROM drift_sessions WHERE durationMs = 0")
    suspend fun unfinished(): List<DriftSession>
}

@Database(
    entities = [InstrumentProfile::class, CustomTemperament::class, DriftSession::class, DriftReading::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun temperamentDao(): TemperamentDao
    abstract fun sessionDao(): SessionDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "beatwheel.db",
                ).build().also { instance = it }
            }
    }
}
