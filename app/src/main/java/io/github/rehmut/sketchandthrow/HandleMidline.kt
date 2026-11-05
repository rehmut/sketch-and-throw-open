package io.github.rehmut.sketchandthrow

import java.util.PriorityQueue
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Traces a handle's centre line along its medial ridge.
 *
 * A distance map gives every handle pixel its distance to the nearest edge; the
 * ridge of that map is the centre line and twice its height the thickness. The
 * path runs from the upper join to the lower join by the cheapest route, where
 * pixels near the ridge are cheap and pixels near an edge dear, so it follows
 * the middle of the handle whatever its shape: a C, an ear, or a pitcher's
 * bridge and strap. Background pixels are allowed at a high cost, so a small gap
 * the segmentation left in the handle is bridged rather than ending the trace.
 */
internal object HandleMidline {
    private const val DIAGONAL = 1.4142135f
    /** Cost of a step across background, relative to a step along the ridge. */
    private const val GAP_COST = 25f
    /** Rows between join pixels that separate the upper join from the lower. */
    private const val JOIN_GAP_ROWS = 4
    /** Distance between output points along the path, in pixels. */
    private const val SAMPLE_SPACING = 4f

    /**
     * [handle] marks handle pixels in an image [width] × [height]; [touching]
     * lists handle pixels that sit against the body. Returns points from the
     * upper join to the lower, or an empty list when no path exists.
     */
    fun trace(width: Int, height: Int, handle: BooleanArray, pixels: IntArray, touching: IntArray): List<HandlePoint> {
        if (pixels.isEmpty()) return emptyList()
        var minX = width; var maxX = 0; var minY = height; var maxY = 0
        for (index in pixels) {
            val x = index % width
            val y = index / width
            minX = min(minX, x); maxX = max(maxX, x); minY = min(minY, y); maxY = max(maxY, y)
        }
        val left = max(0, minX - 2)
        val topY = max(0, minY - 2)
        val w = min(width - 1, maxX + 2) - left + 1
        val h = min(height - 1, maxY + 2) - topY + 1
        fun local(index: Int) = (index / width - topY) * w + (index % width - left)
        val inside = BooleanArray(w * h)
        for (index in pixels) inside[local(index)] = true
        val distance = distanceMap(inside, w, h)

        val (start, end) = endpoints(touching.map(::local), pixels.map(::local), distance, w) ?: return emptyList()
        val route = cheapestPath(inside, distance, w, h, start, end) ?: return emptyList()
        return sample(route, distance, w, left, topY)
    }

    /** Two-pass chamfer distance to the nearest pixel outside the handle. */
    private fun distanceMap(inside: BooleanArray, w: Int, h: Int): FloatArray {
        val far = Float.MAX_VALUE / 4
        val d = FloatArray(inside.size) { if (inside[it]) far else 0f }
        fun relax(i: Int, x: Int, y: Int, dx: Int, dy: Int, cost: Float) {
            val nx = x + dx
            val ny = y + dy
            val v = if (nx in 0 until w && ny in 0 until h) d[ny * w + nx] else 0f
            if (v + cost < d[i]) d[i] = v + cost
        }
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            if (!inside[i]) continue
            relax(i, x, y, -1, 0, 1f); relax(i, x, y, 0, -1, 1f)
            relax(i, x, y, -1, -1, DIAGONAL); relax(i, x, y, 1, -1, DIAGONAL)
        }
        for (y in h - 1 downTo 0) for (x in w - 1 downTo 0) {
            val i = y * w + x
            if (!inside[i]) continue
            relax(i, x, y, 1, 0, 1f); relax(i, x, y, 0, 1, 1f)
            relax(i, x, y, 1, 1, DIAGONAL); relax(i, x, y, -1, 1, DIAGONAL)
        }
        return d
    }

    /**
     * The middle of the upper and of the lower join. Pixels touching the body are
     * split where the rows break; with only one run, as for a lug, the handle's
     * top-most and bottom-most pixels stand in.
     */
    private fun endpoints(touching: List<Int>, all: List<Int>, distance: FloatArray, w: Int): Pair<Int, Int>? {
        if (all.isEmpty()) return null
        val runs = mutableListOf<MutableList<Int>>()
        var lastRow = Int.MIN_VALUE
        for (index in touching.sortedBy { it / w }) {
            val row = index / w
            if (runs.isEmpty() || row - lastRow > JOIN_GAP_ROWS) runs.add(mutableListOf())
            runs.last().add(index)
            lastRow = row
        }
        // Pixels against the body all sit about equally near the handle's edge, so
        // take the join's middle row, and the deepest pixel in it.
        fun deepest(group: List<Int>): Int {
            val rows = group.map { it / w }.sorted()
            val middle = rows[rows.size / 2]
            return group.filter { it / w == middle }.maxBy { distance[it] }
        }
        return if (runs.size >= 2) deepest(runs.first()) to deepest(runs.last())
        else all.minBy { it / w } to all.maxBy { it / w }
    }

    private fun cheapestPath(
        inside: BooleanArray, distance: FloatArray, w: Int, h: Int, start: Int, end: Int
    ): List<Int>? {
        val cost = FloatArray(inside.size) { Float.MAX_VALUE }
        val previous = IntArray(inside.size) { -1 }
        val queue = PriorityQueue<Pair<Float, Int>>(compareBy { it.first })
        cost[start] = 0f
        queue.add(0f to start)
        while (queue.isNotEmpty()) {
            val (spent, i) = queue.poll()
            if (spent > cost[i]) continue
            if (i == end) break
            val x = i % w
            val y = i / w
            for (dy in -1..1) for (dx in -1..1) {
                if (dx == 0 && dy == 0) continue
                val nx = x + dx
                val ny = y + dy
                if (nx !in 0 until w || ny !in 0 until h) continue
                val n = ny * w + nx
                val step = if (dx != 0 && dy != 0) DIAGONAL else 1f
                // Near the ridge a step is cheap, near an edge dear, across a gap dearer still.
                val weight = if (inside[n]) 1f / (1f + distance[n] * distance[n]) else GAP_COST
                val next = spent + step * weight
                if (next < cost[n]) {
                    cost[n] = next
                    previous[n] = i
                    queue.add(next to n)
                }
            }
        }
        if (previous[end] == -1 && end != start) return null
        val route = ArrayList<Int>()
        var at = end
        while (at != -1) {
            route.add(at)
            at = previous[at]
        }
        return route.asReversed()
    }

    /** Points every [SAMPLE_SPACING] px along the route, with thickness from the distance map. */
    private fun sample(route: List<Int>, distance: FloatArray, w: Int, left: Int, top: Int): List<HandlePoint> {
        val points = ArrayList<HandlePoint>()
        var travelled = SAMPLE_SPACING
        var last: Int? = null
        for ((position, i) in route.withIndex()) {
            last?.let { previous ->
                val dx = (i % w - previous % w).toFloat()
                val dy = (i / w - previous / w).toFloat()
                travelled += sqrt(dx * dx + dy * dy)
            }
            last = i
            if (travelled >= SAMPLE_SPACING || position == route.lastIndex) {
                travelled = 0f
                // A pixel's distance counts to the outside pixel's centre, so the
                // edge lies half a pixel nearer.
                val thickness = if (distance[i] > 0f) 2f * distance[i] - 1f else 0f
                points.add(HandlePoint((i % w + left).toFloat(), (i / w + top).toFloat(), thickness))
            }
        }
        return fillGaps(points)
    }

    /** Points crossing a bridged gap have no thickness of their own: borrow the nearest. */
    private fun fillGaps(points: List<HandlePoint>): List<HandlePoint> {
        val known = points.withIndex().filter { it.value.thickness > 0f }
        if (known.isEmpty()) return points
        return points.mapIndexed { index, point ->
            if (point.thickness > 0f) point
            else point.copy(thickness = known.minBy { kotlin.math.abs(it.index - index) }.value.thickness)
        }
    }
}
