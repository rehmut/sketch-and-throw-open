package com.example.potterywheeldetector

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.DataInputStream
import java.util.zip.GZIPInputStream
import kotlin.math.sqrt

class EndDiameterTest {
    /** Width of an ellipse cap [capRows] deep, [row] rows in from its tip. */
    private fun cap(full: Float, capRows: Int, row: Int): Float {
        if (row >= capRows) return full
        val t = 1f - row.toFloat() / capRows
        return full * sqrt(1f - t * t)
    }

    @Test fun rimAndFootSeenAsEllipsesReadTheirFullWidth() {
        // A 300-row cylinder whose rim and foot show as flat ellipses, as in a photo
        // taken from slightly above.
        val widths = (0..300).map { y -> minOf(cap(100f, 12, y), cap(100f, 8, 300 - y)) }
        assertEquals(100f, EndDiameter.rim(widths), 2f)
        assertEquals(100f, EndDiameter.foot(widths), 2f)
    }

    @Test fun bevelledFootKeepsItsNarrowEnd() {
        // A straight 30-row chamfer: real geometry, not perspective.
        val widths = (0..300).map { y -> if (y < 270) 120f else 120f - (y - 270) * 1.2f }
        assertEquals(widths[294], EndDiameter.foot(widths), 3f)
    }

    @Test fun bowlFootRingIsMeasuredNotTheBody() {
        // Rim ellipse 8 rows deep, a wall narrowing from 160 to 40 below its widest
        // line, then a vertical foot ring 40 wide standing on its own ellipse.
        val widths = (0..200).map { y ->
            when {
                y < 8 -> cap(160f, 8, y)
                y < 188 -> 160f - 120f * (y - 8) / 180f
                else -> cap(40f, 5, 200 - y)
            }
        }
        assertEquals(160f, EndDiameter.rim(widths), 3f)
        assertEquals(40f, EndDiameter.foot(widths), 2f)
    }

    @Test fun singlePixelTipIsIgnored() {
        val widths = (0..100).map { if (it < 2) 2f else 100f }
        assertEquals(100f, EndDiameter.rim(widths), 0.5f)
    }

    @Test fun photographedMugRimMatchesItsBody() {
        val stream = DataInputStream(GZIPInputStream(javaClass.getResourceAsStream("/mug_side.mask.gz")!!))
        val w = stream.readInt()
        val h = stream.readInt()
        val bytes = ByteArray(w * h).also { stream.readFully(it) }
        val profile = VesselProfileExtractor.extractFromMask(w, h,
            BooleanArray(w * h) { bytes[it].toInt() == 1 }, null, null, false)!!
        val result = VesselMeasurementCalculator.calculate(profile, 10.5, 5.0, 25.0)!!
        // A straight mug: its rim is as wide as its body.
        assertEquals(result.widestDiameterCm, result.rimDiameterCm, result.widestDiameterCm * 0.03)
    }

    @Test fun narrowestDiameterSkipsTheRimEllipse() {
        val widths = (0..300).map { y -> minOf(cap(100f, 12, y), cap(100f, 8, 300 - y)) }
        val profile = VesselProfile(400, 400,
            widths.mapIndexed { y, w -> ProfileRow(y.toFloat(), 200f - w / 2f, 200f + w / 2f) })
        val result = VesselMeasurementCalculator.calculate(profile, 30.0, 5.0, 25.0)!!
        assertEquals(10.0, result.narrowestDiameterCm, 0.2)
    }

    @Test fun tinyProfilesStillGiveAValue() {
        assertEquals(50f, EndDiameter.rim(listOf(50f, 50f)), 0.01f)
        assertEquals(0f, EndDiameter.rim(emptyList()), 0.01f)
    }
}
