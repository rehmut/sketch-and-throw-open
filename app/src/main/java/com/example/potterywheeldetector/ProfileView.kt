package com.example.potterywheeldetector

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View

class ProfileView(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    var profile: VesselProfile? = null
        set(value) { field = value; invalidate() }
    var measurements: VesselMeasurements? = null
        set(value) { field = value; invalidate() }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(182, 118, 75) }
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(72, 45, 33)
        style = Paint.Style.STROKE
        strokeWidth = 2f * resources.displayMetrics.density
    }
    private val axis = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(160, 137, 122)
        strokeWidth = resources.displayMetrics.density
    }
    private val measureLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(20, 105, 130)
        strokeWidth = 2f * resources.displayMetrics.density
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 13f,
            resources.displayMetrics)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val shape = profile ?: return
        val margin = 20f * resources.displayMetrics.density
        val scale = minOf((width - 2 * margin) / shape.width,
            (height - 2 * margin) / shape.height)
        val center = (shape.left + shape.right) / 2f
        val xOffset = width / 2f - center * scale
        val yOffset = (height - shape.height * scale) / 2f - shape.top * scale
        val path = Path()
        shape.rows.forEachIndexed { i, row ->
            val x = xOffset + row.left * scale
            val y = yOffset + row.y * scale
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        shape.rows.asReversed().forEach { row ->
            path.lineTo(xOffset + row.right * scale, yOffset + row.y * scale)
        }
        path.close()
        canvas.drawPath(path, fill)
        canvas.drawPath(path, outline)
        canvas.drawLine(width / 2f, margin / 2f, width / 2f, height - margin / 2f, axis)
        measurements?.let { values ->
            val cmPerPixel = values.heightCm / shape.height
            fun mark(fromBaseCm: Double, label: String, color: Int) {
                val rowY = shape.bottom - fromBaseCm / cmPerPixel
                val row = shape.rows.minBy { kotlin.math.abs(it.y - rowY) }
                val y = yOffset + row.y * scale
                val left = xOffset + row.left * scale
                val right = xOffset + row.right * scale
                measureLine.color = color
                canvas.drawLine(left, y, right, y, measureLine)
                canvas.drawCircle(left, y, 4f * resources.displayMetrics.density, measureLine)
                canvas.drawCircle(right, y, 4f * resources.displayMetrics.density, measureLine)
                canvas.drawText(label, (right + 7f * resources.displayMetrics.density)
                    .coerceAtMost(width - 25f * resources.displayMetrics.density),
                    y - 5f * resources.displayMetrics.density, measureLine)
            }
            mark(values.widestFromBaseCm, "W", Color.rgb(20, 105, 130))
            mark(values.narrowestFromBaseCm, "N", Color.rgb(154, 73, 41))
        }
    }
}
