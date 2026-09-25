package com.example.potterywheeldetector

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import kotlin.math.abs

/**
 * A small technical drawing: outline, axis, dashed handle and lettered
 * dimensions matching the readout list (A height, B widest, C narrowest).
 */
class ProfileView(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    var profile: VesselProfile? = null
        set(value) { field = value; invalidate() }
    var measurements: VesselMeasurements? = null
        set(value) { field = value; invalidate() }
    var showHandle: Boolean = true
        set(value) { field = value; invalidate() }

    private val density = resources.displayMetrics.density
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.graphite)
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
    }
    private val handleLine = SilhouettePainter.dashed(Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.graphite)
        style = Paint.Style.STROKE
        strokeWidth = 1.2f * density
    }, 4 * density, 3 * density)
    private val axis = SilhouettePainter.dashed(Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.scale)
        style = Paint.Style.STROKE
        strokeWidth = density
    }, 6 * density, 3 * density)
    private val dimension = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.graphite)
        strokeWidth = density
    }
    private val widest = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.signal)
        strokeWidth = 1.6f * density
    }
    private val narrowest = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.scale)
        strokeWidth = 1.6f * density
    }
    private val letter = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.graphite)
        typeface = Typeface.MONOSPACE
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 11f, resources.displayMetrics)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val shape = profile ?: return
        val bounds = SilhouettePainter.bounds(shape)
        // Leave room on the left for the height dimension.
        bounds.left -= shape.width * 0.22f
        val map = ImageMapping.fit(bounds, width, height, 12 * density)
        val axisX = map.x((shape.left + shape.right) / 2f)
        canvas.drawLine(axisX, map.y(shape.top) - 6 * density, axisX,
            map.y(shape.bottom) + 6 * density, axis)
        canvas.drawPath(SilhouettePainter.bodyPath(shape, map), outline)
        val handle = shape.handle
        if (handle != null && showHandle) {
            canvas.drawPath(SilhouettePainter.midlinePath(handle, map), handleLine)
        }

        val dimX = map.x(shape.left) - shape.width * 0.12f * map.scale
        val tick = 4 * density
        canvas.drawLine(dimX, map.y(shape.top), dimX, map.y(shape.bottom), dimension)
        canvas.drawLine(dimX - tick, map.y(shape.top), dimX + tick, map.y(shape.top), dimension)
        canvas.drawLine(dimX - tick, map.y(shape.bottom), dimX + tick, map.y(shape.bottom), dimension)
        letter.textAlign = Paint.Align.RIGHT
        canvas.drawText("A", dimX - 3 * density, (map.y(shape.top) + map.y(shape.bottom)) / 2f, letter)

        val values = measurements ?: return
        val cmPerPixel = values.heightCm / shape.height
        // B sits right of the axis and C left, so they stay apart when the widest
        // and narrowest rows are close, as on a straight cylinder.
        fun mark(fromBaseCm: Double, label: String, paint: Paint, align: Paint.Align) {
            val rowY = shape.bottom - fromBaseCm / cmPerPixel
            val row = shape.rows.minBy { abs(it.y - rowY) }
            val y = map.y(row.y)
            canvas.drawLine(map.x(row.left), y, map.x(row.right), y, paint)
            letter.textAlign = align
            val offset = if (align == Paint.Align.LEFT) 3 * density else -3 * density
            canvas.drawText(label, axisX + offset, y + 13 * density, letter)
        }
        mark(values.widestFromBaseCm, "B", widest, Paint.Align.LEFT)
        mark(values.narrowestFromBaseCm, "C", narrowest, Paint.Align.RIGHT)
    }
}
