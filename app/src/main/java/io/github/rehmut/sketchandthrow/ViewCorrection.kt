package io.github.rehmut.sketchandthrow

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin

/**
 * Undoes the camera looking down on the pot.
 *
 * From above, every level circle of the pot shows as an ellipse: the rim's far
 * edge rises above the rim line and the foot's near edge drops below the foot
 * line, while the wall between them is foreshortened by cos θ. Measured against
 * the real height the user types in, the pot then reads too tall at the ends and
 * too narrow everywhere. Given the camera's elevation θ, this drops the two
 * ellipse caps and stretches the wall back to its true height. Widths are kept:
 * a level circle's width does not change with the view.
 */
object ViewCorrection {
    const val MAX_DEGREES = 45

    fun correct(profile: VesselProfile, degrees: Double): VesselProfile {
        if (degrees <= 0.0 || profile.rows.size < 3) return profile
        val theta = Math.toRadians(degrees.coerceAtMost(MAX_DEGREES.toDouble()))
        val lift = sin(theta).toFloat()
        val squash = cos(theta).toFloat()
        val rows = profile.rows
        val rimY = capEnd(rows, lift)
        val footY = capEnd(rows.asReversed(), lift)
        if (footY - rimY < 2f) return profile

        val top = profile.top
        fun unsquash(y: Float) = top + (y - rimY) / squash
        val bottom = unsquash(footY)
        val corrected = (0..floor(bottom - top).toInt()).map { step ->
            val source = rimY + step * squash
            val (left, right) = edgesAt(rows, source)
            ProfileRow(top + step, left, right)
        }
        // A handle may rise above the rim, but its joins sit on the wall.
        fun onWall(y: Float) = unsquash(y.coerceIn(rimY, footY))
        val handle = profile.handle?.let { h ->
            h.copy(path = h.path.map { it.copy(y = unsquash(it.y)) },
                upperJoinY = onWall(h.upperJoinY), lowerJoinY = onWall(h.lowerJoinY))
        }
        return profile.copy(imageHeight = max(profile.imageHeight, ceil(bottom).toInt() + 1),
            rows = corrected, handle = handle)
    }

    /**
     * Where an end's ellipse cap meets the wall. A cap of a circle radius r is
     * r·sin θ deep and reaches that full radius only at its base, so walking in
     * from the end, the first row whose depth covers its own half-width·sin θ is
     * the rim or foot line. The end row itself always qualifies, at depth 0 and
     * often width 0, so the walk starts one row in. [rows] start at the end in
     * question.
     */
    private fun capEnd(rows: List<ProfileRow>, lift: Float): Float {
        val end = rows.first().y
        val row = rows.drop(1).firstOrNull { abs(it.y - end) >= it.radius * lift } ?: rows.last()
        return row.y
    }

    /** Edges at [y], linearly between the rows either side of it. */
    private fun edgesAt(rows: List<ProfileRow>, y: Float): Pair<Float, Float> {
        val after = rows.binarySearch { it.y.compareTo(y) }.let { if (it >= 0) it else -it - 1 }
        if (after <= 0) return rows.first().left to rows.first().right
        if (after > rows.lastIndex) return rows.last().left to rows.last().right
        val a = rows[after - 1]
        val b = rows[after]
        val t = if (b.y > a.y) (y - a.y) / (b.y - a.y) else 0f
        return (a.left + (b.left - a.left) * t) to (a.right + (b.right - a.right) * t)
    }
}
