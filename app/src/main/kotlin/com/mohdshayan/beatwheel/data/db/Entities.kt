package com.mohdshayan.beatwheel.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "instrument_profiles")
data class InstrumentProfile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
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

@Entity(tableName = "custom_temperaments")
data class CustomTemperament(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val offsetsCents: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "drift_sessions")
data class DriftSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val note: String = "",
    val profileName: String,
    val referenceAHz: Double,
    val temperamentName: String,
    val transpositionSemitones: Int,
    val startedAt: Long,
    val durationMs: Long = 0,
    val readingCount: Int = 0,
    val startCents: Float? = null,
    val endCents: Float? = null,
    val minCents: Float? = null,
    val maxCents: Float? = null,
    val driftCentsPerMinute: Float? = null,
)

@Entity(
    tableName = "drift_readings",
    foreignKeys = [
        ForeignKey(
            entity = DriftSession::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId")],
)
data class DriftReading(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val offsetMs: Long,
    val midi: Int,
    val hz: Float,
    val cents: Float,
    val levelDb: Float,
    val gap: Boolean = false,
)
