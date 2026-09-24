package com.example.potterywheeldetector

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class GalleryOutlineView(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    var bitmap: Bitmap? = null
        set(value) { field = value; invalidate() }
    var profile: VesselProfile? = null
        set(value) { field = value; invalidate() }
    var onVesselTap: ((PointF) -> Unit)? = null

    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(255, 190, 70)
        style = Paint.Style.STROKE
        strokeWidth = 3f * resources.displayMetrics.density
    }
    private val imageBounds = RectF()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val image = bitmap ?: return
        val scale = minOf(width.toFloat() / image.width, height.toFloat() / image.height)
        val drawWidth = image.width * scale
        val drawHeight = image.height * scale
        imageBounds.set((width - drawWidth) / 2f, (height - drawHeight) / 2f,
            (width + drawWidth) / 2f, (height + drawHeight) / 2f)
        canvas.drawBitmap(image, null, imageBounds, imagePaint)

        val outline = profile ?: return
        val path = Path()
        outline.rows.forEachIndexed { index, row ->
            val x = imageBounds.left + row.left * scale
            val y = imageBounds.top + row.y * scale
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        outline.rows.asReversed().forEach { row ->
            path.lineTo(imageBounds.left + row.right * scale,
                imageBounds.top + row.y * scale)
        }
        path.close()
        canvas.drawPath(path, linePaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_UP) return true
        val image = bitmap ?: return true
        if (!imageBounds.contains(event.x, event.y)) return true
        val x = (event.x - imageBounds.left) * image.width / imageBounds.width()
        val y = (event.y - imageBounds.top) * image.height / imageBounds.height()
        onVesselTap?.invoke(PointF(x, y))
        performClick()
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
