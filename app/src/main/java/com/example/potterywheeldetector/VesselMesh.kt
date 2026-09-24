package com.example.potterywheeldetector

import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

data class MeshVertex(val x: Float, val y: Float, val z: Float)
data class MeshFace(val indices: List<Int>)
data class VesselMesh(val vertices: List<MeshVertex>, val faces: List<MeshFace>) {
    fun toObj(): String = buildString {
        appendLine("# Pottery vessel generated from a photo silhouette")
        appendLine("# Relative units only; calibrate before manufacturing")
        vertices.forEach { appendLine(String.format(Locale.US, "v %.5f %.5f %.5f", it.x, it.y, it.z)) }
        faces.forEach { face ->
            appendLine("f ${face.indices.joinToString(" ") { (it + 1).toString() }}")
        }
    }
}

object VesselMeshBuilder {
    fun build(profile: VesselProfile, segments: Int = 32, maxRings: Int = 72): VesselMesh {
        val sourceRows = profile.rows
        val rowCount = minOf(maxRings, sourceRows.size)
        val rings = (0 until rowCount).map { index ->
            sourceRows[index * sourceRows.lastIndex / max(1, rowCount - 1)]
        }
        val maxRadius = rings.maxOf { it.radius }.coerceAtLeast(1f)
        val heightScale = 50f / maxRadius
        val height = profile.height * heightScale
        val wall = 3f
        val vertices = ArrayList<MeshVertex>(rowCount * segments * 2)
        for (inside in 0..1) {
            for (row in rings) {
                val radius = row.radius * heightScale
                val r = if (inside == 1) (radius - wall).coerceAtLeast(0.5f) else radius
                val y = height / 2f - (row.y - profile.top) * heightScale
                for (step in 0 until segments) {
                    val angle = 2.0 * PI * step / segments
                    vertices.add(MeshVertex((r * cos(angle)).toFloat(), y,
                        (r * sin(angle)).toFloat()))
                }
            }
        }
        fun at(inside: Int, row: Int, segment: Int) =
            inside * rowCount * segments + row * segments + segment % segments
        val faces = ArrayList<MeshFace>()
        for (row in 0 until rowCount - 1) {
            for (segment in 0 until segments) {
                val next = (segment + 1) % segments
                faces.add(MeshFace(listOf(at(0, row, segment), at(0, row, next),
                    at(0, row + 1, next), at(0, row + 1, segment))))
                faces.add(MeshFace(listOf(at(1, row, segment), at(1, row + 1, segment),
                    at(1, row + 1, next), at(1, row, next))))
            }
        }
        for (segment in 0 until segments) {
            val next = (segment + 1) % segments
            faces.add(MeshFace(listOf(at(0, 0, segment), at(1, 0, segment),
                at(1, 0, next), at(0, 0, next))))
            faces.add(MeshFace(listOf(at(0, rowCount - 1, segment), at(0, rowCount - 1, next),
                at(1, rowCount - 1, next), at(1, rowCount - 1, segment))))
        }
        val baseCenter = vertices.size
        vertices.add(MeshVertex(0f, -height / 2f, 0f))
        for (segment in 0 until segments) {
            faces.add(MeshFace(listOf(baseCenter, at(1, rowCount - 1, segment),
                at(1, rowCount - 1, (segment + 1) % segments))))
        }
        return VesselMesh(vertices, faces)
    }
}
