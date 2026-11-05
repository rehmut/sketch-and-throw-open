package io.github.rehmut.sketchandthrow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HandleSeedingTest {
    /** A 100 px wide body from x = 50 to 150, rows 0..200. */
    private val profile = VesselProfile(300, 300, (0..200).map { ProfileRow(it.toFloat(), 50f, 150f) })

    @Test fun probeSitsHalfwayAlongTheHandle() {
        val handle = VesselHandle(HandleSide.RIGHT,
            listOf(HandlePoint(150f, 50f, 10f), HandlePoint(190f, 60f, 10f),
                HandlePoint(200f, 100f, 10f), HandlePoint(190f, 140f, 10f), HandlePoint(150f, 150f, 10f)),
            50f, 150f, 50f, true)
        val probe = HandleSeeding.probe(handle)
        assertEquals(200f, probe.x, 2f)
        assertEquals(100f, probe.y, 2f)
    }

    @Test fun tapsBesideTheBodyAreHandleTaps() {
        assertTrue(HandleSeeding.isBesideBody(profile, 185f, 100f))
        assertTrue(HandleSeeding.isBesideBody(profile, 20f, 100f))
    }

    @Test fun tapsOnTheBodyOrFarAwayAreNot() {
        assertFalse(HandleSeeding.isBesideBody(profile, 100f, 100f))
        // Just outside the wall is still the body's own edge.
        assertFalse(HandleSeeding.isBesideBody(profile, 152f, 100f))
        // Above the rim or below the foot is not where a handle sits.
        assertFalse(HandleSeeding.isBesideBody(profile, 185f, -40f))
        assertFalse(HandleSeeding.isBesideBody(profile, 185f, 260f))
    }
}
