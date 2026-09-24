package com.example.potterywheeldetector

import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.Rect
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class ProfileRow(val y: Float, val left: Float, val right: Float) {
    val radius: Float get() = (right - left) / 2f
}

data class VesselProfile(
    val imageWidth: Int,
    val imageHeight: Int,
    val rows: List<ProfileRow>
) {
    val top: Float get() = rows.first().y
    val bottom: Float get() = rows.last().y
    val left: Float get() = rows.minOf { it.left }
    val right: Float get() = rows.maxOf { it.right }
    val height: Float get() = bottom - top
    val width: Float get() = right - left
}

/** Extracts the dominant foreground silhouette from an upright, straight-on photo. */
object VesselProfileExtractor {
    fun extract(
        bitmap: Bitmap,
        focus: Rect? = null,
        seed: PointF? = null,
        sensitivity: Int = 50
    ): VesselProfile? {
        val w = bitmap.width
        val h = bitmap.height
        if (w < 32 || h < 32) return null
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        val background = sampleBackground(pixels, w, h)
        val distances = IntArray(pixels.size)
        val histogram = IntArray(256)
        for (i in pixels.indices) {
            var nearest = 255
            for (color in background) nearest = min(nearest, colorDistance(pixels[i], color))
            distances[i] = nearest
            histogram[nearest]++
        }
        val auto = otsu(histogram, pixels.size).coerceIn(18, 90)
        val cutoff = (auto * (1.55f - sensitivity.coerceIn(0, 100) / 100f))
            .roundToInt().coerceIn(12, 125)

        val mask = BooleanArray(pixels.size) { distances[it] >= cutoff }
        val visited = BooleanArray(pixels.size)
        val queue = IntArray(pixels.size)
        val seedIndex = seed?.let {
            val sx = it.x.roundToInt().coerceIn(0, w - 1)
            val sy = it.y.roundToInt().coerceIn(0, h - 1)
            sy * w + sx
        }
        var bestPixels: IntArray? = null
        var bestCount = 0
        var bestScore = -1.0

        for (start in mask.indices) {
            if (!mask[start] || visited[start]) continue
            var head = 0
            var tail = 1
            var minX = w
            var maxX = 0
            var minY = h
            var maxY = 0
            var edgeContacts = 0
            var containsSeed = false
            var focusHits = 0
            queue[0] = start
            visited[start] = true

            while (head < tail) {
                val index = queue[head++]
                val x = index % w
                val y = index / w
                minX = min(minX, x)
                maxX = max(maxX, x)
                minY = min(minY, y)
                maxY = max(maxY, y)
                if (x == 0 || y == 0 || x == w - 1 || y == h - 1) edgeContacts++
                if (index == seedIndex) containsSeed = true
                if (focus?.contains(x, y) == true) focusHits++

                fun enqueue(neighbor: Int) {
                    if (mask[neighbor] && !visited[neighbor]) {
                        visited[neighbor] = true
                        queue[tail++] = neighbor
                    }
                }
                if (x > 0) enqueue(index - 1)
                if (x < w - 1) enqueue(index + 1)
                if (y > 0) enqueue(index - w)
                if (y < h - 1) enqueue(index + w)
            }

            val componentHeight = maxY - minY + 1
            if (tail < w * h / 200 || componentHeight < h / 8) continue
            val center = (minX + maxX) / 2.0
            val centrality = 1.0 - min(0.7, abs(center - w / 2.0) / w)
            val framePenalty = 1.0 / (1.0 + edgeContacts.toDouble() / max(1, tail) * 12)
            val focusBonus = if (focus == null) 1.0 else 1.0 + focusHits.toDouble() / tail
            val seedBonus = if (containsSeed) 100.0 else 1.0
            val score = tail * centrality * framePenalty * focusBonus * seedBonus
            if (score > bestScore) {
                bestScore = score
                bestCount = tail
                bestPixels = queue.copyOf(tail)
            }
        }

        val selected = bestPixels ?: return null
        if (bestCount == 0) return null
        val byRow = Array(h) { ArrayList<Int>() }
        for (index in selected) byRow[index / w].add(index % w)
        val top = byRow.indexOfFirst { it.isNotEmpty() }
        val bottom = byRow.indexOfLast { it.isNotEmpty() }
        if (top < 0 || bottom - top < h / 8) return null
        val roughWidth = selected.maxOf { it % w } - selected.minOf { it % w } + 1
        val gap = max(2, (roughWidth * 0.06f).roundToInt())
        val rows = ArrayList<ProfileRow>()
        var previous: ProfileRow? = null
        for (y in top..bottom) {
            val xs = byRow[y]
            val row = if (xs.isEmpty()) {
                val last = previous ?: continue
                ProfileRow(y.toFloat(), last.left, last.right)
            } else {
                xs.sort()
                var runStart = xs[0]
                var runEnd = xs[0]
                var runCount = 1
                var bestStart = runStart
                var bestEnd = runEnd
                var longest = 1
                for (i in 1 until xs.size) {
                    val x = xs[i]
                    if (x - runEnd > gap) {
                        if (runCount > longest) {
                            longest = runCount
                            bestStart = runStart
                            bestEnd = runEnd
                        }
                        runStart = x
                        runCount = 0
                    }
                    runEnd = x
                    runCount++
                }
                if (runCount > longest) {
                    bestStart = runStart
                    bestEnd = runEnd
                }
                ProfileRow(y.toFloat(), bestStart.toFloat(), bestEnd.toFloat())
            }
            rows.add(row)
            previous = row
        }
        val smoothed = rows.mapIndexed { index, row ->
            val reach = min(3, min(index, rows.lastIndex - index))
            val window = rows.subList(index - reach, index + reach + 1)
            ProfileRow(row.y, window.map { it.left }.average().toFloat(),
                window.map { it.right }.average().toFloat())
        }
        // A wheel-thrown vessel is approximately rotationally symmetric. Strong
        // light can merge one half with the backdrop; retain the more reliable
        // outer edge on each row and mirror it around the estimated axis.
        val detectedWidth = smoothed.maxOf { it.right } - smoothed.minOf { it.left }
        val axis = when {
            focus != null && focus.width() > detectedWidth * 1.2f -> focus.centerX().toFloat()
            seed != null -> seed.x
            else -> w / 2f
        }
        val symmetric = smoothed.map { row ->
            val radius = max(abs(row.left - axis), abs(row.right - axis))
            ProfileRow(row.y, (axis - radius).coerceAtLeast(0f),
                (axis + radius).coerceAtMost((w - 1).toFloat()))
        }
        val profile = VesselProfile(w, h, symmetric)
        return if (profile.width > w * 0.04f && profile.height > h * 0.12f) profile else null
    }

    private fun sampleBackground(pixels: IntArray, w: Int, h: Int): IntArray {
        val points = arrayOf(
            0.03f to 0.03f, 0.97f to 0.03f, 0.03f to 0.55f,
            0.97f to 0.55f, 0.03f to 0.97f, 0.97f to 0.97f
        )
        return points.map { (fx, fy) ->
            val cx = (fx * (w - 1)).roundToInt()
            val cy = (fy * (h - 1)).roundToInt()
            var red = 0
            var green = 0
            var blue = 0
            var count = 0
            for (y in max(0, cy - 3)..min(h - 1, cy + 3)) {
                for (x in max(0, cx - 3)..min(w - 1, cx + 3)) {
                    val color = pixels[y * w + x]
                    red += color shr 16 and 255
                    green += color shr 8 and 255
                    blue += color and 255
                    count++
                }
            }
            (red / count shl 16) or (green / count shl 8) or (blue / count)
        }.toIntArray()
    }

    private fun colorDistance(a: Int, b: Int): Int {
        val dr = (a shr 16 and 255) - (b shr 16 and 255)
        val dg = (a shr 8 and 255) - (b shr 8 and 255)
        val db = (a and 255) - (b and 255)
        return (sqrt((dr * dr + dg * dg + db * db).toDouble()) / 1.732).roundToInt()
            .coerceIn(0, 255)
    }

    private fun otsu(histogram: IntArray, total: Int): Int {
        var weightedTotal = 0.0
        for (i in histogram.indices) weightedTotal += i * histogram[i]
        var backgroundCount = 0
        var backgroundSum = 0.0
        var best = 0.0
        var threshold = 0
        for (i in histogram.indices) {
            backgroundCount += histogram[i]
            if (backgroundCount == 0 || backgroundCount == total) continue
            backgroundSum += i * histogram[i]
            val foregroundCount = total - backgroundCount
            val difference = backgroundSum / backgroundCount -
                (weightedTotal - backgroundSum) / foregroundCount
            val variance = backgroundCount.toDouble() * foregroundCount * difference * difference
            if (variance > best) {
                best = variance
                threshold = i
            }
        }
        return threshold
    }
}
