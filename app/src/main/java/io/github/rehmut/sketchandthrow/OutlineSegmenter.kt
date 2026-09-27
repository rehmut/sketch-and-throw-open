package io.github.rehmut.sketchandthrow

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PointF
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.ByteBufferExtractor
import com.google.mediapipe.tasks.components.containers.NormalizedKeypoint
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.interactivesegmenter.InteractiveSegmenter
import com.google.mediapipe.tasks.vision.interactivesegmenter.InteractiveSegmenterOptions
import com.google.mediapipe.tasks.vision.interactivesegmenter.Stroke

/**
 * The bundled MagicTouch model: given a photo and one point on an object, a
 * per-pixel confidence that the pixel belongs to that object. Loaded on first
 * use; call [confidence] off the main thread.
 */
internal class OutlineSegmenter(private val context: Context) {
    private var segmenter: InteractiveSegmenter? = null

    fun confidence(bitmap: Bitmap, point: PointF): FloatArray {
        val model = segmenter ?: InteractiveSegmenter.createFromOptions(
            context,
            InteractiveSegmenterOptions.builder().setBaseOptions(
                BaseOptions.builder().setModelAssetPath(MODEL).build()
            ).build()
        ).also { segmenter = it }
        model.setImage(BitmapImageBuilder(bitmap).build())
        val stroke = Stroke.builder()
            .setBrushMode(Stroke.BrushMode.POSITIVE)
            .setPoints(listOf(NormalizedKeypoint.create(
                (point.x / bitmap.width).coerceIn(0f, 1f),
                (point.y / bitmap.height).coerceIn(0f, 1f)
            )))
            .setCompleted(true)
            .build()
        val mask = model.segment(listOf(stroke))
        val values = FloatArray(mask.width * mask.height)
        ByteBufferExtractor.extract(mask).asFloatBuffer().apply {
            rewind()
            get(values)
        }
        return values
    }

    fun close() {
        segmenter?.close()
        segmenter = null
    }

    private companion object {
        const val MODEL = "interactive_segmentation.task"
    }
}
