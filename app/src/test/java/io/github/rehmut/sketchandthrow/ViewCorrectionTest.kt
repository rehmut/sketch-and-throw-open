package io.github.rehmut.sketchandthrow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class ViewCorrectionTest {
    /**
     * The silhouette of an upright cylinder, radius [r] and height [height] px,
     * seen by a camera [degrees] above level (orthographic): the rim's far edge
     * shows above the rim line, the foot's near edge below the foot line, and
     * the height between them is foreshortened.
     */
    private fun tiltedCylinder(r: Float, height: Float, degrees: Double): VesselProfile {
        val t = Math.toRadians(degrees)
        val lift = (r * sin(t)).toFloat()
        val rimY = 40f + lift
        val footY = rimY + (height * cos(t)).toFloat()
        val rows = (40..(footY + lift).toInt()).map { y ->
            val half = when {
                y < rimY -> r * sqrt(maxOf(0f, 1f - ((rimY - y) / lift).let { it * it }))
                y > footY -> r * sqrt(maxOf(0f, 1f - ((y - footY) / lift).let { it * it }))
                else -> r
            }
            ProfileRow(y.toFloat(), 200f - half, 200f + half)
        }
        return VesselProfile(400, 400, rows)
    }

    @Test fun levelViewIsLeftAlone() {
        val profile = tiltedCylinder(50f, 200f, 0.0)
        assertSame(profile, ViewCorrection.correct(profile, 0.0))
    }

    @Test fun correctingForTheCameraAngleRestoresTrueHeightAndWidth() {
        val seen = tiltedCylinder(50f, 200f, 20.0)
        // Uncorrected, the outline is 188 px of wall plus 34 px of rim and foot ellipse.
        assertEquals(222f, seen.height, 3f)
        val corrected = ViewCorrection.correct(seen, 20.0)
        assertEquals(200f, corrected.height, 3f)
        assertTrue(corrected.rows.all { kotlin.math.abs((it.right - it.left) - 100f) < 3f })
    }

    @Test fun capsEndingInASinglePixelAreStillDropped() {
        val seen = tiltedCylinder(50f, 200f, 20.0)
        val pointed = seen.copy(rows = seen.rows.mapIndexed { index, row ->
            if (index == 0 || index == seen.rows.lastIndex) row.copy(left = 200f, right = 200f) else row
        })
        assertEquals(200f, ViewCorrection.correct(pointed, 20.0).height, 3f)
    }

    @Test fun handleJoinsStayOnTheBody() {
        val seen = tiltedCylinder(50f, 200f, 20.0)
        // A join traced on the rim ellipse, above the rim line.
        val handle = VesselHandle(HandleSide.RIGHT,
            listOf(HandlePoint(250f, seen.top + 4f, 10f), HandlePoint(280f, seen.top + 80f, 10f),
                HandlePoint(250f, seen.bottom - 4f, 10f)), seen.top + 4f, seen.bottom - 4f, 30f, true)
        val corrected = ViewCorrection.correct(seen.copy(handle = handle), 20.0)
        assertEquals(corrected.top, corrected.handle!!.upperJoinY, 0.01f)
        assertEquals(corrected.bottom, corrected.handle!!.lowerJoinY, 1f)
    }

    @Test fun measurementsFromTheCorrectedShapeAreTrue() {
        val seen = tiltedCylinder(50f, 200f, 20.0)
        // The user enters the real height, 20 cm: the pot is 10 cm across.
        val naive = VesselMeasurementCalculator.calculate(seen, 20.0, 5.0, 25.0)!!
        val corrected = VesselMeasurementCalculator.calculate(ViewCorrection.correct(seen, 20.0), 20.0, 5.0, 25.0)!!
        assertTrue("uncorrected reads ${naive.widestDiameterCm}", naive.widestDiameterCm < 9.3)
        assertEquals(10.0, corrected.widestDiameterCm, 0.2)
    }

    @Test fun handleMovesWithTheBody() {
        val seen = tiltedCylinder(50f, 200f, 20.0)
        val rimY = seen.top + 50f * sin(Math.toRadians(20.0)).toFloat()
        val handle = VesselHandle(HandleSide.RIGHT,
            listOf(HandlePoint(260f, rimY + 47f, 10f), HandlePoint(280f, rimY + 94f, 10f),
                HandlePoint(260f, rimY + 141f, 10f)), rimY + 47f, rimY + 141f, 30f, true)
        val corrected = ViewCorrection.correct(seen.copy(handle = handle), 20.0)
        val moved = corrected.handle!!
        // 47 px of foreshortened wall is 50 px of real wall below the rim.
        assertEquals(corrected.top + 50f, moved.upperJoinY, 1.5f)
        assertEquals(corrected.top + 150f, moved.lowerJoinY, 1.5f)
        assertEquals(280f, moved.path[1].x, 0.01f)
    }
}
