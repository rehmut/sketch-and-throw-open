package com.example.potterywheeldetector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

class ModelConsistencyTest {
    private fun cap(full: Float, capRows: Int, row: Int): Float {
        if (row >= capRows) return full
        val t = 1f - row.toFloat() / capRows
        return full * sqrt(1f - t * t)
    }

    /** A 300-row, 100 px cylinder on x = 200 whose rim and foot show as ellipses. */
    private val capped = VesselProfile(400, 400, (0..300).map { y ->
        val w = minOf(cap(100f, 12, y), cap(100f, 8, 300 - y))
        ProfileRow(y.toFloat(), 200f - w / 2f, 200f + w / 2f)
    })

    @Test fun levellingGivesRimAndFootTheirMeasuredWidth() {
        val level = ProfileEnds.level(capped)
        assertTrue(level.rows.all { abs((it.right - it.left) - 100f) < 2.5f })
        assertEquals(200f, level.rows.first().let { (it.left + it.right) / 2f }, 0.5f)
    }

    @Test fun levellingLeavesABevelledFootAlone() {
        val bevel = VesselProfile(400, 400, (0..300).map { y ->
            val w = if (y < 270) 120f else 120f - (y - 270) * 1.2f
            ProfileRow(y.toFloat(), 200f - w / 2f, 200f + w / 2f)
        })
        val level = ProfileEnds.level(bevel)
        assertEquals(bevel.rows[296].right, level.rows[296].right, 0.01f)
    }

    @Test fun threeDRimMatchesTheDrawnRimAndWall() {
        // 300 px = 30 cm, so the rim is 10 cm across and a 5 mm wall leaves 9 cm.
        val mesh = VesselMeshBuilder.build(capped, segments = 24, wallMm = 5.0, heightCm = 30.0)
        val topY = mesh.vertices.maxOf { it.y }
        val topRadii = mesh.vertices.filter { abs(it.y - topY) < 1e-3f }.map { hypot(it.x, it.z) }
        val outer = topRadii.max()
        val inner = topRadii.min()
        // The rim is as wide as the body, not narrowed to the ellipse tip...
        val middleY = mesh.vertices.minBy { abs(it.y) }.y
        val bodyRadius = mesh.vertices.filter { abs(it.y - middleY) < 1e-3f }.maxOf { hypot(it.x, it.z) }
        assertEquals(bodyRadius, outer, bodyRadius * 0.02f)
        // ...and a 5 mm wall on a 5 cm radius leaves an opening 90 % as wide.
        assertEquals(0.9f, inner / outer, 0.01f)
    }

    @Test fun handleMidlineIsSmoothedButKeepsItsEnds() {
        // A C-shaped midline with a sawtooth wobble and a jittery thickness.
        val noisy = (0..40).map { i ->
            val a = Math.PI * i / 40
            val wobble = if (i % 2 == 0) 3f else -3f
            HandlePoint((300 + 60 * sin(a)).toFloat() + wobble, (100 + 60 * (1 - kotlin.math.cos(a))).toFloat(),
                if (i % 3 == 0) 14f else 8f)
        }
        val smooth = HandleMeshBuilder.smoothPath(noisy)
        fun roughness(points: List<HandlePoint>) = points.windowed(3).sumOf { (a, b, c) ->
            hypot((a.x - 2 * b.x + c.x).toDouble(), (a.y - 2 * b.y + c.y).toDouble())
        } / (points.size - 2)
        assertTrue(roughness(smooth) < roughness(noisy) * 0.3)
        assertEquals(noisy.first().y, smooth.first().y, 1f)
        assertEquals(noisy.last().y, smooth.last().y, 1f)
        val thickness = smooth.map { it.thickness }
        assertTrue(thickness.max() - thickness.min() < 3f)
    }
}
