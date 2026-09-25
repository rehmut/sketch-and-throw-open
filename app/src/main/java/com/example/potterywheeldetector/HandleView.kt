package com.example.potterywheeldetector

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View

/**
 * The handle step's drawing: body in quiet grey, the traced handle in the
 * signal colour, its midline dashed and both joins marked.
 */
class HandleView(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    var profile: VesselProfile? = null
        set(value) { field = value; updateDescription(); invalidate() }
    var handleEnabled: Boolean = true
        set(value) { field = value; invalidate() }

    private val density = resources.displayMetrics.density
    private val bodyFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.getColor(R.color.housing_dim) }
    private val bodyLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.scale)
        style = Paint.Style.STROKE
        strokeWidth = 1.2f * density
    }
    private val axis = SilhouettePainter.dashed(Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.scale)
        style = Paint.Style.STROKE
        strokeWidth = density
    }, 6 * density, 3 * density)
    private val handleFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.signal)
        alpha = 70
    }
    private val midline = SilhouettePainter.dashed(Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.graphite)
        style = Paint.Style.STROKE
        strokeWidth = 1.4f * density
    }, 5 * density, 4 * density)
    private val joinFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.getColor(R.color.key) }
    private val joinLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.graphite)
        style = Paint.Style.STROKE
        strokeWidth = 1.6f * density
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.graphite_soft)
        typeface = Typeface.MONOSPACE
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 11f, resources.displayMetrics)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val shape = profile ?: return
        val map = ImageMapping.fit(SilhouettePainter.bounds(shape), width, height, 28 * density)
        val body = SilhouettePainter.bodyPath(shape, map)
        canvas.drawPath(body, bodyFill)
        canvas.drawPath(body, bodyLine)
        val axisX = map.x((shape.left + shape.right) / 2f)
        canvas.drawLine(axisX, map.y(shape.top) - 8 * density, axisX, map.y(shape.bottom) + 8 * density, axis)
        val handle = shape.handle ?: return
        if (!handleEnabled) return
        SilhouettePainter.drawHandleBody(canvas, handle, map, handleFill)
        canvas.drawPath(SilhouettePainter.midlinePath(handle, map), midline)
        val alignRight = handle.side == HandleSide.RIGHT
        text.textAlign = if (alignRight) Paint.Align.RIGHT else Paint.Align.LEFT
        val labelX = if (alignRight) width - 10 * density else 10 * density
        listOf(handle.path.first() to "upper join", handle.path.last() to "lower join")
            .forEach { (point, label) ->
                val x = map.x(point.x)
                val y = map.y(point.y)
                canvas.drawCircle(x, y, 5 * density, joinFill)
                canvas.drawCircle(x, y, 5 * density, joinLine)
                canvas.drawText(label, labelX, y + 4 * density, text)
            }
    }

    private fun updateDescription() {
        val handle = profile?.handle
        contentDescription = when {
            profile == null -> "No vessel outline yet"
            handle == null -> "Vessel outline without a handle"
            else -> "Handle traced on the ${handle.side.name.lowercase()} side"
        }
    }
}
