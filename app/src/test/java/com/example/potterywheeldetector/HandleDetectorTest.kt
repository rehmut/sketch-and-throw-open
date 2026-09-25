package com.example.potterywheeldetector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Synthetic silhouettes: a 260 × 260 image with an upright cylindrical body
 * spanning x 90..170 and y 30..230, centred on the image like a real photo.
 */
class HandleDetectorTest {
    private val w = 260
    private val h = 260
    private val bodyLeft = 90
    private val bodyRight = 170

    private fun mask(vararg shapes: (Int, Int) -> Boolean) =
        BooleanArray(w * h) { i -> shapes.any { it(i % w, i / w) } }

    private val body: (Int, Int) -> Boolean = { x, y -> x in bodyLeft..bodyRight && y in 30..230 }

    /** A C-shaped strap handle: an elliptical ring, outer half only. */
    private fun ringHandle(edgeX: Int, sign: Int): (Int, Int) -> Boolean = { x, y ->
        val dx = (x - edgeX) * sign
        val dy = y - 130
        fun inside(rx: Double, ry: Double) =
            (dx * dx) / (rx * rx) + (dy * dy) / (ry * ry) <= 1.0
        dx >= 0 && inside(45.0, 60.0) && !inside(30.0, 45.0)
    }

    private fun profileOf(mask: BooleanArray, mirror: Boolean = false) =
        VesselProfileExtractor.extractFromMask(w, h, mask, null, null, mirror)

    @Test fun strapHandleOnTheRightIsDetected() {
        val profile = profileOf(mask(body, ringHandle(bodyRight, 1)))
        val handle = assertNotNull(profile).let { profile!!.handle }
        assertNotNull(handle)
        handle!!
        assertEquals(HandleSide.RIGHT, handle.side)
        assertTrue(handle.hasEye)
        assertEquals(45f, handle.projection, 5f)
        assertTrue("upper join ${handle.upperJoinY}", handle.upperJoinY in 62f..100f)
        assertTrue("lower join ${handle.lowerJoinY}", handle.lowerJoinY in 160f..198f)
        assertTrue(handle.path.size >= 8)
        // The midline of the ring sits between the outer (45) and inner (30) radius.
        assertEquals(bodyRight + 37.5f, handle.path.maxOf { it.x }, 5f)
        assertEquals(15f, handle.path.map { it.thickness }.sorted()[handle.path.size / 2], 5f)
    }

    @Test fun handleJoinsDoNotBulgeTheBodyOutline() {
        val profile = profileOf(mask(body, ringHandle(bodyRight, 1)))!!
        // Rows 70..85 and 175..190 are where the ring meets the body; without
        // correction the longest run there would stretch out to the handle.
        for (y in listOf(75, 80, 180, 185)) {
            val row = profile.rows.first { it.y.toInt() == y }
            assertEquals("row $y", bodyRight.toFloat(), row.right, 3f)
        }
    }

    @Test fun mirroredColourOutlineKeepsTheBodySymmetric() {
        val profile = profileOf(mask(body, ringHandle(bodyRight, 1)), mirror = true)!!
        assertNotNull(profile.handle)
        val row = profile.rows.first { it.y.toInt() == 80 }
        assertEquals(bodyLeft.toFloat(), row.left, 3f)
        assertEquals(bodyRight.toFloat(), row.right, 3f)
    }

    @Test fun handleOnTheLeftIsDetected() {
        val handle = profileOf(mask(body, ringHandle(bodyLeft, -1)))!!.handle
        assertNotNull(handle)
        assertEquals(HandleSide.LEFT, handle!!.side)
        assertEquals(bodyLeft - 37.5f, handle.path.minOf { it.x }, 5f)
    }

    @Test fun switchedOffDetectionLeavesTheOutlineAlone() {
        val profile = VesselProfileExtractor.extractFromMask(w, h, mask(body, ringHandle(bodyRight, 1)),
            null, null, mirrorMissingSide = false, detectHandles = false)!!
        assertNull(profile.handle)
        // Without detection the join rows keep their raw, handle-wide extent.
        assertTrue(profile.rows.first { it.y.toInt() == 82 }.right > bodyRight + 15f)
    }

    @Test fun plainCylinderHasNoHandle() {
        val profile = profileOf(mask(body))!!
        assertNull(profile.handle)
        assertEquals(bodyRight.toFloat(), profile.rows[100].right, 1f)
    }

    @Test fun castShadowAlongTheBaseIsNotAHandle() {
        val shadow: (Int, Int) -> Boolean = { x, y -> x in bodyRight..235 && y in 222..230 }
        assertNull(profileOf(mask(body, shadow))!!.handle)
    }

    @Test fun shortSpoutNearTheRimIsNotAHandle() {
        val spout: (Int, Int) -> Boolean = { x, y ->
            y in 36..48 && x in bodyRight..(bodyRight + 30 - (y - 36))
        }
        assertNull(profileOf(mask(body, spout))!!.handle)
    }

    @Test fun handleBrokenIntoPiecesIsTracedAsOne() {
        // An ML mask often loses the thin middle of a handle: two arcs remain.
        val ring = ringHandle(bodyRight, 1)
        val broken: (Int, Int) -> Boolean = { x, y -> ring(x, y) && y !in 120..140 }
        val profile = profileOf(mask(body, broken))!!
        val handle = profile.handle
        assertNotNull(handle)
        assertTrue("upper join ${handle!!.upperJoinY}", handle.upperJoinY < 100f)
        assertTrue("lower join ${handle.lowerJoinY}", handle.lowerJoinY > 160f)
        for (y in listOf(80, 180)) {
            assertEquals("row $y", bodyRight.toFloat(),
                profile.rows.first { it.y.toInt() == y }.right, 3f)
        }
    }

    @Test fun faintHandleIsRecoveredFromTheLooserMask() {
        // The body alone passes the strict threshold; the handle only the loose one.
        val strict = mask(body)
        val loose = mask(body, ringHandle(bodyRight, 1))
        val profile = VesselProfileExtractor.extractFromMask(w, h, strict, null, null,
            mirrorMissingSide = false, handleMask = loose)!!
        assertNotNull(profile.handle)
        assertEquals(45f, profile.handle!!.projection, 5f)
    }

    @Test fun looseMaskDoesNotAddAHandleThatIsNotThere() {
        val profile = VesselProfileExtractor.extractFromMask(w, h, mask(body), null, null,
            mirrorMissingSide = false, handleMask = mask(body))!!
        assertNull(profile.handle)
    }

    @Test fun solidLugHandleIsDetectedWithoutAnEye() {
        val lug: (Int, Int) -> Boolean = { x, y ->
            val dx = x - bodyRight
            val dy = y - 110
            dx >= 0 && dx * dx / (26.0 * 26.0) + dy * dy / (32.0 * 32.0) <= 1.0
        }
        val handle = profileOf(mask(body, lug))!!.handle
        assertNotNull(handle)
        assertFalse(handle!!.hasEye)
        assertEquals(26f, handle.projection, 4f)
    }
}
