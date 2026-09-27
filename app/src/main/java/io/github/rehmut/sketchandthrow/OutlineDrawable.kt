package io.github.rehmut.sketchandthrow

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable

/**
 * A saved vessel's own traced outline, drawn like the category illustrations:
 * pale body, graphite line. Two mugs look like two different mugs.
 */
class OutlineDrawable(
    context: Context,
    private val profile: VesselProfile,
    private val showHandle: Boolean
) : Drawable() {
    private val density = context.resources.displayMetrics.density
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.graphite)
        style = Paint.Style.STROKE
        strokeWidth = 1.4f * density
        strokeJoin = Paint.Join.ROUND
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.getColor(R.color.key) }
    /** Handle outline: a graphite stroke with a narrower pale stroke inside it. */
    private val handleEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.getColor(R.color.graphite) }
    private val handleFace = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.getColor(R.color.key) }

    override fun draw(canvas: Canvas) {
        val area = bounds
        val shape = if (showHandle) profile else profile.copy(handle = null)
        val box = SilhouettePainter.bounds(shape)
        val margin = 3 * density
        val scale = minOf((area.width() - 2 * margin) / box.width().coerceAtLeast(1f),
            (area.height() - 2 * margin) / box.height().coerceAtLeast(1f))
        val map = ImageMapping(scale,
            area.exactCenterX() - box.centerX() * scale,
            area.exactCenterY() - box.centerY() * scale)
        shape.handle?.let { handle ->
            // Every edge segment first, then every face segment: interleaving them
            // lets each dark edge cover the previous pale face.
            val edge = 2 * line.strokeWidth
            listOf(handleEdge to edge, handleFace to 0f).forEach { (paint, extra) ->
                paint.style = Paint.Style.STROKE
                paint.strokeCap = Paint.Cap.ROUND
                handle.path.zipWithNext { a, b ->
                    paint.strokeWidth = (a.thickness + b.thickness) / 2f * scale + extra
                    canvas.drawLine(map.x(a.x), map.y(a.y), map.x(b.x), map.y(b.y), paint)
                }
            }
        }
        val body = SilhouettePainter.bodyPath(shape, map)
        canvas.drawPath(body, fill)
        canvas.drawPath(body, line)
    }

    override fun setAlpha(alpha: Int) {
        listOf(line, fill, handleEdge, handleFace).forEach { it.alpha = alpha }
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        listOf(line, fill, handleEdge, handleFace).forEach { it.colorFilter = colorFilter }
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}
