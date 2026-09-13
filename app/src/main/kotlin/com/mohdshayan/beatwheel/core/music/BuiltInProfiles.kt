package com.mohdshayan.beatwheel.core.music

/** The shape of a profile without any storage concerns, so built-ins can be defined and tested on the JVM. */
data class ProfileSpec(
    val builtInKey: String,
    val name: String,
    val transpositionSemitones: Int,
    val minHz: Double,
    val maxHz: Double,
    val stringTargetsMidi: String = "",
    val heldToneMode: Boolean = false,
    val referenceAHz: Double = 440.0,
    val temperamentKey: String = Temperaments.EQUAL,
    val tonicPitchClass: Int = 0,
    val keepReferenceA: Boolean = true,
    val noiseGateDb: Float = -52f,
)

object BuiltInProfiles {
    val all: List<ProfileSpec> = listOf(
        ProfileSpec("chromatic", "Chromatic", 0, 27.5, 2500.0),
        ProfileSpec("guitar", "Guitar", 0, 70.0, 1400.0, "40,45,50,55,59,64"),
        ProfileSpec("ukulele", "Ukulele", 0, 230.0, 1400.0, "67,60,64,69"),
        ProfileSpec("violin", "Violin", 0, 180.0, 2500.0, "55,62,69,76"),
        ProfileSpec("viola", "Viola", 0, 120.0, 1800.0, "48,55,62,69"),
        ProfileSpec("cello", "Cello", 0, 60.0, 1200.0, "36,43,50,57"),
        ProfileSpec("double_bass", "Double bass", 0, 38.0, 500.0, "28,33,38,43"),
        ProfileSpec("flute", "Flute", 0, 250.0, 2500.0),
        ProfileSpec("oboe", "Oboe", 0, 220.0, 1800.0, heldToneMode = true),
        ProfileSpec("clarinet_bb", "Clarinet in B flat", -2, 140.0, 1700.0),
        ProfileSpec("alto_sax_eb", "Alto sax in E flat", -9, 130.0, 1000.0),
        ProfileSpec("tenor_sax_bb", "Tenor sax in B flat", -14, 100.0, 800.0),
        ProfileSpec("trumpet_bb", "Trumpet in B flat", -2, 160.0, 1100.0),
        ProfileSpec("horn_f", "Horn in F", -7, 60.0, 800.0),
        ProfileSpec("harmonium", "Harmonium", 0, 100.0, 1400.0, heldToneMode = true),
    )
}
