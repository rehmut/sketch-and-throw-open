package io.github.rehmut.sketchandthrow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class VesselMeshBuilderTest {
    private val profile = VesselProfile(120, 120, (0..100).map { y ->
        val radius = 45f - y * 0.25f
        ProfileRow(y.toFloat(), 60f - radius, 60f + radius)
    })

    @Test fun thrownVesselHasOpenRimWallsAndRaisedInsideFloor() {
        val mesh = VesselMeshBuilder.build(profile, segments = 16)
        val topY = mesh.vertices.maxOf { it.y }
        val bottomY = mesh.vertices.minOf { it.y }
        val topRadii = mesh.vertices.filter { it.y == topY }
            .map { hypot(it.x, it.z) }
        assertEquals(32, topRadii.size)
        assertTrue(topRadii.min() > 0f)
        assertTrue(topRadii.max() > topRadii.min())
        assertTrue(mesh.vertices.any { it.x == 0f && it.z == 0f && it.y > bottomY })
        assertTrue(mesh.vertices.any { it.x == 0f && it.z == 0f && it.y == bottomY })
        assertTrue(mesh.vertices.none { it.x == 0f && it.z == 0f && it.y == topY })
        val edgeCounts = mutableMapOf<Pair<Int, Int>, Int>()
        mesh.faces.forEach { face ->
            face.indices.indices.forEach { i ->
                val a = face.indices[i]
                val b = face.indices[(i + 1) % face.indices.size]
                val edge = minOf(a, b) to maxOf(a, b)
                edgeCounts[edge] = (edgeCounts[edge] ?: 0) + 1
            }
        }
        assertTrue(edgeCounts.values.all { it == 2 })
    }

    @Test fun calibratedWallThicknessChangesOpening() {
        fun openingRadius(wallMm: Double): Float {
            val mesh = VesselMeshBuilder.build(profile, segments = 16,
                wallMm = wallMm, heightCm = 20.0)
            val topY = mesh.vertices.maxOf { it.y }
            return mesh.vertices.filter { it.y == topY }
                .minOf { hypot(it.x, it.z) }
        }
        assertTrue(openingRadius(10.0) < openingRadius(2.0))
    }
}
