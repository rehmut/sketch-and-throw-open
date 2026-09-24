package com.example.potterywheeldetector

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.RectF
import android.os.Bundle
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis.COORDINATE_SYSTEM_VIEW_REFERENCED
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.ObjectDetector
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions

class MainActivity : ComponentActivity() {
    private lateinit var preview: PreviewView
    private lateinit var overlay: DetectionOverlay
    private lateinit var status: TextView
    private var controller: LifecycleCameraController? = null
    private var detector: ObjectDetector? = null

    private val requestCamera = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startCamera() else status.text =
            "Camera permission is needed to detect objects. Grant it in app settings."
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        preview = findViewById(R.id.preview)
        overlay = findViewById(R.id.overlay)
        status = findViewById(R.id.status)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            requestCamera.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {
        if (controller != null) return
        val options = ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
            .build()
        val objectDetector = ObjectDetection.getClient(options)
        detector = objectDetector

        val cameraController = LifecycleCameraController(this)
        cameraController.cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
        cameraController.setEnabledUseCases(LifecycleCameraController.IMAGE_ANALYSIS)
        val mainExecutor = ContextCompat.getMainExecutor(this)
        cameraController.setImageAnalysisAnalyzer(
            mainExecutor,
            MlKitAnalyzer(
                listOf(objectDetector),
                COORDINATE_SYSTEM_VIEW_REFERENCED,
                mainExecutor
            ) { result ->
                val error = result?.getThrowable(objectDetector)
                if (error != null) {
                    overlay.showBoxes(emptyList())
                    status.text = "Detection error: ${error.localizedMessage ?: "unknown error"}"
                    return@MlKitAnalyzer
                }
                val objects = result?.getValue(objectDetector).orEmpty()
                overlay.showBoxes(objects.map { RectF(it.boundingBox) })
                status.text = if (objects.isEmpty()) {
                    "Looking for an object…"
                } else {
                    "Tracking ${objects.size} visible object${if (objects.size == 1) "" else "s"}"
                }
            }
        )

        preview.controller = cameraController
        try {
            cameraController.bindToLifecycle(this)
            controller = cameraController
            status.text = "Looking for an object…"
        } catch (error: Exception) {
            cameraController.clearImageAnalysisAnalyzer()
            objectDetector.close()
            detector = null
            status.text = "Could not start camera: ${error.localizedMessage ?: "unknown error"}"
        }
    }

    override fun onDestroy() {
        controller?.clearImageAnalysisAnalyzer()
        detector?.close()
        super.onDestroy()
    }
}
