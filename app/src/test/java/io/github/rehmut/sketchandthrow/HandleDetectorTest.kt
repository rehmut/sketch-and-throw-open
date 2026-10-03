package io.github.rehmut.sketchandthrow

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
    private fun ringHandle(edgeX: Int, sign: Int, centreY: Int = 130): (Int, Int) -> Boolean = { x, y ->
        val dx = (x - edgeX) * sign
        val dy = y - centreY
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

    @Test fun sliverAlongTheBodyBelowTheJoinIsNotPartOfTheHandle() {
        // A sliver of background hugging the body's bulge, joined to the handle's
        // lower end: the midline must stop where the handle meets the wall.
        val sliver: (Int, Int) -> Boolean = { x, y -> x in bodyRight + 1..bodyRight + 4 && y in 160..225 }
        val handle = profileOf(mask(body, ringHandle(bodyRight, 1, centreY = 110), sliver))!!.handle!!
        assertTrue("lower join ${handle.lowerJoinY}", handle.lowerJoinY < 180f)
        assertTrue("path reaches ${handle.path.maxOf { it.y }}", handle.path.maxOf { it.y } < 180f)
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

    @Test fun handlePassFindsTheHandleButNeverReshapesTheBody() {
        // First pass: only the body. Second pass, seeded on the handle: unsure of the
        // whole handle and of the eye alike, and wrongly taking in a patch above the
        // rim. Colour tells the handle from the eye: the eye is background-coloured.
        val bodyOnly = FloatArray(w * h) { i -> if (body(i % w, i / w)) 0.95f else 0.02f }
        val ring = ringHandle(bodyRight, 1)
        fun inEye(x: Int, y: Int): Boolean {
            val dx = x - bodyRight
            val dy = y - 130
            return dx >= 0 && dx * dx / 900.0 + dy * dy / 2025.0 < 1.0
        }
        val handlePass = FloatArray(w * h) { i ->
            val x = i % w
            val y = i / w
            when {
                x in 100..160 && y in 12..29 -> 0.9f
                ring(x, y) || inEye(x, y) -> 0.35f
                else -> 0.02f
            }
        }
        val notBackground = BooleanArray(w * h) { i -> body(i % w, i / w) || ring(i % w, i / w) }
        val profile = VesselProfileExtractor.extractFromConfidence(w, h, bodyOnly,
            handleConfidence = handlePass, handleColourMask = notBackground)!!
        val handle = profile.handle
        assertNotNull(handle)
        assertTrue("the eye must stay open", handle!!.hasEye)
        assertTrue("upper join ${handle.upperJoinY}", handle.upperJoinY < 100f)
        assertTrue("lower join ${handle.lowerJoinY}", handle.lowerJoinY > 160f)
        assertEquals(30f, profile.top, 1f)
    }

    @Test fun tiltedRimCornerIsNotMistakenForHandle() {
        // The rim leans: the body's top-left corner is missing in the strict mask,
        // but the looser mask fills it in. That corner sits over the body, not
        // beside it, and must not become the start of the handle.
        val leanRim: (Int, Int) -> Boolean = { x, y -> body(x, y) && y >= 30 + (bodyRight - x) * 16 / 80 }
        // The handle joins just under the rim, as on most mugs.
        val strict = mask(leanRim, ringHandle(bodyLeft, -1, centreY = 110))
        val loose = mask(body, ringHandle(bodyLeft, -1, centreY = 110))
        val handle = VesselProfileExtractor.extractFromMask(w, h, strict, null, null,
            mirrorMissingSide = false, handleMask = loose)!!.handle
        assertNotNull(handle)
        handle!!.path.forEach { point ->
            assertTrue("point ${point.x},${point.y} is over the body", point.x < bodyLeft + 4f)
        }
    }

    @Test fun looseMaskDoesNotAddAHandleThatIsNotThere() {
        val profile = VesselProfileExtractor.extractFromMask(w, h, mask(body), null, null,
            mirrorMissingSide = false, handleMask = mask(body))!!
        assertNull(profile.handle)
    }

    /** Points along a cubic from the upper join out and back to the lower join. */
    private fun earCurve(): List<Pair<Float, Float>> = (0..200).map { i ->
        val t = i / 200f
        val u = 1 - t
        val p0 = bodyRight.toFloat() to 62f
        val p1 = 250f to 58f
        val p2 = 244f to 214f
        val p3 = bodyRight.toFloat() to 196f
        (u * u * u * p0.first + 3 * u * u * t * p1.first + 3 * u * t * t * p2.first + t * t * t * p3.first) to
            (u * u * u * p0.second + 3 * u * u * t * p1.second + 3 * u * t * t * p2.second + t * t * t * p3.second)
    }

    /** An asymmetric ear-shaped handle, 14 px thick, along [earCurve]. */
    private val earHandle: (Int, Int) -> Boolean = run {
        val curve = earCurve()
        val lookup = { x: Int, y: Int ->
            x >= bodyRight && curve.any { (cx, cy) -> (cx - x) * (cx - x) + (cy - y) * (cy - y) <= 49f }
        }
        lookup
    }

    @Test fun earShapedHandleMidlineFollowsTheHandle() {
        val handle = profileOf(mask(body, earHandle))!!.handle
        assertNotNull(handle)
        val curve = earCurve()
        // Every traced point lies on the true centre line, not cutting corners.
        handle!!.path.forEach { point ->
            val nearest = curve.minOf { (cx, cy) -> kotlin.math.hypot(cx - point.x, cy - point.y) }
            assertTrue("point ${point.x},${point.y} is $nearest px off", nearest < 4f)
        }
        // And it runs all the way from join to join.
        assertEquals(62f, handle.upperJoinY, 12f)
        assertEquals(196f, handle.lowerJoinY, 12f)
        assertEquals(14f, handle.typicalThickness, 3f)
    }

    /**
     * A pitcher handle: a short bridge straight out from the rim, a strap hanging
     * almost vertically, and a turn back into the body near the foot. The strap
     * runs parallel to rays cast from the body, which is where ray tracing fails.
     */
    private val pitcherCentre: List<Pair<Float, Float>> = buildList {
        for (x in bodyRight..213) add(x.toFloat() to 56f)                       // bridge
        for (y in 56..196) add(213f to y.toFloat())                             // strap
        for (i in 0..40) {                                                      // turn back in
            val a = Math.PI / 2 * i / 40
            add((bodyRight + 43 * kotlin.math.cos(a)).toFloat() to (196 + 12 * kotlin.math.sin(a)).toFloat())
        }
    }

    private val pitcherHandle: (Int, Int) -> Boolean = { x, y ->
        x >= bodyRight && pitcherCentre.any { (cx, cy) -> (cx - x) * (cx - x) + (cy - y) * (cy - y) <= 49f }
    }

    @Test fun pitcherHandleIsTracedAlongItsStrapWithoutBlobs() {
        val handle = profileOf(mask(body, pitcherHandle))!!.handle
        assertNotNull(handle)
        handle!!
        handle.path.forEach { point ->
            val nearest = pitcherCentre.minOf { (cx, cy) -> kotlin.math.hypot(cx - point.x, cy - point.y) }
            assertTrue("point ${point.x},${point.y} is $nearest px off", nearest < 4f)
        }
        // No point reads the strap lengthways as one enormous thickness.
        assertTrue("max thickness ${handle.path.maxOf { it.thickness }}", handle.path.maxOf { it.thickness } < 22f)
        assertEquals(14f, handle.typicalThickness, 3f)
        // The bridge at the rim is part of the handle, so the upper join sits there.
        assertEquals(56f, handle.upperJoinY, 8f)
        assertEquals(208f, handle.lowerJoinY, 10f)
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
