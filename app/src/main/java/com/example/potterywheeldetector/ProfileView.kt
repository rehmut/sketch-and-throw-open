package com.example.potterywheeldetector

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import java.util.Locale
import kotlin.math.abs

/**
 * The technical drawing: outline, axis, dashed handle and lettered dimensions
 * matching the readout list (A height, B widest, C narrowest, D rim, E foot).
 * Small, it carries the letters only; with [showValues] it becomes a full
 * dimensioned drawing with each value written beside its line.
 */
class ProfileView(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    var profile: VesselProfile? = null
        set(value) { field = value; invalidate() }
    var measurements: VesselMeasurements? = null
        set(value) { field = value; invalidate() }
    var showHandle: Boolean = true
        set(value) { field = value; invalidate() }
    var showValues: Boolean = false
        set(value) { field = value; invalidate() }

    private val density = resources.displayMetrics.density
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.graphite)
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
        strokeJoin = Paint.Join.ROUND
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
    private val extension = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.scale)
        strokeWidth = 0.8f * density
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
    }
    /** Paper colour behind a written value, breaking any line it crosses. */
    private val knockout = Paint().apply { color = context.getColor(R.color.key) }
    private val labelBox = android.graphics.RectF()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val shape = profile ?: return
        val values = measurements?.takeIf { showValues }
        letter.textSize = sp(if (values != null) 13f else 11f)
        val bounds = SilhouettePainter.bounds(shape)
        // Room on the left for the height dimension, and above and below for
        // rim and foot when values are written out.
        bounds.left -= shape.width * (if (values != null) 0.34f else 0.22f)
        if (values != null) {
            bounds.top -= shape.height * 0.14f
            bounds.bottom += shape.height * 0.14f
        }
        val map = ImageMapping.fit(bounds, width, height, 12 * density)
        val axisX = map.x((shape.left + shape.right) / 2f)
        canvas.drawLine(axisX, map.y(shape.top) - 6 * density, axisX,
            map.y(shape.bottom) + 6 * density, axis)
        canvas.drawPath(SilhouettePainter.bodyPath(shape, map), outline)
        val handle = shape.handle
        if (handle != null && showHandle) {
            canvas.drawPath(SilhouettePainter.midlinePath(handle, map), handleLine)
        }

        drawHeight(canvas, shape, map, values)
        val measured = measurements ?: return
        val cmPerPixel = measured.heightCm / shape.height
        // B sits right of the axis and C left, so they stay apart when the widest
        // and narrowest rows are close, as on a straight cylinder.
        // Lines first, then every label over them, so no line runs through text.
        val labels = mutableListOf<Triple<String, Float, Paint.Align>>()
        fun mark(fromBaseCm: Double, label: String, diameter: Double, paint: Paint, align: Paint.Align) {
            val rowY = shape.bottom - fromBaseCm / cmPerPixel
            val row = shape.rows.minBy { abs(it.y - rowY) }
            val y = map.y(row.y)
            canvas.drawLine(map.x(row.left), y, map.x(row.right), y, paint)
            labels.add(Triple(if (values != null) "$label ${cm(diameter)}" else label,
                y + letter.textSize + 2 * density, align))
        }
        mark(measured.widestFromBaseCm, "B", measured.widestDiameterCm, widest, Paint.Align.LEFT)
        mark(measured.narrowestFromBaseCm, "C", measured.narrowestDiameterCm, narrowest, Paint.Align.RIGHT)
        labels.forEach { (text, y, align) ->
            val offset = if (align == Paint.Align.LEFT) 3 * density else -3 * density
            drawLabel(canvas, text, axisX + offset, y, align)
        }
        if (values != null) {
            drawEndDimension(canvas, shape.rows.first(), values.rimDiameterCm / cmPerPixel, map,
                above = true, "D ${cm(values.rimDiameterCm)}")
            drawEndDimension(canvas, shape.rows.last(), values.footDiameterCm / cmPerPixel, map,
                above = false, "E ${cm(values.footDiameterCm)}")
        }
    }

    /** Vertical height dimension on the left, its value written along it when shown. */
    private fun drawHeight(canvas: Canvas, shape: VesselProfile, map: ImageMapping, values: VesselMeasurements?) {
        val dimX = map.x(shape.left) - shape.width * 0.12f * map.scale
        val top = map.y(shape.top)
        val bottom = map.y(shape.bottom)
        val tick = 4 * density
        canvas.drawLine(dimX, top, dimX, bottom, dimension)
        canvas.drawLine(dimX - tick, top, dimX + tick, top, dimension)
        canvas.drawLine(dimX - tick, bottom, dimX + tick, bottom, dimension)
        val middle = (top + bottom) / 2f
        if (values == null) {
            letter.textAlign = Paint.Align.RIGHT
            canvas.drawText("A", dimX - 3 * density, middle, letter)
            return
        }
        letter.textAlign = Paint.Align.CENTER
        canvas.save()
        canvas.rotate(-90f, dimX - 5 * density, middle)
        canvas.drawText("A ${cm(values.heightCm)}", dimX - 5 * density, middle, letter)
        canvas.restore()
    }

    /**
     * Rim or foot diameter: a dimension line just beyond the end, with extension
     * ticks, spanning the measured [diameterPx] rather than the end row, which a
     * photo shows narrowed by perspective.
     */
    private fun drawEndDimension(
        canvas: Canvas, row: ProfileRow, diameterPx: Double, map: ImageMapping, above: Boolean, text: String
    ) {
        val gap = 12 * density
        val edgeY = map.y(row.y)
        val y = if (above) edgeY - gap else edgeY + gap
        val center = (row.left + row.right) / 2f
        val left = map.x(center - diameterPx.toFloat() / 2f)
        val right = map.x(center + diameterPx.toFloat() / 2f)
        canvas.drawLine(left, edgeY, left, y + (if (above) -3 else 3) * density, extension)
        canvas.drawLine(right, edgeY, right, y + (if (above) -3 else 3) * density, extension)
        canvas.drawLine(left, y, right, y, dimension)
        val textY = if (above) y - 4 * density else y + letter.textSize + 2 * density
        drawLabel(canvas, text, (left + right) / 2f, textY, Paint.Align.CENTER)
    }

    /** Text on a small paper-coloured patch, only when values are written out. */
    private fun drawLabel(canvas: Canvas, text: String, x: Float, baseline: Float, align: Paint.Align) {
        letter.textAlign = align
        if (showValues) {
            val textWidth = letter.measureText(text)
            val left = when (align) {
                Paint.Align.LEFT -> x
                Paint.Align.RIGHT -> x - textWidth
                else -> x - textWidth / 2f
            }
            val pad = 2 * density
            labelBox.set(left - pad, baseline - letter.textSize, left + textWidth + pad, baseline + pad)
            canvas.drawRect(labelBox, knockout)
        }
        canvas.drawText(text, x, baseline, letter)
    }

    private fun cm(value: Double) = String.format(Locale.getDefault(), "%.1f cm", value)

    private fun sp(value: Float) =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)
}
