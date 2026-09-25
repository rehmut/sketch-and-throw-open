package com.example.potterywheeldetector

import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min

data class VesselMeasurements(
    val heightCm: Double,
    val widestDiameterCm: Double,
    val widestFromBaseCm: Double,
    val narrowestDiameterCm: Double,
    val narrowestFromBaseCm: Double,
    val baseCurveEndCm: Double,
    val rimDiameterCm: Double,
    val footDiameterCm: Double,
    val estimatedClayGrams: Double
)

object VesselMeasurementCalculator {
    // Approximate density of plastic throwing clay. Actual clay bodies and water
    // contents vary, so this is a planning estimate, not a measured weight.
    const val WET_CLAY_GRAMS_PER_CM3 = 1.8

    fun calculate(
        profile: VesselProfile,
        heightCm: Double,
        wallMm: Double,
        allowancePercent: Double
    ): VesselMeasurements? {
        if (heightCm <= 0.0 || wallMm <= 0.0 || allowancePercent < 0.0 ||
            profile.height <= 0f || profile.rows.size < 2
        ) return null
        val cmPerPixel = heightCm / profile.height
        val wallCm = wallMm / 10.0
        val widest = profile.rows.maxBy { it.right - it.left }
        // The first and last few mask rows often taper to a one-pixel tip.
        val trim = max(1, profile.rows.size / 50)
        val rows = profile.rows
        val footWidth = rows[rows.lastIndex - trim].right - rows[rows.lastIndex - trim].left
        val widestWidth = rows.maxOf { it.right - it.left }
        val minWallWidth = footWidth + (widestWidth - footWidth) * 0.30f
        val maxBaseRows = max(2, rows.size * 35 / 100)
        val candidate = (rows.lastIndex - trim downTo max(0, rows.lastIndex - maxBaseRows))
            .firstOrNull { rows[it].right - rows[it].left >= minWallWidth }
        val excludedBaseRows = max(rows.size * 15 / 100,
            if (candidate == null || widestWidth <= footWidth * 1.05f)
                rows.size * 15 / 100 else rows.lastIndex - candidate)
        val bodyEnd = min(rows.lastIndex - trim, rows.lastIndex - excludedBaseRows)
            .coerceAtLeast(trim)
        val bodyRows = rows.subList(trim, bodyEnd + 1)
        val narrowest = bodyRows.minBy { it.right - it.left }
        fun diameter(row: ProfileRow) = (row.right - row.left) * cmPerPixel

        // Revolve each band of the photographed outside profile. The assumed
        // inside wall has a constant thickness; add a solid floor of the same
        // thickness beneath it. The actual inside and trimmed foot are unknown.
        var wallVolumeCm3 = 0.0
        for (i in 0 until profile.rows.lastIndex) {
            val a = profile.rows[i]
            val b = profile.rows[i + 1]
            fun annulusArea(row: ProfileRow): Double {
                val outer = max(0.0, diameter(row) / 2.0)
                val inner = max(0.0, outer - wallCm)
                return PI * (outer * outer - inner * inner)
            }
            val bandHeight = (b.y - a.y) * cmPerPixel
            wallVolumeCm3 += (annulusArea(a) + annulusArea(b)) / 2.0 * bandHeight
        }
        val baseRadius = max(0.0, diameter(profile.rows.last()) / 2.0 - wallCm)
        val floorVolumeCm3 = PI * baseRadius * baseRadius * wallCm
        val clayGrams = (wallVolumeCm3 + floorVolumeCm3) *
            WET_CLAY_GRAMS_PER_CM3 * (1.0 + allowancePercent / 100.0)
        return VesselMeasurements(
            heightCm,
            diameter(widest), (profile.bottom - widest.y) * cmPerPixel,
            diameter(narrowest), (profile.bottom - narrowest.y) * cmPerPixel,
            (profile.bottom - rows[bodyEnd].y) * cmPerPixel,
            diameter(profile.rows[trim]), diameter(profile.rows[profile.rows.lastIndex - trim]),
            clayGrams
        )
    }
}
