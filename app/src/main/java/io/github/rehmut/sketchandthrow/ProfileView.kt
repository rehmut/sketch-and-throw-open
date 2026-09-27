package io.github.rehmut.sketchandthrow

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
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
    /** Assumed wall and floor, drawn inside the outline once the height is known. */
    var wallMm: Double = 5.0
        set(value) { field = value; invalidate() }
    var baseMm: Double = 5.0
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
    /** The clay in section: wall and floor between outline and assumed inside. */
    private val clay = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.getColor(R.color.housing_dim) }
    private val innerLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.graphite_soft)
        style = Paint.Style.STROKE
        strokeWidth = density
    }
    private val labelBox = android.graphics.RectF()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // The levelled outline the 3D model and clay estimate use, so all three agree.
        val shape = profile?.let(ProfileEnds::level) ?: return
        val values = measurements?.takeIf { showValues }
        letter.textSize = sp(if (values != null) 13f else 11f)
        val bounds = SilhouettePainter.bounds(shape)
        // The height dimension goes on the side away from the handle. Leave room
        // for it, and above and below for rim and foot when values are written out.
        val handleOnLeft = showHandle && shape.handle?.side == HandleSide.LEFT
        val dimensionRoom = shape.width * (if (values != null) 0.34f else 0.22f)
        if (handleOnLeft) bounds.right += dimensionRoom else bounds.left -= dimensionRoom
        if (values != null) {
            bounds.top -= shape.height * 0.14f
            bounds.bottom += shape.height * 0.14f
        }
        val map = ImageMapping.fit(bounds, width, height, 12 * density)
        val axisX = map.x(shape.axisX)
        canvas.drawLine(axisX, map.y(shape.top) - 6 * density, axisX,
            map.y(shape.bottom) + 6 * density, axis)
        val body = SilhouettePainter.bodyPath(shape, map)
        measurements?.let { drawWall(canvas, shape, map, body, it.heightCm / shape.height) }
        canvas.drawPath(body, outline)
        val handle = shape.handle
        if (handle != null && showHandle) {
            canvas.drawPath(SilhouettePainter.midlinePath(handle, map), handleLine)
        }

        drawHeight(canvas, shape, map, values, onRight = handleOnLeft)
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
            drawEndDimension(canvas, shape.rows.first(), shape.axisX, values.rimDiameterCm / cmPerPixel, map,
                above = true, "D ${cm(values.rimDiameterCm)}")
            drawEndDimension(canvas, shape.rows.last(), shape.axisX, values.footDiameterCm / cmPerPixel, map,
                above = false, "E ${cm(values.footDiameterCm)}")
        }
    }

    /**
     * The assumed inside, offset inward by the wall and raised by the floor: the
     * same construction as the 3D model, so the opening and wall match it.
     */
    private fun drawWall(
        canvas: Canvas, shape: VesselProfile, map: ImageMapping, body: Path, cmPerPixel: Double
    ) {
        val wallPx = (wallMm / 10.0 / cmPerPixel).toFloat()
        val floorY = shape.bottom - (baseMm / 10.0 / cmPerPixel).toFloat()
        val hollow = shape.rows.filter { it.y < floorY && it.right - it.left > 2 * wallPx }
        if (hollow.size < 2) return
        val inside = Path().apply {
            hollow.forEachIndexed { index, row ->
                val x = map.x(row.left + wallPx)
                if (index == 0) moveTo(x, map.y(row.y)) else lineTo(x, map.y(row.y))
            }
            val last = hollow.last()
            lineTo(map.x(last.left + wallPx), map.y(floorY))
            lineTo(map.x(last.right - wallPx), map.y(floorY))
            hollow.asReversed().forEach { row -> lineTo(map.x(row.right - wallPx), map.y(row.y)) }
        }
        canvas.drawPath(body, clay)
        canvas.drawPath(inside, knockout)
        canvas.drawPath(inside, innerLine)
    }

    /** Vertical height dimension beside the body, its value written along it when shown. */
    private fun drawHeight(
        canvas: Canvas, shape: VesselProfile, map: ImageMapping, values: VesselMeasurements?, onRight: Boolean
    ) {
        val offset = shape.width * 0.12f * map.scale
        val dimX = if (onRight) map.x(shape.right) + offset else map.x(shape.left) - offset
        val outward = if (onRight) 1f else -1f
        val top = map.y(shape.top)
        val bottom = map.y(shape.bottom)
        val tick = 4 * density
        canvas.drawLine(dimX, top, dimX, bottom, dimension)
        canvas.drawLine(dimX - tick, top, dimX + tick, top, dimension)
        canvas.drawLine(dimX - tick, bottom, dimX + tick, bottom, dimension)
        val middle = (top + bottom) / 2f
        if (values == null) {
            letter.textAlign = if (onRight) Paint.Align.LEFT else Paint.Align.RIGHT
            canvas.drawText("A", dimX + outward * 3 * density, middle, letter)
            return
        }
        // Read bottom-to-top on the left, top-to-bottom on the right, text outside the line.
        letter.textAlign = Paint.Align.CENTER
        val textX = dimX + outward * 5 * density
        canvas.save()
        canvas.rotate(if (onRight) 90f else -90f, textX, middle)
        canvas.drawText("A ${cm(values.heightCm)}", textX, middle, letter)
        canvas.restore()
    }

    /**
     * Rim or foot diameter: a dimension line just beyond the end, with extension
     * ticks, spanning the measured [diameterPx] centred on the turning axis. The
     * end row itself is no guide: a photo narrows it by perspective, and on a
     * tilted rim its highest point sits off to one side.
     */
    private fun drawEndDimension(
        canvas: Canvas, row: ProfileRow, center: Float, diameterPx: Double, map: ImageMapping,
        above: Boolean, text: String
    ) {
        val gap = 12 * density
        val edgeY = map.y(row.y)
        val y = if (above) edgeY - gap else edgeY + gap
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
