package com.example.potterywheeldetector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VesselCategoryTest {
    /** Builds a profile of [height] rows whose radius follows [radius] (0 = rim, 1 = foot). */
    private fun shape(height: Int, handle: VesselHandle? = null, radius: (Float) -> Float) =
        VesselProfile(400, 400, (0..height).map { y ->
            val r = radius(y / height.toFloat())
            ProfileRow(y.toFloat(), 200f - r, 200f + r)
        }, handle)

    private val handle = VesselHandle(HandleSide.RIGHT,
        listOf(HandlePoint(260f, 30f, 10f), HandlePoint(290f, 60f, 10f), HandlePoint(260f, 90f, 10f)),
        30f, 90f, 30f, true)

    @Test fun wideLowOpenFormIsABowl() {
        assertEquals(VesselCategory.BOWL, VesselCategory.suggest(shape(60) { 100f - 40f * it }))
    }

    @Test fun veryFlatFormIsAPlate() {
        assertEquals(VesselCategory.PLATE, VesselCategory.suggest(shape(20) { 120f - 20f * it }))
    }

    @Test fun tallFormWithHandleIsAMug() {
        assertEquals(VesselCategory.MUG, VesselCategory.suggest(shape(120, handle) { 50f }))
    }

    @Test fun lowFormWithHandleIsACup() {
        assertEquals(VesselCategory.CUP, VesselCategory.suggest(shape(60, handle) { 50f }))
    }

    @Test fun narrowNeckOnATallBellyIsAVase() {
        // Neck at the rim a third of the belly width.
        val vase = shape(240) { t -> if (t < 0.3f) 22f else 22f + 48f * minOf(1f, (t - 0.3f) * 4f) }
        assertEquals(VesselCategory.VASE, VesselCategory.suggest(vase))
    }

    @Test fun squatFormWithANarrowerMouthIsAJar() {
        val jar = shape(110) { t -> if (t < 0.15f) 45f else 70f }
        assertEquals(VesselCategory.JAR, VesselCategory.suggest(jar))
    }

    @Test fun unknownStoredNamesFallBackToNothing() {
        assertEquals(VesselCategory.MUG, VesselCategory.fromName("MUG"))
        assertNull(VesselCategory.fromName("TEAPOT"))
    }
}
