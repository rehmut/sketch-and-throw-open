package com.example.potterywheeldetector

import kotlin.math.max

/**
 * Rim and foot diameters from a silhouette's row widths.
 *
 * A photo sees the rim and the foot ring as flat ellipses, so the silhouette
 * narrows to a point at each end. Reading the width a fixed few rows in lands on
 * that curve and under-reads, often badly. Instead, walk in from the end until
 * the width stops growing: an ellipse grows steeply and then levels off, and the
 * level it reaches is the true diameter.
 *
 * A bevel or a sloping foot also narrows towards the end, but in a straight
 * line. That is real geometry, not perspective, so there the end width is kept.
 */
internal object EndDiameter {
    /** Fraction of the height searched from each end for the rim or foot. */
    private const val SEARCH_ZONE = 0.12f
    /** Growth over one window, as a width fraction, that counts as levelled off. */
    private const val LEVEL_GROWTH = 0.015f
    /** How far the halfway width must bulge above a straight line to be an ellipse. */
    private const val ELLIPSE_BULGE = 0.15f

    /** A diameter and how many rows in from its end it was taken. */
    data class Reading(val width: Float, val rowsIn: Int)

    /** [widths] from rim (index 0) to foot. */
    fun rim(widths: List<Float>): Float = rimReading(widths).width

    /** [widths] from rim (index 0) to foot. */
    fun foot(widths: List<Float>): Float = footReading(widths).width

    /** Rows before [Reading.rowsIn] belong to the rim ellipse, not the wall. */
    fun rimReading(widths: List<Float>): Reading = fromEnd(widths)

    fun footReading(widths: List<Float>): Reading = fromEnd(widths.asReversed())

    private fun fromEnd(raw: List<Float>): Reading {
        if (raw.isEmpty()) return Reading(0f, 0)
        if (raw.size < 3) return Reading(raw.max(), 0)
        val widths = median3(raw)
        val last = widths.lastIndex
        val endRow = max(1, widths.size / 50).coerceAtMost(last)
        val zone = max(6, (widths.size * SEARCH_ZONE).toInt()).coerceAtMost(last)
        val window = max(2, widths.size / 60)

        val level = (0..zone).firstOrNull { row ->
            row + window <= last &&
                widths[row + window] - widths[row] <= max(1f, LEVEL_GROWTH * widths[row])
        } ?: return Reading(widths[endRow], endRow)
        val growth = widths[level] - widths[0]
        if (level < 2 || growth <= 0f) {
            val row = max(endRow, level).coerceAtMost(last)
            return Reading(widths[row], row)
        }

        // An ellipse is already most of the way out halfway to where it levels
        // off; a straight bevel is only halfway.
        val halfway = widths[level / 2]
        val straightLine = (widths[0] + widths[level]) / 2f
        return if (halfway - straightLine > ELLIPSE_BULGE * growth) {
            val levelEnd = (level + window).coerceAtMost(last)
            Reading(widths.subList(0, levelEnd + 1).max(), levelEnd)
        } else {
            Reading(widths[endRow], endRow)
        }
    }

    /** Removes single-row spikes and dropouts without moving edges. */
    private fun median3(values: List<Float>) = values.indices.map { index ->
        if (index == 0 || index == values.lastIndex) values[index]
        else listOf(values[index - 1], values[index], values[index + 1]).sorted()[1]
    }
}
