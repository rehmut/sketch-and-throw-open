package io.github.rehmut.sketchandthrow

import kotlin.math.abs
import kotlin.math.hypot

/**
 * The interactive segmenter is told where the vessel is with one tap-like point,
 * normally in the middle of the body. It is sure of the body and often unsure of
 * a thin handle far from that point, so the handle comes back in pieces. A second
 * pass pointed at the handle, merged with the first, recovers it.
 */
internal object HandleSeeding {
    /** Width fraction beyond the wall that still counts as the wall itself. */
    private const val WALL_MARGIN = 0.06f
    /** How far above or below the body, as a height fraction, a handle can reach. */
    private const val HEIGHT_MARGIN = 0.05f

    /** A point halfway along the handle's midline, where a second pass should look. */
    fun probe(handle: VesselHandle): HandlePoint {
        val path = handle.path
        val half = handle.length / 2f
        var walked = 0f
        for (index in 1 until path.size) {
            val a = path[index - 1]
            val b = path[index]
            val step = hypot(b.x - a.x, b.y - a.y)
            if (walked + step >= half && step > 0f) {
                val t = (half - walked) / step
                return HandlePoint(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t,
                    a.thickness + (b.thickness - a.thickness) * t)
            }
            walked += step
        }
        return path[path.size / 2]
    }

    /** True for a tap to the side of the body, where a missed handle would be. */
    fun isBesideBody(profile: VesselProfile, x: Float, y: Float): Boolean {
        if (profile.rows.isEmpty()) return false
        val reach = profile.height * HEIGHT_MARGIN
        if (y < profile.top - reach || y > profile.bottom + reach) return false
        val row = profile.rows.minBy { abs(it.y - y) }
        val margin = (row.right - row.left) * WALL_MARGIN
        return x > row.right + margin || x < row.left - margin
    }
}
