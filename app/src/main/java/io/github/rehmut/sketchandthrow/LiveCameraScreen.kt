package io.github.rehmut.sketchandthrow

import android.graphics.RectF
import android.widget.ImageButton
import androidx.activity.ComponentActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis.COORDINATE_SYSTEM_VIEW_REFERENCED
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.ObjectDetector
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions

/**
 * The live camera: preview with tracked-object boxes and a shutter that hands a
 * still to [onCaptured]. The caller owns the frame and must close it.
 */
internal class LiveCameraScreen(
    private val activity: ComponentActivity,
    private val preview: PreviewView,
    private val overlay: DetectionOverlay,
    private val shutter: ImageButton,
    private val isVisible: () -> Boolean,
    private val onStatus: (String) -> Unit,
    private val onCaptured: (ImageProxy) -> Unit
) {
    private var controller: LifecycleCameraController? = null
    private var detector: ObjectDetector? = null

    init {
        shutter.setOnClickListener { capture() }
    }

    fun start() {
        controller?.let { existing ->
            try {
                existing.bindToLifecycle(activity)
                onStatus("Looking for an object…")
            } catch (error: Exception) {
                onStatus("Could not start camera: ${error.localizedMessage ?: "unknown error"}")
            }
            return
        }
        val objectDetector = ObjectDetection.getClient(
            ObjectDetectorOptions.Builder().setDetectorMode(ObjectDetectorOptions.STREAM_MODE).build()
        )
        detector = objectDetector
        val cameraController = LifecycleCameraController(activity).apply {
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            setEnabledUseCases(
                LifecycleCameraController.IMAGE_ANALYSIS or LifecycleCameraController.IMAGE_CAPTURE)
            imageCaptureMode = ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
        }
        val mainExecutor = ContextCompat.getMainExecutor(activity)
        cameraController.setImageAnalysisAnalyzer(mainExecutor,
            MlKitAnalyzer(listOf(objectDetector), COORDINATE_SYSTEM_VIEW_REFERENCED, mainExecutor) { result ->
                if (!isVisible()) return@MlKitAnalyzer
                val error = result?.getThrowable(objectDetector)
                if (error != null) {
                    overlay.showBoxes(emptyList())
                    onStatus("Detection error: ${error.localizedMessage ?: "unknown error"}")
                    return@MlKitAnalyzer
                }
                val objects = result?.getValue(objectDetector).orEmpty()
                overlay.showBoxes(objects.map { RectF(it.boundingBox) })
                onStatus(if (objects.isEmpty()) "Looking for an object…"
                else "Tracking ${objects.size} object${if (objects.size == 1) "" else "s"}. Tap the shutter.")
            })
        preview.controller = cameraController
        try {
            cameraController.bindToLifecycle(activity)
            controller = cameraController
            onStatus("Looking for an object…")
        } catch (error: Exception) {
            cameraController.clearImageAnalysisAnalyzer()
            objectDetector.close()
            detector = null
            onStatus("Could not start camera: ${error.localizedMessage ?: "unknown error"}")
        }
    }

    fun stop() {
        controller?.unbind()
    }

    fun release() {
        controller?.clearImageAnalysisAnalyzer()
        controller?.unbind()
        detector?.close()
    }

    private fun capture() {
        val camera = controller ?: return
        shutter.isEnabled = false
        onStatus("Taking picture…")
        camera.takePicture(ContextCompat.getMainExecutor(activity),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    shutter.isEnabled = true
                    onCaptured(image)
                }

                override fun onError(error: ImageCaptureException) {
                    shutter.isEnabled = true
                    onStatus("Could not take the picture: ${error.localizedMessage ?: "camera error"}")
                }
            })
    }
}
