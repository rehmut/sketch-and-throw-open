package com.example.potterywheeldetector

import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

data class MeshVertex(val x: Float, val y: Float, val z: Float)
enum class MeshPart(val objName: String) { BODY("body"), HANDLE("handle") }
data class MeshFace(
    val indices: List<Int>,
    val interior: Boolean = false,
    val part: MeshPart = MeshPart.BODY
)
data class VesselMesh(val vertices: List<MeshVertex>, val faces: List<MeshFace>) {
    fun toObj(): String = buildString {
        appendLine("# Pottery vessel generated from a photo silhouette")
        appendLine("# Relative units only; calibrate before manufacturing")
        vertices.forEach { appendLine(String.format(Locale.US, "v %.5f %.5f %.5f", it.x, it.y, it.z)) }
        faces.groupBy { it.part }.forEach { (part, partFaces) ->
            appendLine("g ${part.objName}")
            partFaces.forEach { face ->
                appendLine("f ${face.indices.joinToString(" ") { (it + 1).toString() }}")
            }
        }
    }
}

object VesselMeshBuilder {
    fun build(traced: VesselProfile, segments: Int = 32, maxRings: Int = 72,
              wallMm: Double? = null, heightCm: Double? = null, baseMm: Double? = null,
              handle: HandleOptions = HandleOptions()): VesselMesh {
        require(traced.rows.size >= 2 && segments >= 3 && maxRings >= 2)
        // The same levelled outline the drawing and the clay estimate use, so the
        // rim and opening here match theirs rather than the foreshortened photo.
        val profile = ProfileEnds.level(traced)
        // Mask tips can be a single pixel wide. Start and end at stable rows.
        val trim = if (profile.rows.size < 5) 0 else
            max(1, profile.rows.size / 50).coerceAtMost((profile.rows.size - 2) / 2)
        val sourceRows = profile.rows.subList(trim, profile.rows.size - trim)
        val rowCount = minOf(maxRings, sourceRows.size)
        val rings = (0 until rowCount).map { index ->
            sourceRows[index * sourceRows.lastIndex / max(1, rowCount - 1)]
        }
        val maxRadius = rings.maxOf { it.radius }.coerceAtLeast(1f)
        val heightScale = 50f / maxRadius
        val height = profile.height * heightScale
        val unitsPerMm = if (heightCm != null && heightCm > 0)
            (height / (heightCm * 10.0)).toFloat() else null
        val requestedWall = if (wallMm != null && wallMm > 0 && unitsPerMm != null)
            (wallMm * unitsPerMm).toFloat() else 3f
        val wall = min(max(0.5f, requestedWall),
            rings.first().radius * heightScale * 0.25f)
        val modeledHeight = (rings.last().y - rings.first().y) * heightScale
        val requestedBase = if (baseMm != null && baseMm > 0 && unitsPerMm != null)
            (baseMm * unitsPerMm).toFloat() else wall
        val baseThickness = min(max(2f, requestedBase), modeledHeight * 0.25f)
        val bottomY = height / 2f - (rings.last().y - profile.top) * heightScale
        val floorY = bottomY + baseThickness
        val vertices = ArrayList<MeshVertex>(rowCount * segments * 2 + 2)
        fun addRing(radius: Float, y: Float): Int {
            val start = vertices.size
            for (step in 0 until segments) {
                val angle = 2.0 * PI * step / segments
                vertices.add(MeshVertex((radius * cos(angle)).toFloat(), y,
                    (radius * sin(angle)).toFloat()))
            }
            return start
        }
        val outer = rings.map { row ->
            addRing(row.radius * heightScale,
                height / 2f - (row.y - profile.top) * heightScale)
        }
        val inner = ArrayList<Int>()
        for (row in rings) {
            val y = height / 2f - (row.y - profile.top) * heightScale
            if (y <= floorY) break
            inner.add(addRing(max(0.5f, row.radius * heightScale - wall), y))
        }
        // Add the inside floor ring at the exact floor height, interpolated
        // from the outer silhouette where it meets the solid base.
        val floorRadius = run {
            val lower = rings.indexOfFirst { row ->
                height / 2f - (row.y - profile.top) * heightScale <= floorY
            }.coerceAtLeast(1)
            val upperY = height / 2f - (rings[lower - 1].y - profile.top) * heightScale
            val lowerY = height / 2f - (rings[lower].y - profile.top) * heightScale
            val fraction = ((upperY - floorY) / (upperY - lowerY)).coerceIn(0f, 1f)
            val outerRadius = (rings[lower - 1].radius +
                (rings[lower].radius - rings[lower - 1].radius) * fraction) * heightScale
            max(0.5f, outerRadius - wall)
        }
        inner.add(addRing(floorRadius, floorY))
        fun at(ring: Int, segment: Int) = ring + segment % segments
        val faces = ArrayList<MeshFace>()
        for (row in 0 until rowCount - 1) {
            for (segment in 0 until segments) {
                val next = (segment + 1) % segments
                faces.add(MeshFace(listOf(at(outer[row], segment), at(outer[row], next),
                    at(outer[row + 1], next), at(outer[row + 1], segment))))
            }
        }
        for (row in 0 until inner.lastIndex) {
            for (segment in 0 until segments) {
                val next = (segment + 1) % segments
                faces.add(MeshFace(listOf(at(inner[row], segment), at(inner[row + 1], segment),
                    at(inner[row + 1], next), at(inner[row], next)), interior = true))
            }
        }
        for (segment in 0 until segments) {
            val next = (segment + 1) % segments
            faces.add(MeshFace(listOf(at(outer.first(), segment), at(inner.first(), segment),
                at(inner.first(), next), at(outer.first(), next))))
        }
        val floorCenter = vertices.size
        vertices.add(MeshVertex(0f, floorY, 0f))
        val baseCenter = vertices.size
        vertices.add(MeshVertex(0f, bottomY, 0f))
        for (segment in 0 until segments) {
            val next = (segment + 1) % segments
            faces.add(MeshFace(listOf(floorCenter, at(inner.last(), next),
                at(inner.last(), segment)), interior = true))
            faces.add(MeshFace(listOf(baseCenter, at(outer.last(), segment),
                at(outer.last(), next))))
        }
        val traced = profile.handle
        if (traced != null && handle.enabled) {
            HandleMeshBuilder.append(vertices, faces, traced, handle,
                HandleMeshBuilder.Placement(profile.axisX, profile.top, height / 2f, heightScale, unitsPerMm))
        }
        return VesselMesh(vertices, faces)
    }
}
