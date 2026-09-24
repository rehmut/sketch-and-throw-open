package com.example.potterywheeldetector

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View

class ProfileView(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    var profile: VesselProfile? = null
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
    }
}
