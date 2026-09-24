package com.example.potterywheeldetector

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

class Vessel3DView(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    var mesh: VesselMesh? = null
        set(value) { field = value; invalidate() }
    private var yaw = 0.35f
    private var pitch = -0.24f
    private var lastX = 0f
    private var lastY = 0f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private data class Projected(val x: Float, val y: Float, val z: Float)
    private data class DrawFace(val depth: Float, val path: Path, val shade: Int)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val shape = mesh ?: return
        val cy = cos(yaw)
        val sy = sin(yaw)
        val cp = cos(pitch)
        val sp = sin(pitch)
        val transformed = shape.vertices.map { vertex ->
            val rx = vertex.x * cy + vertex.z * sy
            val rz = -vertex.x * sy + vertex.z * cy
            val ry = vertex.y * cp - rz * sp
            val depth = vertex.y * sp + rz * cp
            Projected(rx, ry, depth)
        }
        val spanX = transformed.maxOf { it.x } - transformed.minOf { it.x }
        val spanY = transformed.maxOf { it.y } - transformed.minOf { it.y }
        val scale = minOf(width * 0.75f / max(1f, spanX), height * 0.8f / max(1f, spanY))
        val xMid = (transformed.maxOf { it.x } + transformed.minOf { it.x }) / 2f
        val yMid = (transformed.maxOf { it.y } + transformed.minOf { it.y }) / 2f

        val faces = shape.faces.map { face ->
            val points = face.indices.map { transformed[it] }
            val a = points[0]
            val b = points[1]
            val c = points[2]
            val path = Path().apply {
                moveTo(width / 2f + (a.x - xMid) * scale, height / 2f - (a.y - yMid) * scale)
                points.drop(1).forEach { point ->
                    lineTo(width / 2f + (point.x - xMid) * scale,
                        height / 2f - (point.y - yMid) * scale)
                }
                close()
            }
            val ux = b.x - a.x
            val uy = b.y - a.y
            val uz = b.z - a.z
            val vx = c.x - a.x
            val vy = c.y - a.y
            val vz = c.z - a.z
            val nx = uy * vz - uz * vy
            val ny = uz * vx - ux * vz
            val nz = ux * vy - uy * vx
            val length = kotlin.math.sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(0.001f)
            val light = ((nx * -0.3f + ny * 0.5f + nz * 0.85f) / length)
                .coerceIn(-1f, 1f)
            val shade = (0.55f + 0.37f * light).coerceIn(0.25f, 1f)
            DrawFace(points.map { it.z }.average().toFloat(), path,
                Color.rgb((197 * shade).toInt(), (129 * shade).toInt(),
                    (84 * shade).toInt()))
        }.sortedBy { it.depth }
        faces.forEach { face ->
            paint.color = face.shade
            paint.style = Paint.Style.FILL
            canvas.drawPath(face.path, paint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x
                lastY = event.y
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                yaw += (event.x - lastX) * 0.012f
                pitch = (pitch + (event.y - lastY) * 0.008f).coerceIn(-1.1f, 1.1f)
                lastX = event.x
                lastY = event.y
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                performClick()
                return true
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
