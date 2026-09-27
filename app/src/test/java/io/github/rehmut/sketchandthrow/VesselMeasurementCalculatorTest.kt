package io.github.rehmut.sketchandthrow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

class VesselMeasurementCalculatorTest {
    @Test fun cylinderVolumeIncludesWallBaseAndAllowance() {
        val rows = (0..100).map { ProfileRow(it.toFloat(), 0f, 100f) }
        val profile = VesselProfile(120, 120, rows)
        val result = VesselMeasurementCalculator.calculate(profile, 10.0, 5.0, 25.0)
        assertNotNull(result)
        assertEquals(10.0, result!!.heightCm, 0.001)
        assertEquals(10.0, result.widestDiameterCm, 0.001)
        val expectedVolume = PI * (25.0 - 20.25) * 10.0 + PI * 20.25 * 0.5
        assertEquals(expectedVolume * 1.8 * 1.25, result.estimatedClayGrams, 0.01)
    }

    @Test fun isolatedMaskTipDoesNotBecomeReportedNarrowestPoint() {
        val rows = (0..100).map { y ->
            val width = if (y < 2) 2f else 100f
            ProfileRow(y.toFloat(), 0f, width)
        }
        val result = VesselMeasurementCalculator.calculate(
            VesselProfile(120, 120, rows), 10.0, 5.0, 25.0
        )!!
        assertEquals(10.0, result.narrowestDiameterCm, 0.001)
        assertEquals(10.0, result.rimDiameterCm, 0.001)
    }

    @Test fun narrowFootIsExcludedFromNarrowestWallMeasurement() {
        val rows = (0..100).map { y ->
            val width = if (y < 75) 80f else 80f - (y - 75) * 2f
            ProfileRow(y.toFloat(), 0f, width)
        }
        val result = VesselMeasurementCalculator.calculate(
            VesselProfile(120, 120, rows), 10.0, 5.0, 25.0
        )!!
        assertTrue(result.narrowestDiameterCm > result.footDiameterCm)
        assertTrue(result.narrowestFromBaseCm >= result.baseCurveEndCm)
        assertTrue(result.baseCurveEndCm >= 1.5)
    }
}
