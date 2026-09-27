package io.github.rehmut.sketchandthrow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

class HandleModelTest {
    /** A 100 px tall cylinder, 100 px wide, centred at x = 60. */
    private val rows = (0..100).map { ProfileRow(it.toFloat(), 10f, 110f) }

    /** A straight vertical handle 50 px long, 10 px thick, 20 px out from the body. */
    private val straightHandle = VesselHandle(
        side = HandleSide.RIGHT,
        path = (0..10).map { HandlePoint(130f, 25f + it * 5f, 10f) },
        upperJoinY = 25f, lowerJoinY = 75f, projection = 25f, hasEye = true
    )

    private val profile = VesselProfile(140, 120, rows, straightHandle)

    @Test fun handleClayFollowsTheChosenCrossSection() {
        // 100 px = 10 cm, so the handle is 5 cm long and 1 cm thick; 2 cm wide.
        fun handleGrams(section: HandleSection) = VesselMeasurementCalculator.calculate(
            profile, 10.0, 5.0, 0.0,
            handle = HandleOptions(section = section, widthMm = 20.0)
        )!!.handleClayGrams
        val density = VesselMeasurementCalculator.WET_CLAY_GRAMS_PER_CM3
        assertEquals(PI / 4 * 1.0 * 1.0 * 5.0 * density, handleGrams(HandleSection.ROUND), 0.01)
        assertEquals(PI / 4 * 1.0 * 2.0 * 5.0 * density, handleGrams(HandleSection.OVAL), 0.01)
        assertEquals(0.9 * 1.0 * 2.0 * 5.0 * density, handleGrams(HandleSection.STRAP), 0.01)
    }

    @Test fun handleClayIsAddedBeforeTheTrimmingAllowance() {
        val with = VesselMeasurementCalculator.calculate(profile, 10.0, 5.0, 25.0)!!
        val without = VesselMeasurementCalculator.calculate(
            profile, 10.0, 5.0, 25.0, handle = HandleOptions(enabled = false))!!
        assertEquals(0.0, without.handleClayGrams, 0.0)
        assertEquals(with.handleClayGrams * 1.25,
            with.estimatedClayGrams - without.estimatedClayGrams, 0.01)
    }

    @Test fun handleDimensionsAreReportedInCentimetres() {
        val result = VesselMeasurementCalculator.calculate(profile, 10.0, 5.0, 25.0)!!
        assertEquals(5.0, result.handleSpanCm!!, 0.01)
        assertEquals(2.5, result.handleProjectionCm!!, 0.01)
        assertEquals(1.0, result.handleThicknessCm!!, 0.01)
        val plain = VesselMeasurementCalculator.calculate(
            VesselProfile(140, 120, rows), 10.0, 5.0, 25.0)!!
        assertNull(plain.handleSpanCm)
    }

    @Test fun thickerBaseAddsFloorClay() {
        val thin = VesselMeasurementCalculator.calculate(
            VesselProfile(140, 120, rows), 10.0, 5.0, 0.0, baseMm = 5.0)!!
        val thick = VesselMeasurementCalculator.calculate(
            VesselProfile(140, 120, rows), 10.0, 5.0, 0.0, baseMm = 10.0)!!
        // Inside radius 4.5 cm; 5 mm more floor.
        val extra = PI * 4.5 * 4.5 * 0.5 * VesselMeasurementCalculator.WET_CLAY_GRAMS_PER_CM3
        assertEquals(extra, thick.estimatedClayGrams - thin.estimatedClayGrams, 0.5)
    }

    @Test fun handleMeshIsAClosedTubeOnTheHandleSide() {
        val mesh = VesselMeshBuilder.build(profile, segments = 16,
            wallMm = 5.0, heightCm = 10.0, handle = HandleOptions(widthMm = 20.0))
        val handleFaces = mesh.faces.filter { it.part == MeshPart.HANDLE }
        assertTrue(handleFaces.isNotEmpty())
        val edgeCounts = mutableMapOf<Pair<Int, Int>, Int>()
        handleFaces.forEach { face ->
            face.indices.indices.forEach { i ->
                val a = face.indices[i]
                val b = face.indices[(i + 1) % face.indices.size]
                edgeCounts.merge(minOf(a, b) to maxOf(a, b), 1, Int::plus)
            }
        }
        assertTrue(edgeCounts.values.all { it == 2 })
        val handleVertices = handleFaces.flatMap { it.indices }.toSet().map { mesh.vertices[it] }
        assertTrue(handleVertices.all { it.x > 0f })
        // Front-to-back width follows the assumed 20 mm against a 10 cm body.
        val depth = handleVertices.maxOf { it.z } - handleVertices.minOf { it.z }
        val bodyHeight = mesh.vertices.maxOf { it.y } - mesh.vertices.minOf { it.y }
        assertEquals(0.2f, depth / bodyHeight, 0.03f)
        assertTrue(mesh.toObj().contains("g handle"))
    }

    @Test fun disabledHandleLeavesOnlyTheBody() {
        val mesh = VesselMeshBuilder.build(profile, segments = 16,
            handle = HandleOptions(enabled = false))
        assertTrue(mesh.faces.none { it.part == MeshPart.HANDLE })
    }
}
