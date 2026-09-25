package com.example.potterywheeldetector

import java.util.Locale
import kotlin.math.roundToLong

/** Turns measurements into the short, lettered lines the measure step shows. */
object VesselReadout {
    data class SpecRow(val letter: String, val label: String, val value: String)

    data class ClaySplit(
        val totalGrams: Double,
        val bodyGrams: Double,
        val handleGrams: Double,
        val allowanceGrams: Double
    )

    fun specRows(m: VesselMeasurements, locale: Locale = Locale.getDefault()): List<SpecRow> {
        fun cm(value: Double) = String.format(locale, "%.1f cm", value)
        fun at(value: Double) = String.format(locale, "%.1f", value)
        val rows = mutableListOf(
            SpecRow("A", "height", cm(m.heightCm)),
            SpecRow("B", "widest Ø · at ${at(m.widestFromBaseCm)} cm", cm(m.widestDiameterCm)),
            SpecRow("C", "narrowest Ø · at ${at(m.narrowestFromBaseCm)} cm", cm(m.narrowestDiameterCm)),
            SpecRow("D", "rim Ø", cm(m.rimDiameterCm)),
            SpecRow("E", "foot Ø", cm(m.footDiameterCm)),
            SpecRow("F", "height : width", String.format(locale, "%.2f", m.heightCm / m.widestDiameterCm))
        )
        val span = m.handleSpanCm
        val projection = m.handleProjectionCm
        if (span != null && projection != null) {
            rows.add(SpecRow("G", "handle span · reach", "${at(span)} · ${cm(projection)}"))
        }
        return rows
    }

    /** Splits the ball weight into body, handle and the trimming allowance on top. */
    fun claySplit(m: VesselMeasurements, allowancePercent: Double): ClaySplit {
        val beforeAllowance = m.estimatedClayGrams / (1.0 + allowancePercent / 100.0)
        return ClaySplit(
            totalGrams = m.estimatedClayGrams,
            bodyGrams = beforeAllowance - m.handleClayGrams,
            handleGrams = m.handleClayGrams,
            allowanceGrams = m.estimatedClayGrams - beforeAllowance
        )
    }

    fun grams(value: Double): String = "${value.roundToLong()} g"

    fun millimetres(value: Double, locale: Locale = Locale.getDefault()): String =
        if (value % 1.0 == 0.0) value.roundToLong().toString()
        else String.format(locale, "%.1f", value)

    fun settingsSummary(
        wallMm: Double, baseMm: Double, allowancePercent: Double,
        locale: Locale = Locale.getDefault()
    ) = "wall ${millimetres(wallMm, locale)} · base ${millimetres(baseMm, locale)} mm · " +
        "trim ${allowancePercent.roundToLong()} %"
}
