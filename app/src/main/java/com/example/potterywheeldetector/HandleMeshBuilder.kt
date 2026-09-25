package com.example.potterywheeldetector

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
    private const val JOIN_INSET = 0.6f

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
        val path = extendIntoBody(handle)
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
