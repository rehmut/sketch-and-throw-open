package io.github.rehmut.sketchandthrow

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sign

/**
 * Sweeps a closed tube along a traced handle midline, in the same coordinates
 * as the revolved body: x across the photo, y up, z towards the viewer.
 */
internal object HandleMeshBuilder {
    /** Exponent giving a pulled strap its softened-rectangle section. */
    private const val STRAP_ROUNDNESS = 0.35
    /** How far each end is pushed into the body wall, as a fraction of thickness. */
    private const val JOIN_INSET = 0.35f
    private const val MIN_SAMPLES = 12
    private const val MAX_SAMPLES = 48
    private const val SMOOTHING_PASSES = 8
    /** Points either side averaged into each thickness. */
    private const val THICKNESS_REACH = 3
    /** How far thickness may stray from its typical value, as a fraction. */
    private const val THICKNESS_SPREAD = 0.2f

    class Placement(
        /** Image x of the body axis. */
        val axisX: Float,
        /** Image y of the top of the profile. */
        val top: Float,
        /** Model y of the profile top. */
        val topY: Float,
        /** Model units per image pixel. */
        val scale: Float,
        /** Model units per millimetre, when the height is known. */
        val unitsPerMm: Float?
    )

    fun append(
        vertices: MutableList<MeshVertex>,
        faces: MutableList<MeshFace>,
        handle: VesselHandle,
        options: HandleOptions,
        placement: Placement,
        segments: Int = 12
    ) {
        // Extend first, then smooth: the turn into the wall is rounded off with the
        // rest instead of leaving a corner that pinches the tube.
        val path = smoothPath(extendIntoBody(handle))
        if (path.size < 2) return
        val centers = path.map { point ->
            Pair((point.x - placement.axisX) * placement.scale,
                placement.topY - (point.y - placement.top) * placement.scale)
        }
        val rings = path.indices.map { index ->
            val before = centers[max(0, index - 1)]
            val after = centers[minOf(centers.lastIndex, index + 1)]
            val tx = after.first - before.first
            val ty = after.second - before.second
            val length = hypot(tx, ty).coerceAtLeast(1e-4f)
            // In-plane normal: across the handle within the photo plane.
            val nx = -ty / length
            val ny = tx / length
            val thickness = path[index].thickness * placement.scale
            val depth = depthOf(thickness, options, placement)
            val start = vertices.size
            for (step in 0 until segments) {
                val angle = 2.0 * PI * step / segments
                val (across, forward) = sectionPoint(angle, options.section)
                vertices.add(MeshVertex(
                    centers[index].first + nx * across * thickness / 2f,
                    centers[index].second + ny * across * thickness / 2f,
                    forward * depth / 2f))
            }
            start
        }
        fun at(ring: Int, step: Int) = ring + step % segments
        for (index in 0 until rings.lastIndex) {
            for (step in 0 until segments) {
                faces.add(MeshFace(listOf(at(rings[index], step), at(rings[index], step + 1),
                    at(rings[index + 1], step + 1), at(rings[index + 1], step)),
                    part = MeshPart.HANDLE))
            }
        }
        fun cap(ring: Int, center: Pair<Float, Float>, reversed: Boolean) {
            val middle = vertices.size
            vertices.add(MeshVertex(center.first, center.second, 0f))
            for (step in 0 until segments) {
                val a = at(ring, step)
                val b = at(ring, step + 1)
                faces.add(MeshFace(if (reversed) listOf(middle, b, a) else listOf(middle, a, b),
                    part = MeshPart.HANDLE))
            }
        }
        cap(rings.first(), centers.first(), reversed = true)
        cap(rings.last(), centers.last(), reversed = false)
    }

    /**
     * The traced midline wobbles by a pixel or two from ray to ray, and its
     * thickness jumps where a ray crosses at a slant; swept as they are, both show
     * as lumps. Resample evenly along the path, smooth the positions with the ends
     * held, and ease the thickness towards its typical value.
     */
    internal fun smoothPath(path: List<HandlePoint>): List<HandlePoint> {
        if (path.size < 4) return path
        val count = path.size.coerceIn(MIN_SAMPLES, MAX_SAMPLES)
        var points = resample(path, count)
        repeat(SMOOTHING_PASSES) {
            points = points.mapIndexed { index, point ->
                if (index == 0 || index == points.lastIndex) point
                else {
                    val a = points[index - 1]
                    val b = points[index + 1]
                    point.copy(x = (a.x + 2 * point.x + b.x) / 4f, y = (a.y + 2 * point.y + b.y) / 4f)
                }
            }
        }
        val eased = points.indices.map { index ->
            val from = maxOf(0, index - THICKNESS_REACH)
            val to = minOf(points.lastIndex, index + THICKNESS_REACH)
            (from..to).map { points[it].thickness }.average().toFloat()
        }
        val typical = eased.sorted()[eased.size / 2]
        return points.mapIndexed { index, point ->
            point.copy(thickness = eased[index].coerceIn(typical * (1 - THICKNESS_SPREAD),
                typical * (1 + THICKNESS_SPREAD)))
        }
    }

    /** [count] points evenly spaced along the path, thickness interpolated. */
    private fun resample(path: List<HandlePoint>, count: Int): List<HandlePoint> {
        val lengths = FloatArray(path.size)
        for (index in 1 until path.size) {
            lengths[index] = lengths[index - 1] +
                hypot(path[index].x - path[index - 1].x, path[index].y - path[index - 1].y)
        }
        val total = lengths.last()
        if (total <= 0f) return path
        var segment = 1
        return (0 until count).map { step ->
            val target = total * step / (count - 1)
            while (segment < path.lastIndex && lengths[segment] < target) segment++
            val a = path[segment - 1]
            val b = path[segment]
            val span = (lengths[segment] - lengths[segment - 1]).coerceAtLeast(1e-4f)
            val t = ((target - lengths[segment - 1]) / span).coerceIn(0f, 1f)
            HandlePoint(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t,
                a.thickness + (b.thickness - a.thickness) * t)
        }
    }

    /** Pushes both ends toward the axis so the tube meets the wall without a gap. */
    private fun extendIntoBody(handle: VesselHandle): List<HandlePoint> {
        val inward = if (handle.side == HandleSide.RIGHT) -1f else 1f
        val first = handle.path.first()
        val last = handle.path.last()
        return listOf(first.copy(x = first.x + inward * first.thickness * JOIN_INSET)) +
            handle.path + last.copy(x = last.x + inward * last.thickness * JOIN_INSET)
    }

    private fun depthOf(thickness: Float, options: HandleOptions, placement: Placement): Float {
        val width = placement.unitsPerMm?.let { (options.widthMm * it).toFloat() }
            ?: thickness * 2f
        return options.section.depth(thickness.toDouble(), width.toDouble()).toFloat()
    }

    /** A unit section point: x across the handle, z front to back, each in -1..1. */
    private fun sectionPoint(angle: Double, section: HandleSection): Pair<Float, Float> {
        val c = cos(angle)
        val s = sin(angle)
        if (section != HandleSection.STRAP) return Pair(c.toFloat(), s.toFloat())
        fun soften(v: Double) = (sign(v) * abs(v).pow(STRAP_ROUNDNESS)).toFloat()
        return Pair(soften(c), soften(s))
    }
}
