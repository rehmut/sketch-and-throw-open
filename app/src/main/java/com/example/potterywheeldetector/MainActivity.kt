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
import android.text.Html
import android.text.method.LinkMovementMethod
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.ArrayAdapter
import android.widget.ImageView
import androidx.activity.OnBackPressedCallback
import androidx.activity.ComponentActivity
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis.COORDINATE_SYSTEM_VIEW_REFERENCED
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import android.app.AlertDialog
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
import java.util.Locale

class MainActivity : ComponentActivity() {
    private lateinit var preview: PreviewView
    private lateinit var overlay: DetectionOverlay
    private lateinit var status: TextView
    private lateinit var cameraPanel: FrameLayout
    private lateinit var homePanel: LinearLayout
    private lateinit var libraryPanel: ScrollView
    private lateinit var galleryPanel: ScrollView
    private lateinit var viewerPanel: LinearLayout
    private lateinit var libraryContent: LinearLayout
    private lateinit var homeButton: Button
    private lateinit var heroImage: ImageView
    private lateinit var heroCreditText: TextView
    private lateinit var galleryImage: GalleryOutlineView
    private lateinit var profileView: ProfileView
    private lateinit var vessel3dView: Vessel3DView
    private lateinit var exportButton: Button
    private lateinit var saveVesselButton: Button
    private lateinit var open3dButton: Button
    private lateinit var heightInput: EditText
    private lateinit var wallInput: EditText
    private lateinit var allowanceInput: EditText
    private lateinit var measurementsText: TextView
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
    private lateinit var vesselStore: VesselStore
    private var currentSavedId: String? = null
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
        vesselStore = VesselStore(this)
        overlay = findViewById(R.id.overlay)
        status = findViewById(R.id.status)
        cameraPanel = findViewById(R.id.cameraPanel)
        homePanel = findViewById(R.id.homePanel)
        libraryPanel = findViewById(R.id.libraryPanel)
        galleryPanel = findViewById(R.id.galleryPanel)
        viewerPanel = findViewById(R.id.viewerPanel)
        libraryContent = findViewById(R.id.libraryContent)
        homeButton = findViewById(R.id.libraryButton)
        heroImage = findViewById(R.id.heroImage)
        heroCreditText = findViewById(R.id.heroCreditText)
        galleryImage = findViewById(R.id.galleryImage)
        profileView = findViewById(R.id.profileView)
        vessel3dView = findViewById(R.id.vessel3dView)
        exportButton = findViewById(R.id.exportButton)
        saveVesselButton = findViewById(R.id.saveVesselButton)
        open3dButton = findViewById(R.id.open3dButton)
        heightInput = findViewById(R.id.heightInput)
        wallInput = findViewById(R.id.wallInput)
        allowanceInput = findViewById(R.id.allowanceInput)
        measurementsText = findViewById(R.id.measurementsText)
        sensitivity = findViewById(R.id.sensitivity)
        mlOutlineSwitch = findViewById(R.id.mlOutlineSwitch)

        homeButton.setOnClickListener { showHome() }
        findViewById<Button>(R.id.newFolderButton).setOnClickListener { createFolderDialog() }
        findViewById<View>(R.id.myVesselsButton).setOnClickListener { showLibrary() }
        findViewById<View>(R.id.liveButton).setOnClickListener { showCamera() }
        findViewById<View>(R.id.pickButton).setOnClickListener {
            pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        exportButton.setOnClickListener { saveObj.launch("pottery-vessel.obj") }
        saveVesselButton.setOnClickListener { saveVesselDialog() }
        open3dButton.setOnClickListener { show3d() }
        findViewById<Button>(R.id.backToProfileButton).setOnClickListener { showGallery() }
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
        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) { updateMeasurements() }
        }
        heightInput.addTextChangedListener(textWatcher)
        wallInput.addTextChangedListener(textWatcher)
        allowanceInput.addTextChangedListener(textWatcher)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    viewerPanel.visibility == View.VISIBLE -> showGallery()
                    homePanel.visibility != View.VISIBLE -> showHome()
                    else -> finish()
                }
            }
        })
        showBowlPhoto()
        showHome()
    }

    private fun showBowlPhoto() {
        heroImage.setImageResource(R.drawable.hero_bowl)
        heroCreditText.text = Html.fromHtml(
            "Photo: <a href=\"https://commons.wikimedia.org/wiki/File:Light_green_ceramic_bowl_1.jpg\">W.carter</a> · " +
                "<a href=\"https://creativecommons.org/licenses/by-sa/4.0/\">CC BY-SA 4.0</a> · display cropped",
            Html.FROM_HTML_MODE_LEGACY
        )
        heroCreditText.movementMethod = LinkMovementMethod.getInstance()
    }

    private fun showCamera() {
        status.visibility = View.VISIBLE
        homeButton.visibility = View.VISIBLE
        homePanel.visibility = View.GONE
        libraryPanel.visibility = View.GONE
        viewerPanel.visibility = View.GONE
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
        status.visibility = View.VISIBLE
        photoVersion++
        extractionVersion++
        val version = photoVersion
        controller?.unbind()
        homeButton.visibility = View.VISIBLE
        cameraPanel.visibility = View.GONE
        homePanel.visibility = View.GONE
        libraryPanel.visibility = View.GONE
        viewerPanel.visibility = View.GONE
        galleryPanel.visibility = View.VISIBLE
        status.text = "Opening photo…"
        currentSavedId = null
        heightInput.setText("")
        wallInput.setText("5")
        allowanceInput.setText("25")
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
        saveVesselButton.isEnabled = false
        open3dButton.isEnabled = false
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
                saveVesselButton.isEnabled = newMesh != null
                open3dButton.isEnabled = newMesh != null
                updateMeasurements()
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

    private fun showHome() {
        controller?.unbind()
        status.visibility = View.GONE
        homeButton.visibility = View.GONE
        cameraPanel.visibility = View.GONE
        galleryPanel.visibility = View.GONE
        viewerPanel.visibility = View.GONE
        libraryPanel.visibility = View.GONE
        homePanel.visibility = View.VISIBLE
    }

    private fun showLibrary() {
        controller?.unbind()
        status.visibility = View.VISIBLE
        homeButton.visibility = View.VISIBLE
        cameraPanel.visibility = View.GONE
        galleryPanel.visibility = View.GONE
        viewerPanel.visibility = View.GONE
        homePanel.visibility = View.GONE
        libraryPanel.visibility = View.VISIBLE
        renderLibrary()
        status.text = "Choose a saved vessel or create a folder."
    }

    private fun showGallery() {
        status.visibility = View.VISIBLE
        homeButton.visibility = View.VISIBLE
        cameraPanel.visibility = View.GONE
        homePanel.visibility = View.GONE
        libraryPanel.visibility = View.GONE
        viewerPanel.visibility = View.GONE
        galleryPanel.visibility = View.VISIBLE
    }

    private fun show3d() {
        if (mesh == null) return
        status.visibility = View.VISIBLE
        homeButton.visibility = View.VISIBLE
        cameraPanel.visibility = View.GONE
        homePanel.visibility = View.GONE
        libraryPanel.visibility = View.GONE
        galleryPanel.visibility = View.GONE
        viewerPanel.visibility = View.VISIBLE
        status.text = "Drag on the model to rotate it; use Back to profile to return."
    }

    private fun renderLibrary() {
        libraryContent.removeAllViews()
        for (folder in vesselStore.folders()) {
            val folderRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            val heading = TextView(this).apply {
                text = folder.name
                textSize = 19f
                setTextColor(0xff2d241e.toInt())
                setPadding(4, 24, 4, 8)
            }
            folderRow.addView(heading, LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            folderRow.addView(Button(this).apply {
                text = "Edit folder"
                isAllCaps = false
                setOnClickListener { editFolderDialog(folder) }
            })
            libraryContent.addView(folderRow)
            val saved = vesselStore.vessels(folder.id)
            if (saved.isEmpty()) {
                libraryContent.addView(TextView(this).apply {
                    text = "No vessels yet"
                    setPadding(12, 4, 4, 12)
                })
            }
            for (entry in saved) {
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(4, 6, 4, 6)
                }
                val thumbnail = ImageView(this).apply {
                    setImageBitmap(vesselStore.loadThumbnail(entry.id))
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    setOnClickListener { openSavedVessel(entry) }
                }
                row.addView(thumbnail, LinearLayout.LayoutParams(72.dp, 72.dp))
                val open = Button(this).apply {
                    text = entry.name
                    isAllCaps = false
                    setOnClickListener { openSavedVessel(entry) }
                }
                row.addView(open, LinearLayout.LayoutParams(0, 72.dp, 1f))
                val delete = Button(this).apply {
                    text = "Delete"
                    isAllCaps = false
                    setOnClickListener {
                        AlertDialog.Builder(this@MainActivity)
                            .setTitle("Delete ${entry.name}?")
                            .setMessage("This removes the saved photo and vessel profile from the app.")
                            .setNegativeButton("Cancel", null)
                            .setPositiveButton("Delete") { _, _ ->
                                vesselStore.delete(entry.id)
                                if (currentSavedId == entry.id) currentSavedId = null
                                renderLibrary()
                            }.show()
                    }
                }
                row.addView(delete, LinearLayout.LayoutParams(96.dp, 72.dp))
                libraryContent.addView(row)
            }
        }
    }

    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()

    private fun createFolderDialog() {
        val input = EditText(this).apply { hint = "Folder name, e.g. Bowls" }
        AlertDialog.Builder(this).setTitle("New folder").setView(input)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Create") { _, _ ->
                try {
                    vesselStore.createFolder(input.text.toString())
                    renderLibrary()
                    status.text = "Folder created."
                } catch (error: Exception) {
                    status.text = "Choose a nonempty folder name that is not already used."
                }
            }.show()
    }

    private fun editFolderDialog(folder: VesselFolder) {
        AlertDialog.Builder(this).setTitle(folder.name)
            .setItems(arrayOf("Rename folder", "Delete empty folder")) { _, choice ->
                if (choice == 0) {
                    val input = EditText(this).apply { setText(folder.name); selectAll() }
                    AlertDialog.Builder(this).setTitle("Rename folder").setView(input)
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Rename") { _, _ ->
                            try {
                                vesselStore.renameFolder(folder.id, input.text.toString())
                                renderLibrary()
                            } catch (error: Exception) {
                                status.text = "Choose a nonempty name that is not already used."
                            }
                        }.show()
                } else {
                    if (vesselStore.vessels(folder.id).isNotEmpty()) {
                        status.text = "Move or delete this folder's vessels before deleting it."
                    } else {
                        AlertDialog.Builder(this).setTitle("Delete ${folder.name}?")
                            .setNegativeButton("Cancel", null)
                            .setPositiveButton("Delete") { _, _ ->
                                try {
                                    vesselStore.deleteFolder(folder.id)
                                    renderLibrary()
                                } catch (error: Exception) {
                                    status.text = "Keep at least one folder in the library."
                                }
                            }.show()
                    }
                }
            }.show()
    }

    private fun saveVesselDialog() {
        val bitmap = photo ?: return
        val profile = profileView.profile ?: return
        val folders = vesselStore.folders()
        val previous = vesselStore.find(currentSavedId)
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16.dp, 4.dp, 16.dp, 4.dp)
        }
        val nameInput = EditText(this).apply {
            hint = "Vessel name"
            setText(previous?.name ?: "Untitled vessel")
            selectAll()
        }
        val folderSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item,
                folders.map { it.name })
            val index = folders.indexOfFirst { it.id == previous?.folderId }
            if (index >= 0) setSelection(index)
        }
        form.addView(nameInput)
        form.addView(folderSpinner)
        AlertDialog.Builder(this).setTitle(if (previous == null) "Save vessel" else "Update vessel")
            .setView(form).setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                try {
                    val folder = folders[folderSpinner.selectedItemPosition]
                    val entry = vesselStore.save(currentSavedId, folder.id,
                        nameInput.text.toString(), bitmap, profile,
                        heightInput.text.toString().replace(',', '.').toDoubleOrNull(),
                        wallInput.text.toString().replace(',', '.').toDoubleOrNull() ?: 5.0,
                        allowanceInput.text.toString().replace(',', '.').toDoubleOrNull() ?: 25.0)
                    currentSavedId = entry.id
                    status.text = "Saved ${entry.name} in ${folder.name}."
                } catch (error: Exception) {
                    status.text = "Could not save vessel: ${error.localizedMessage ?: "unknown error"}"
                }
            }.show()
    }

    private fun openSavedVessel(entry: SavedVessel) {
        val bitmap = vesselStore.loadImage(entry.id)
        if (bitmap == null) {
            status.text = "Saved photo is missing."
            return
        }
        photoVersion++
        extractionVersion++
        controller?.unbind()
        photo = bitmap
        focus = null
        seed = null
        mlConfidence = null
        mlRequestVersion = -1
        mlPoint = null
        mlModelState = ""
        currentSavedId = entry.id
        galleryImage.bitmap = bitmap
        galleryImage.profile = entry.profile
        profileView.profile = entry.profile
        val savedMesh = VesselMeshBuilder.build(entry.profile)
        vessel3dView.mesh = savedMesh
        mesh = savedMesh
        saveVesselButton.isEnabled = true
        open3dButton.isEnabled = true
        exportButton.isEnabled = true
        heightInput.setText(entry.heightCm?.toString() ?: "")
        wallInput.setText(entry.wallMm.toString())
        allowanceInput.setText(entry.allowancePercent.toString())
        updateMeasurements()
        showGallery()
        status.text = "Opened ${entry.name}. Tap inside the photo to retrace its outline."
    }

    private fun updateMeasurements() {
        if (!::measurementsText.isInitialized) return
        val profile = profileView.profile
        if (profile == null) {
            measurementsText.text = "Select a photo to see its profile."
            profileView.measurements = null
            return
        }
        val height = heightInput.text.toString().replace(',', '.').toDoubleOrNull()
        val wall = wallInput.text.toString().replace(',', '.').toDoubleOrNull()
        val allowance = allowanceInput.text.toString().replace(',', '.').toDoubleOrNull()
        if (height == null || height <= 0.0) {
            measurementsText.text = String.format(Locale.getDefault(),
                "Height : widest diameter = %.2f : 1. Enter a height in cm for dimensions.",
                profile.height / profile.width)
            profileView.measurements = null
            return
        }
        if (height > 500.0 || wall == null || wall !in 0.5..50.0 ||
            allowance == null || allowance !in 0.0..300.0
        ) {
            measurementsText.text = "Enter a height up to 500 cm, wall 0.5–50 mm, and allowance 0–300%."
            profileView.measurements = null
            return
        }
        val measured = VesselMeasurementCalculator.calculate(profile, height, wall, allowance)
            ?: return
        val scaledMesh = VesselMeshBuilder.build(profile, wallMm = wall, heightCm = height)
        mesh = scaledMesh
        vessel3dView.mesh = scaledMesh
        profileView.measurements = measured
        measurementsText.text = String.format(Locale.getDefault(),
            "Height: %.1f cm\nWidest diameter: %.1f cm at %.1f cm above base\n" +
                "Narrowest wall diameter: %.1f cm at %.1f cm above base\n" +
                "Base curve excluded below about %.1f cm\n" +
                "Rim / foot diameter: %.1f / %.1f cm\n" +
                "Height : widest diameter = %.2f : 1\n" +
                "Clay to centre: about %.0f g (%.2f kg)",
            measured.heightCm, measured.widestDiameterCm, measured.widestFromBaseCm,
            measured.narrowestDiameterCm, measured.narrowestFromBaseCm,
            measured.baseCurveEndCm,
            measured.rimDiameterCm, measured.footDiameterCm,
            measured.heightCm / measured.widestDiameterCm,
            measured.estimatedClayGrams, measured.estimatedClayGrams / 1000.0)
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
