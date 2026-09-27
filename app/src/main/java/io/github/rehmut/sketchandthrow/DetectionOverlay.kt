package io.github.rehmut.sketchandthrow

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

class DetectionOverlay(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    private val boxes = mutableListOf<RectF>()
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(255, 192, 83)
        style = Paint.Style.STROKE
        strokeWidth = 4f * resources.displayMetrics.density
    }

    fun showBoxes(newBoxes: List<RectF>) {
        boxes.clear()
        boxes.addAll(newBoxes.map(::RectF))
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        boxes.forEach { canvas.drawRoundRect(it, 12f, 12f, borderPaint) }
    }
}
