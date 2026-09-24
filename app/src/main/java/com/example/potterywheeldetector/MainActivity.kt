package com.example.potterywheeldetector

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis.COORDINATE_SYSTEM_VIEW_REFERENCED
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.ByteBufferExtractor
import com.google.mediapipe.tasks.components.containers.NormalizedKeypoint
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.interactivesegmenter.InteractiveSegmenter
import com.google.mediapipe.tasks.vision.interactivesegmenter.InteractiveSegmenterOptions
import com.google.mediapipe.tasks.vision.interactivesegmenter.Stroke
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.ObjectDetector
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    private lateinit var preview: PreviewView
    private lateinit var overlay: DetectionOverlay
    private lateinit var status: TextView
    private lateinit var cameraPanel: FrameLayout
    private lateinit var galleryPanel: ScrollView
    private lateinit var galleryImage: GalleryOutlineView
    private lateinit var profileView: ProfileView
    private lateinit var vessel3dView: Vessel3DView
    private lateinit var exportButton: Button
    private lateinit var sensitivity: SeekBar
    private lateinit var mlOutlineSwitch: Switch
    private val worker = Executors.newSingleThreadExecutor()
    private var controller: LifecycleCameraController? = null
    private var detector: ObjectDetector? = null
    private var photo: Bitmap? = null
    private var focus: Rect? = null
    private var seed: PointF? = null
    private var mesh: VesselMesh? = null
    private var photoVersion = 0
    private var extractionVersion = 0
    private var mlConfidence: FloatArray? = null
    private var mlModelState = ""
    private var mlRequestVersion = -1
    private var mlPoint: PointF? = null
    private var interactiveSegmenter: InteractiveSegmenter? = null

    private val requestCamera = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) showCamera() else status.text =
            "Camera permission denied. You can still use Pick photo."
    }

    private val pickPhoto = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) loadPhoto(uri)
    }

    private val saveObj = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val data = mesh?.toObj() ?: return@registerForActivityResult
        if (uri == null) return@registerForActivityResult
        try {
            contentResolver.openOutputStream(uri)?.use { stream ->
                stream.write(data.toByteArray(Charsets.UTF_8))
            } ?: error("Could not open destination")
            status.text = "3D vessel saved as OBJ. Dimensions are relative until calibrated."
        } catch (error: Exception) {
            status.text = "Could not save OBJ: ${error.localizedMessage ?: "unknown error"}"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        preview = findViewById(R.id.preview)
        overlay = findViewById(R.id.overlay)
        status = findViewById(R.id.status)
        cameraPanel = findViewById(R.id.cameraPanel)
        galleryPanel = findViewById(R.id.galleryPanel)
        galleryImage = findViewById(R.id.galleryImage)
        profileView = findViewById(R.id.profileView)
        vessel3dView = findViewById(R.id.vessel3dView)
        exportButton = findViewById(R.id.exportButton)
        sensitivity = findViewById(R.id.sensitivity)
        mlOutlineSwitch = findViewById(R.id.mlOutlineSwitch)

        findViewById<Button>(R.id.liveButton).setOnClickListener { showCamera() }
        findViewById<Button>(R.id.pickButton).setOnClickListener {
            pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        exportButton.setOnClickListener { saveObj.launch("pottery-vessel.obj") }
        galleryImage.onVesselTap = { point ->
            seed = point
            if (mlOutlineSwitch.isChecked && photo != null) {
                requestMlOutline(photo!!, photoVersion)
            }
            runExtraction()
        }
        sensitivity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) = Unit
            override fun onStartTrackingTouch(bar: SeekBar?) = Unit
            override fun onStopTrackingTouch(bar: SeekBar?) { runExtraction() }
        })
        mlOutlineSwitch.setOnCheckedChangeListener { _, checked ->
            if (checked && photo != null) {
                requestMlOutline(photo!!, photoVersion)
            }
            runExtraction()
        }
        showCamera()
    }

    private fun showCamera() {
        cameraPanel.visibility = View.VISIBLE
        galleryPanel.visibility = View.GONE
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestCamera.launch(Manifest.permission.CAMERA)
            return
        }
        startCamera()
    }

    private fun startCamera() {
        if (controller != null) {
            try {
                controller?.bindToLifecycle(this)
                status.text = "Looking for an object…"
            } catch (error: Exception) {
                status.text = "Could not start camera: ${error.localizedMessage ?: "unknown error"}"
            }
            return
        }
        val objectDetector = ObjectDetection.getClient(
            ObjectDetectorOptions.Builder()
                .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
                .build()
        )
        detector = objectDetector
        val cameraController = LifecycleCameraController(this)
        cameraController.cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
        cameraController.setEnabledUseCases(LifecycleCameraController.IMAGE_ANALYSIS)
        val mainExecutor = ContextCompat.getMainExecutor(this)
        cameraController.setImageAnalysisAnalyzer(
            mainExecutor,
            MlKitAnalyzer(listOf(objectDetector), COORDINATE_SYSTEM_VIEW_REFERENCED,
                mainExecutor) { result ->
                if (galleryPanel.visibility == View.VISIBLE) return@MlKitAnalyzer
                val error = result?.getThrowable(objectDetector)
                if (error != null) {
                    overlay.showBoxes(emptyList())
                    status.text = "Detection error: ${error.localizedMessage ?: "unknown error"}"
                    return@MlKitAnalyzer
                }
                val objects = result?.getValue(objectDetector).orEmpty()
                overlay.showBoxes(objects.map { RectF(it.boundingBox) })
                status.text = if (objects.isEmpty()) "Looking for an object…"
                else "Tracking ${objects.size} visible object${if (objects.size == 1) "" else "s"}"
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

    private fun loadPhoto(uri: Uri) {
        photoVersion++
        extractionVersion++
        val version = photoVersion
        controller?.unbind()
        cameraPanel.visibility = View.GONE
        galleryPanel.visibility = View.VISIBLE
        status.text = "Opening photo…"
        photo = null
        focus = null
        seed = null
        mlConfidence = null
        mlModelState = ""
        mlRequestVersion = -1
        mlPoint = null
        mesh = null
        galleryImage.profile = null
        profileView.profile = null
        vessel3dView.mesh = null
        exportButton.isEnabled = false
        worker.execute {
            try {
                val decoded = if (Build.VERSION.SDK_INT >= 28) {
                    val source = ImageDecoder.createSource(contentResolver, uri)
                    ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    }
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(contentResolver, uri)
                }
                val ratio = 768f / maxOf(decoded.width, decoded.height)
                val bitmap = if (ratio < 1f) Bitmap.createScaledBitmap(decoded,
                    (decoded.width * ratio).toInt(), (decoded.height * ratio).toInt(), true)
                else decoded
                runOnUiThread {
                    if (version != photoVersion || isDestroyed) return@runOnUiThread
                    photo = bitmap
                    galleryImage.bitmap = bitmap
                    status.text = "Finding the vessel…"
                    detectPhotoObject(bitmap, version)
                }
            } catch (error: Exception) {
                runOnUiThread {
                    if (version == photoVersion) status.text =
                        "Could not open photo: ${error.localizedMessage ?: "unknown error"}"
                }
            }
        }
    }

    private fun detectPhotoObject(bitmap: Bitmap, version: Int) {
        val imageDetector = ObjectDetection.getClient(
            ObjectDetectorOptions.Builder()
                .setDetectorMode(ObjectDetectorOptions.SINGLE_IMAGE_MODE)
                .enableMultipleObjects()
                .build()
        )
        imageDetector.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { objects ->
                if (version == photoVersion) {
                    focus = objects.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
                        ?.boundingBox
                    runExtraction()
                    if (mlOutlineSwitch.isChecked) requestMlOutline(bitmap, version)
                }
            }
            .addOnFailureListener { error ->
                if (version == photoVersion) {
                    status.text = "ML Kit could not locate an object; trying the photo outline."
                    runExtraction()
                    if (mlOutlineSwitch.isChecked) requestMlOutline(bitmap, version)
                }
            }
            .addOnCompleteListener { imageDetector.close() }
    }

    private fun requestMlOutline(bitmap: Bitmap, version: Int) {
        if (version != photoVersion) return
        val point = seed ?: focus?.let {
            PointF(it.centerX().toFloat(), it.centerY().toFloat())
        } ?: PointF(bitmap.width / 2f, bitmap.height / 2f)
        if (mlRequestVersion == version && mlPoint == point) return
        mlRequestVersion = version
        mlPoint = PointF(point.x, point.y)
        mlConfidence = null
        mlModelState = "Finding vessel edge with ML…"
        status.text = mlModelState
        worker.execute {
            try {
                val segmenter = interactiveSegmenter ?: InteractiveSegmenter.createFromOptions(
                    this,
                    InteractiveSegmenterOptions.builder().setBaseOptions(
                        BaseOptions.builder().setModelAssetPath("interactive_segmentation.task").build()
                    ).build()
                ).also { interactiveSegmenter = it }
                segmenter.setImage(BitmapImageBuilder(bitmap).build())
                val stroke = Stroke.builder()
                    .setBrushMode(Stroke.BrushMode.POSITIVE)
                    .setPoints(listOf(NormalizedKeypoint.create(
                        (point.x / bitmap.width).coerceIn(0f, 1f),
                        (point.y / bitmap.height).coerceIn(0f, 1f)
                    )))
                    .setCompleted(true)
                    .build()
                val mask = segmenter.segment(listOf(stroke))
                val values = FloatArray(mask.width * mask.height)
                ByteBufferExtractor.extract(mask).asFloatBuffer().apply {
                    rewind()
                    get(values)
                }
                runOnUiThread {
                    if (version != photoVersion || mlPoint != point || isDestroyed) return@runOnUiThread
                    mlConfidence = values
                    mlModelState = ""
                    runExtraction()
                }
            } catch (error: Exception) {
                runOnUiThread {
                    if (version != photoVersion || mlPoint != point || isDestroyed) return@runOnUiThread
                    mlRequestVersion = -1
                    mlModelState = "ML outline failed: ${error.localizedMessage ?: "unknown error"}. Colour outline shown."
                    runExtraction()
                }
            }
        }
    }

    private fun runExtraction() {
        val image = photo ?: return
        val photoId = photoVersion
        val runId = ++extractionVersion
        val target = focus?.let(::Rect)
        val selectedPoint = seed?.let { PointF(it.x, it.y) }
        val level = sensitivity.progress
        val confidence = if (mlOutlineSwitch.isChecked) mlConfidence else null
        status.text = "Tracing outline…"
        worker.execute {
            val mlProfile = confidence?.let {
                VesselProfileExtractor.extractFromConfidence(image.width, image.height,
                    it, target, selectedPoint, level)
            }
            val profile = mlProfile ?: VesselProfileExtractor.extract(
                image, target, selectedPoint, level)
            val newMesh = profile?.let(VesselMeshBuilder::build)
            runOnUiThread {
                if (photoId != photoVersion || runId != extractionVersion || isDestroyed)
                    return@runOnUiThread
                galleryImage.profile = profile
                profileView.profile = profile
                vessel3dView.mesh = newMesh
                mesh = newMesh
                exportButton.isEnabled = newMesh != null
                status.text = if (profile == null) {
                    "No clear outline found. Try a plain background, tap the vessel, or adjust sensitivity."
                } else if (mlProfile != null) {
                    "ML vessel outline. Adjust sensitivity or tap inside the vessel if needed."
                } else if (mlOutlineSwitch.isChecked && mlModelState.isNotEmpty()) {
                    mlModelState
                } else {
                    "Colour outline. Drag the 3D vessel to rotate; export OBJ to save the mesh."
                }
            }
        }
    }

    override fun onDestroy() {
        controller?.clearImageAnalysisAnalyzer()
        controller?.unbind()
        detector?.close()
        worker.shutdownNow()
        interactiveSegmenter?.close()
        super.onDestroy()
    }
}
