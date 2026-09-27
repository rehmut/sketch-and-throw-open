package io.github.rehmut.sketchandthrow

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.camera.core.ImageProxy

/** Turns a picked or captured photo into the upright, analysis-sized bitmap the app traces. */
internal object PhotoInput {
    /** Longest side analysed; larger photos add time without a better outline. */
    private const val MAX_SIDE = 768f

    fun fromUri(resolver: ContentResolver, uri: Uri): Bitmap {
        val decoded = if (Build.VERSION.SDK_INT >= 28) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.getBitmap(resolver, uri)
        }
        return prepare(decoded, 0)
    }

    /** Decodes a camera frame and closes it. Call off the main thread. */
    fun fromCapture(image: ImageProxy): Bitmap = image.use {
        prepare(it.toBitmap(), it.imageInfo.rotationDegrees)
    }

    /** Scales so the longest side is at most [MAX_SIDE], turning the frame upright. */
    private fun prepare(source: Bitmap, rotationDegrees: Int): Bitmap {
        val scale = minOf(1f, MAX_SIDE / maxOf(source.width, source.height))
        if (scale == 1f && rotationDegrees == 0) return source
        val matrix = Matrix().apply {
            postScale(scale, scale)
            postRotate(rotationDegrees.toFloat())
        }
        val prepared = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        // Software copy: the ML segmenter and pixel reads need a CPU-backed bitmap.
        return if (prepared.config == Bitmap.Config.HARDWARE)
            prepared.copy(Bitmap.Config.ARGB_8888, false) else prepared
    }
}
