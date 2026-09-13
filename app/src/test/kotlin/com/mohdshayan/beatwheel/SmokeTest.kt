package com.mohdshayan.beatwheel

import com.mohdshayan.beatwheel.ui.theme.RadiusLg
import com.mohdshayan.beatwheel.ui.theme.RadiusMd
import com.mohdshayan.beatwheel.ui.theme.RadiusSm
import org.junit.Assert.assertTrue
import org.junit.Test

/** Proves the JVM unit-test classpath is wired. Replace with tests of the app's own logic. */
class SmokeTest {
    @Test
    fun radiusScaleIsOrdered() {
        assertTrue(RadiusSm < RadiusMd)
        assertTrue(RadiusMd < RadiusLg)
    }
}
