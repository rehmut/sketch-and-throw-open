package com.example.potterywheeldetector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class VesselReadoutTest {
    private val measured = VesselMeasurements(
        heightCm = 10.5, widestDiameterCm = 8.94, widestFromBaseCm = 5.9,
        narrowestDiameterCm = 8.2, narrowestFromBaseCm = 9.8, baseCurveEndCm = 1.1,
        rimDiameterCm = 8.2, footDiameterCm = 7.4, estimatedClayGrams = 610.0,
        handleClayGrams = 38.4, handleSpanCm = 5.8, handleProjectionCm = 3.9,
        handleThicknessCm = 0.9
    )

    @Test fun specRowsAreLetteredLikeTheDrawing() {
        val rows = VesselReadout.specRows(measured, Locale.US)
        assertEquals(listOf("A", "B", "C", "D", "E", "F", "G"), rows.map { it.letter })
        assertEquals("10.5 cm", rows[0].value)
        assertEquals("widest Ø · at 5.9 cm", rows[1].label)
        assertEquals("8.9 cm", rows[1].value)
        assertEquals("1.17", rows[5].value)
        assertEquals("5.8 · 3.9 cm", rows[6].value)
    }

    @Test fun vesselWithoutHandleHasNoHandleRow() {
        val plain = measured.copy(handleClayGrams = 0.0, handleSpanCm = null,
            handleProjectionCm = null, handleThicknessCm = null)
        assertTrue(VesselReadout.specRows(plain, Locale.US).none { it.letter == "G" })
    }

    @Test fun claySplitSeparatesBodyHandleAndAllowance() {
        val split = VesselReadout.claySplit(measured, 25.0)
        // 610 g includes 25 %: 488 g of clay before trimming, 38.4 g of it handle.
        assertEquals(610.0, split.totalGrams, 0.01)
        assertEquals(38.4, split.handleGrams, 0.01)
        assertEquals(449.6, split.bodyGrams, 0.01)
        assertEquals(122.0, split.allowanceGrams, 0.01)
    }

    @Test fun settingsSummaryReadsLikeThePrintedScale() {
        assertEquals("wall 5 · base 7.5 mm · trim 25 %",
            VesselReadout.settingsSummary(5.0, 7.5, 25.0, Locale.US))
    }
}
