package com.example.potterywheeldetector

import org.junit.Assert.assertEquals
import org.junit.Test

class ClayDefaultsTest {
    @Test fun valuesAreClampedToWorkableRanges() {
        val clamped = ClayDefaults(wallMm = 0.1, baseMm = 99.0, allowancePercent = -5.0).clamped()
        assertEquals(ClayDefaults.WALL_RANGE.start, clamped.wallMm, 0.0)
        assertEquals(ClayDefaults.BASE_RANGE.endInclusive, clamped.baseMm, 0.0)
        assertEquals(0.0, clamped.allowancePercent, 0.0)
    }

    @Test fun factoryDefaultsMatchTheEarlierFixedValues() {
        val defaults = ClayDefaults()
        assertEquals(5.0, defaults.wallMm, 0.0)
        assertEquals(5.0, defaults.baseMm, 0.0)
        assertEquals(25.0, defaults.allowancePercent, 0.0)
    }
}
