package io.github.rehmut.sketchandthrow

import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF

/** Maps image pixels onto a view: uniform scale plus offset. */
internal class ImageMapping(val scale: Float, val offsetX: Float, val offsetY: Float) {
    fun x(imageX: Float) = offsetX + imageX * scale
    fun y(imageY: Float) = offsetY + imageY * scale

    companion object {
        /** Fits [bounds] (image pixels) centred inside a view, leaving [margin] all round. */
        fun fit(bounds: RectF, viewWidth: Int, viewHeight: Int, margin: Float): ImageMapping {
            val scale = minOf((viewWidth - 2 * margin) / bounds.width().coerceAtLeast(1f),
                (viewHeight - 2 * margin) / bounds.height().coerceAtLeast(1f))
            return ImageMapping(scale,
                viewWidth / 2f - bounds.centerX() * scale,
                viewHeight / 2f - bounds.centerY() * scale)
        }
    }
}

/** Drawing shared by the photo overlay, the 2D profile and the handle view. */
internal object SilhouettePainter {
    fun bodyPath(profile: VesselProfile, map: ImageMapping): Path = Path().apply {
        profile.rows.forEachIndexed { index, row ->
            if (index == 0) moveTo(map.x(row.left), map.y(row.y)) else lineTo(map.x(row.left), map.y(row.y))
        }
        profile.rows.asReversed().forEach { row -> lineTo(map.x(row.right), map.y(row.y)) }
        close()
    }

    fun midlinePath(handle: VesselHandle, map: ImageMapping): Path = Path().apply {
        handle.path.forEachIndexed { index, point ->
            if (index == 0) moveTo(map.x(point.x), map.y(point.y)) else lineTo(map.x(point.x), map.y(point.y))
        }
    }

    /** The handle as a solid stroke whose width follows the traced thickness. */
    fun drawHandleBody(canvas: Canvas, handle: VesselHandle, map: ImageMapping, paint: Paint) {
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        handle.path.zipWithNext { a, b ->
            paint.strokeWidth = (a.thickness + b.thickness) / 2f * map.scale
            canvas.drawLine(map.x(a.x), map.y(a.y), map.x(b.x), map.y(b.y), paint)
        }
    }

    /** Image-space bounds covering body and handle. */
    fun bounds(profile: VesselProfile): RectF {
        val box = RectF(profile.left, profile.top, profile.right, profile.bottom)
        profile.handle?.path?.forEach { point ->
            val half = point.thickness / 2f
            box.union(point.x - half, point.y - half)
            box.union(point.x + half, point.y + half)
        }
        return box
    }

    fun dashed(paint: Paint, on: Float, off: Float) = paint.apply {
        pathEffect = DashPathEffect(floatArrayOf(on, off), 0f)
    }
}
