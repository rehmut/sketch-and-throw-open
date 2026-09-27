package io.github.rehmut.sketchandthrow

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
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
    /** The point the user last tapped, shown as a crosshair. */
    var seed: PointF? = null
        set(value) { field = value; invalidate() }
    var showHandle: Boolean = true
        set(value) { field = value; invalidate() }
    var onVesselTap: ((PointF) -> Unit)? = null

    private val density = resources.displayMetrics.density
    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.signal)
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * density
    }
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.signal)
        alpha = 110
    }
    private val markerFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.getColor(R.color.key) }
    private val markerLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.graphite)
        style = Paint.Style.STROKE
        strokeWidth = 1.6f * density
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
        val map = ImageMapping(scale, imageBounds.left, imageBounds.top)

        profile?.let { outline ->
            val handle = outline.handle
            if (handle != null && showHandle) {
                SilhouettePainter.drawHandleBody(canvas, handle, map, handlePaint)
            }
            canvas.drawPath(SilhouettePainter.bodyPath(outline, map), linePaint)
        }
        seed?.let { point ->
            val x = map.x(point.x)
            val y = map.y(point.y)
            val r = 8 * density
            canvas.drawCircle(x, y, r, markerFill)
            canvas.drawCircle(x, y, r, markerLine)
            canvas.drawLine(x - 2.2f * r, y, x - 1.4f * r, y, markerLine)
            canvas.drawLine(x + 1.4f * r, y, x + 2.2f * r, y, markerLine)
            canvas.drawLine(x, y - 2.2f * r, x, y - 1.4f * r, markerLine)
            canvas.drawLine(x, y + 1.4f * r, x, y + 2.2f * r, markerLine)
        }
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
