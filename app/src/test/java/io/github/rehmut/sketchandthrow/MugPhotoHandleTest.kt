package io.github.rehmut.sketchandthrow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.DataInputStream
import java.util.zip.GZIPInputStream

/**
 * Regression on a rendered mug photo (768 × 1024): body x 230..510, rim at
 * y ≈ 250, a pulled handle on the right joining at about y 330 and y 610.
 * The mask is the photo's colour-distance foreground, stored as one byte per
 * pixel after a big-endian width and height.
 */
class MugPhotoHandleTest {
    private fun loadMask(): Triple<Int, Int, BooleanArray> {
        val stream = javaClass.getResourceAsStream("/mug_side.mask.gz")
        assertNotNull("fixture missing", stream)
        DataInputStream(GZIPInputStream(stream!!)).use { input ->
            val w = input.readInt()
            val h = input.readInt()
            val bytes = ByteArray(w * h)
            input.readFully(bytes)
            return Triple(w, h, BooleanArray(w * h) { bytes[it].toInt() == 1 })
        }
    }

    @Test fun handleOfAPhotographedMugIsTracedJoinToJoin() {
        val (w, h, mask) = loadMask()
        val profile = VesselProfileExtractor.extractFromMask(w, h, mask, null, null, false)!!
        val handle = profile.handle
        assertNotNull(handle)
        handle!!
        assertEquals(HandleSide.RIGHT, handle.side)
        assertTrue(handle.hasEye)
        assertEquals(330f, handle.upperJoinY, 20f)
        assertEquals(610f, handle.lowerJoinY, 20f)
        // Midline reaches between the ring's inner (585) and outer (630) edge.
        assertEquals(608f, handle.path.maxOf { it.x }, 10f)
    }

    @Test fun mugBodyStaysStraightPastTheJoins() {
        val (w, h, mask) = loadMask()
        val profile = VesselProfileExtractor.extractFromMask(w, h, mask, null, null, false)!!
        val body = profile.rows.filter { it.y > 290f && it.y < 700f }
        assertTrue(body.maxOf { it.right } - body.minOf { it.right } < 4f)
    }
}
