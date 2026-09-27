package io.github.rehmut.sketchandthrow

import kotlin.math.abs
import kotlin.math.max

/**
 * Rim and foot diameters from a silhouette's rows.
 *
 * A photo sees the rim and the foot ring as flat ellipses, so the silhouette
 * narrows to a point at each end. Reading the width a fixed few rows in lands on
 * that curve and under-reads, often badly. Instead, walk in from the end until
 * the width stops growing: an ellipse grows steeply and then levels off, and the
 * level it reaches is the true diameter.
 *
 * A rim that leans, because the pot or the camera is tilted, also narrows the
 * end, but only one edge moves while the other runs straight; that too is read
 * where it levels off. Only a bevel or sloping foot, where both edges step in
 * along a straight line, is real geometry, and there the end width is kept.
 */
internal object EndDiameter {
    /** Fraction of the height searched from each end for the rim or foot. */
    private const val SEARCH_ZONE = 0.12f
    /** Growth over one window, as a width fraction, that counts as levelled off. */
    private const val LEVEL_GROWTH = 0.015f
    /** How far the halfway width must bulge above a straight line to be an ellipse. */
    private const val ELLIPSE_BULGE = 0.15f
    /** An edge moving less than this share of the other edge's travel is standing still. */
    private const val STILL_EDGE = 0.25f

    /**
     * A diameter and how many rows in from its end it was taken. [perspective]
     * is true when the rows before it are an ellipse or a lean, not the pot's
     * real shape, and may be flattened to [width].
     */
    data class Reading(val width: Float, val rowsIn: Int, val perspective: Boolean = false)

    /** [rows] from rim (index 0) to foot. */
    fun rim(rows: List<ProfileRow>): Float = rimReading(rows).width

    fun foot(rows: List<ProfileRow>): Float = footReading(rows).width

    /** Rows before [Reading.rowsIn] belong to the rim ellipse, not the wall. */
    fun rimReading(rows: List<ProfileRow>): Reading = fromEnd(rows)

    fun footReading(rows: List<ProfileRow>): Reading = fromEnd(rows.asReversed())

    private fun fromEnd(rows: List<ProfileRow>): Reading {
        if (rows.isEmpty()) return Reading(0f, 0)
        if (rows.size < 3) return Reading(rows.maxOf { it.right - it.left }, 0)
        val lefts = median3(rows.map { it.left })
        val rights = median3(rows.map { it.right })
        val widths = lefts.indices.map { rights[it] - lefts[it] }
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
        val levelEnd = (level + window).coerceAtMost(last)
        val levelled = Reading(widths.subList(0, levelEnd + 1).max(), levelEnd, perspective = true)

        // An ellipse is already most of the way out halfway to where it levels off.
        val halfway = widths[level / 2]
        val straightLine = (widths[0] + widths[level]) / 2f
        if (halfway - straightLine > ELLIPSE_BULGE * growth) return levelled

        // A straight narrowing: a lean if one edge stood still, a bevel if both moved.
        val leftTravel = abs(lefts[level] - lefts[0])
        val rightTravel = abs(rights[level] - rights[0])
        val leaning = minOf(leftTravel, rightTravel) < STILL_EDGE * maxOf(leftTravel, rightTravel)
        return if (leaning) levelled else Reading(widths[endRow], endRow)
    }

    /** Removes single-row spikes and dropouts without moving edges. */
    private fun median3(values: List<Float>) = values.indices.map { index ->
        if (index == 0 || index == values.lastIndex) values[index]
        else listOf(values[index - 1], values[index], values[index + 1]).sorted()[1]
    }
}
