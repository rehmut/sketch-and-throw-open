package io.github.rehmut.sketchandthrow

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Finds a handle in a side-on vessel silhouette and returns body rows with the
 * handle removed.
 *
 * A thrown body is close to symmetric about its axis, so a handle shows up as
 * one side reaching further from the axis than the other. That side is pulled
 * back to mirror the opposite edge, and whatever part of the silhouette is then
 * left outside the body is the handle candidate. Candidates too short, too
 * shallow or too small to be a handle, such as a cast shadow along the base or
 * a spout, are rejected, and the rows are returned untouched.
 */
internal object HandleDetector {
    data class Result(val rows: List<ProfileRow>, val handle: VesselHandle?)

    /** Width fraction a pixel must clear the body edge by to count as outside it. */
    private const val EDGE_TOLERANCE = 0.02f
    /** Width fraction by which one side may exceed the other before it is a candidate. */
    private const val ASYMMETRY_TOLERANCE = 0.04f
    /** A handle spans at least this fraction of the body height. */
    private const val MIN_SPAN = 0.12f
    /** A handle reaches at least this fraction of the body width beyond the edge. */
    private const val MIN_PROJECTION = 0.08f
    /** A handle covers at least this fraction of the body's area. */
    private const val MIN_AREA = 0.01f
    /** Height fraction added above and below a candidate so its joins are corrected too. */
    private const val SPAN_PAD = 0.03f
    /** Largest vertical gap, as a height fraction, bridged between pieces of one handle. */
    private const val MAX_PIECE_GAP = 0.12f
    private const val MIN_EYE_ROWS = 3
    private const val REFINE_PASSES = 3

    private class Region(
        val side: HandleSide,
        val pixels: IntArray,
        val minY: Int,
        val maxY: Int
    )

    fun detect(width: Int, height: Int, component: BooleanArray, rows: List<ProfileRow>): Result {
        val unchanged = Result(rows, null)
        if (rows.size < 16 || component.size != width * height) return unchanged
        val bodyWidth = median(rows.map { it.right - it.left })
        if (bodyWidth < 8f) return unchanged
        val top = rows.first().y.roundToInt()
        val tolerance = max(2f, bodyWidth * EDGE_TOLERANCE)
        val pad = max(2, (rows.size * SPAN_PAD).roundToInt())

        // A leaning or foreshortened rim leaves corners the strict outline misses but
        // a looser mask fills in. They sit over the body, not beside it: judge what
        // is outside against the rows widened to the measured rim and foot.
        // Only those end rows are widened; elsewhere the rows are left to the handle
        // logic below, since they still carry the handle's joins.
        val guard = ProfileEnds.level(VesselProfile(width, height, rows)).rows
        val rimRows = EndDiameter.rimReading(rows).let { if (it.perspective) it.rowsIn else 0 }
        val footRows = EndDiameter.footReading(rows).let { if (it.perspective) it.rowsIn else 0 }
        fun guarded(edges: List<ProfileRow>) = edges.mapIndexed { index, row ->
            if (index >= rimRows && index <= edges.lastIndex - footRows) row
            else row.copy(left = min(row.left, guard[index].left), right = max(row.right, guard[index].right))
        }

        val axis = median(rows.map { (it.left + it.right) / 2f })
        var edges = trimAsymmetricRows(rows, axis, max(3f, bodyWidth * ASYMMETRY_TOLERANCE))
        val maxGap = (rows.size * MAX_PIECE_GAP).roundToInt()
        var region = largestResidual(width, component, guarded(edges), top, tolerance, maxGap) ?: return unchanged
        // Correct every row the candidate touches, including joins that were too
        // shallow to register as asymmetric, then look again: the region grows to
        // take in the joins and settles within a pass or two.
        for (pass in 0 until REFINE_PASSES) {
            val refinedAxis = axisOutside(rows, region.minY - pad, region.maxY + pad, top, axis)
            val corrected = mirrorSpan(rows, region.side, region.minY - pad,
                region.maxY + pad, refinedAxis)
            val next = largestResidual(width, component, guarded(corrected), top, tolerance, maxGap)
                ?.takeIf { it.side == region.side } ?: break
            val settled = next.minY == region.minY && next.maxY == region.maxY
            edges = corrected
            region = next
            if (settled) break
        }

        val shape = guarded(edges)
        val bodyArea = rows.sumOf { (it.right - it.left + 1f).toDouble() }
        val projection = region.pixels.maxOf { pixel ->
            outsideDistance(pixel % width, shape[pixel / width - top], region.side)
        }
        val span = region.maxY - region.minY + 1
        if (span < rows.size * MIN_SPAN || projection < bodyWidth * MIN_PROJECTION ||
            region.pixels.size < bodyArea * MIN_AREA
        ) return unchanged

        val handleMask = BooleanArray(component.size)
        region.pixels.forEach { handleMask[it] = true }
        val touching = region.pixels.filter { pixel ->
            outsideDistance(pixel % width, shape[pixel / width - top], region.side) <= tolerance + 2f
        }.toIntArray()
        // End points over the body belong to the wall, not the handle: the drawings,
        // the clay estimate and the 3D model should all start at the wall.
        fun overBody(point: HandlePoint): Boolean {
            val row = shape[(point.y.roundToInt() - top).coerceIn(0, shape.lastIndex)]
            return outsideDistance(point.x.roundToInt(), row, region.side) < 0f
        }
        val path = smooth(HandleMidline.trace(width, height, handleMask, region.pixels, touching))
            .dropWhile(::overBody).dropLastWhile(::overBody)
        if (path.size < 3) return unchanged
        val handle = VesselHandle(
            side = region.side,
            path = path,
            upperJoinY = path.first().y,
            lowerJoinY = path.last().y,
            projection = projection,
            hasEye = hasEye(width, component, handleMask, region, shape, top, tolerance)
        )
        return Result(edges, handle)
    }

    /** Pulls back whichever side of a row reaches clearly further from the axis. */
    private fun trimAsymmetricRows(rows: List<ProfileRow>, axis: Float, allowance: Float) =
        rows.map { row ->
            val left = axis - row.left
            val right = row.right - axis
            when {
                right - left > allowance -> row.copy(right = axis + left)
                left - right > allowance -> row.copy(left = axis - right)
                else -> row
            }
        }

    /** Axis from the rows the handle does not touch, when enough of them remain. */
    private fun axisOutside(rows: List<ProfileRow>, from: Int, to: Int, top: Int, fallback: Float): Float {
        val clear = rows.filter { val y = it.y.roundToInt(); y < from || y > to }
        if (clear.size < rows.size / 5) return fallback
        return median(clear.map { (it.left + it.right) / 2f })
    }

    /** Mirrors the opposite edge onto the handle side for rows [from]..[to]; never widens. */
    private fun mirrorSpan(
        rows: List<ProfileRow>, side: HandleSide, from: Int, to: Int, axis: Float
    ) = rows.map { row ->
        val y = row.y.roundToInt()
        if (y < from || y > to) row
        else when (side) {
            HandleSide.RIGHT -> row.copy(right = min(row.right, axis + (axis - row.left)))
            HandleSide.LEFT -> row.copy(left = max(row.left, axis - (row.right - axis)))
        }
    }

    private fun outsideDistance(x: Int, row: ProfileRow, side: HandleSide) = when (side) {
        HandleSide.RIGHT -> x - row.right
        HandleSide.LEFT -> row.left - x
    }

    /**
     * The largest group of silhouette pixels lying beyond the body edges that
     * still touches the body, joined with other touching pieces on the same side
     * no more than [maxGap] rows above or below it. A segmentation mask often
     * loses the thin middle of a handle and leaves its two ends as separate
     * pieces; a piece further off, such as a shadow along the base, stays apart.
     */
    private fun largestResidual(
        width: Int, component: BooleanArray, edges: List<ProfileRow>, top: Int,
        tolerance: Float, maxGap: Int
    ): Region? {
        val pieces = touchingPieces(width, component, edges, top, tolerance)
        val largest = pieces.maxByOrNull { it.pixels.size } ?: return null
        var minY = largest.minY
        var maxY = largest.maxY
        val joined = mutableListOf(largest)
        var grew = true
        while (grew) {
            grew = false
            for (piece in pieces) {
                if (piece in joined || piece.side != largest.side) continue
                if (piece.minY > maxY + maxGap || piece.maxY < minY - maxGap) continue
                joined.add(piece)
                minY = min(minY, piece.minY)
                maxY = max(maxY, piece.maxY)
                grew = true
            }
        }
        if (joined.size == 1) return largest
        val pixels = IntArray(joined.sumOf { it.pixels.size })
        var offset = 0
        joined.forEach { piece ->
            piece.pixels.copyInto(pixels, offset)
            offset += piece.pixels.size
        }
        return Region(largest.side, pixels, minY, maxY)
    }

    /** Every connected group of outside pixels that touches the body. */
    private fun touchingPieces(
        width: Int, component: BooleanArray, edges: List<ProfileRow>, top: Int, tolerance: Float
    ): List<Region> {
        val bottom = top + edges.lastIndex
        val sideOf = ByteArray(component.size)
        for (y in top..bottom) {
            val row = edges[y - top]
            for (x in 0 until width) {
                val index = y * width + x
                if (!component[index]) continue
                sideOf[index] = when {
                    x > row.right + tolerance -> 1
                    x < row.left - tolerance -> 2
                    else -> 0
                }
            }
        }
        val visited = BooleanArray(component.size)
        val queue = IntArray(component.size)
        val pieces = ArrayList<Region>()
        for (start in sideOf.indices) {
            if (sideOf[start].toInt() == 0 || visited[start]) continue
            val sideCode = sideOf[start]
            var head = 0
            var tail = 1
            queue[0] = start
            visited[start] = true
            var minY = Int.MAX_VALUE
            var maxY = Int.MIN_VALUE
            var touchesBody = false
            val side = if (sideCode.toInt() == 1) HandleSide.RIGHT else HandleSide.LEFT
            while (head < tail) {
                val index = queue[head++]
                val x = index % width
                val y = index / width
                minY = min(minY, y)
                maxY = max(maxY, y)
                if (outsideDistance(x, edges[y - top], side) <= tolerance + 2f) touchesBody = true
                fun enqueue(neighbor: Int) {
                    if (neighbor in sideOf.indices && sideOf[neighbor] == sideCode && !visited[neighbor]) {
                        visited[neighbor] = true
                        queue[tail++] = neighbor
                    }
                }
                if (x > 0) enqueue(index - 1)
                if (x < width - 1) enqueue(index + 1)
                if (y > top) enqueue(index - width)
                if (y < bottom) enqueue(index + width)
            }
            if (touchesBody) pieces.add(Region(side, queue.copyOf(tail), minY, maxY))
        }
        return pieces
    }

    private fun smooth(points: List<HandlePoint>): List<HandlePoint> {
        if (points.size < 3) return points
        return points.mapIndexed { index, point ->
            if (index == 0 || index == points.lastIndex) point
            else {
                val a = points[index - 1]
                val b = points[index + 1]
                HandlePoint((a.x + point.x + b.x) / 3f, (a.y + point.y + b.y) / 3f,
                    (a.thickness + point.thickness + b.thickness) / 3f)
            }
        }
    }

    /** Background between body and handle on a few rows means a pulled, open handle. */
    private fun hasEye(
        width: Int, component: BooleanArray, handle: BooleanArray, region: Region,
        edges: List<ProfileRow>, top: Int, tolerance: Float
    ): Boolean {
        val sign = if (region.side == HandleSide.RIGHT) 1 else -1
        var eyeRows = 0
        for (y in region.minY..region.maxY) {
            val row = edges[y - top]
            val edge = if (sign > 0) row.right else row.left
            var x = (edge + sign * (tolerance + 1f)).roundToInt()
            var sawGap = false
            while (x in 0 until width) {
                val index = y * width + x
                if (!component[index]) sawGap = true
                else if (handle[index] && sawGap) { eyeRows++; break }
                x += sign
            }
            if (eyeRows >= MIN_EYE_ROWS) return true
        }
        return false
    }

    private fun median(values: List<Float>): Float {
        val sorted = values.sorted()
        return if (sorted.isEmpty()) 0f else sorted[sorted.size / 2]
    }
}
