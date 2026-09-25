package com.example.potterywheeldetector

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.WindowInsets
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageProxy
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
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    private enum class Screen { HOME, LIBRARY, CAMERA, VESSEL, SETTINGS }
    private enum class Step { OUTLINE, HANDLE, MEASURE, VIEW_3D }

    // Header
    private lateinit var backButton: ImageButton
    private lateinit var brandGroup: View
    private lateinit var titleText: TextView
    private lateinit var ledGroup: View
    private lateinit var headerAction: Button
    private lateinit var settingsButton: ImageButton
    private lateinit var status: TextView

    // Screens
    private lateinit var panels: Map<Screen, View>
    private lateinit var stepPanels: Map<Step, View>
    private lateinit var stepButtons: Map<Step, Button>

    // Home, library, camera
    private lateinit var library: LibraryScreen
    private lateinit var camera: LiveCameraScreen

    // Vessel steps
    private lateinit var galleryImage: GalleryOutlineView
    private lateinit var mlOutlineSwitch: Switch
    private lateinit var sensitivity: FaderControl
    private lateinit var outlineStatus: TextView
    private lateinit var handleView: HandleView
    private lateinit var noHandleCard: View
    private lateinit var handleControls: View
    private lateinit var handleSideText: TextView
    private lateinit var handleSwitch: Switch
    private lateinit var detectHandleSwitch: Switch
    private lateinit var noHandleTitle: TextView
    private lateinit var noHandleText: TextView
    private lateinit var sectionButtons: Map<HandleSection, Button>
    private lateinit var handleThicknessText: TextView
    private lateinit var handleWidthFader: FaderControl
    private lateinit var handleClayText: TextView
    private lateinit var profileView: ProfileView
    private lateinit var heightInput: EditText
    private lateinit var ratioText: TextView
    private lateinit var specList: LinearLayout
    private lateinit var claySettingsText: TextView
    private lateinit var clayTotalText: TextView
    private lateinit var clayBodyText: TextView
    private lateinit var clayHandleText: TextView
    private lateinit var clayTrimText: TextView
    private lateinit var vessel3dView: Vessel3DView
    private lateinit var viewerUnits: TextView
    private lateinit var exportButton: Button

    private lateinit var settings: SettingsScreen

    private lateinit var vesselStore: VesselStore
    private lateinit var defaultsStore: ClayDefaultsStore
    private val worker = Executors.newSingleThreadExecutor()
    private var interactiveSegmenter: InteractiveSegmenter? = null

    private var screen = Screen.HOME
    private var settingsReturn = Screen.HOME
    private var step = Step.OUTLINE

    // The vessel being worked on.
    private var photo: Bitmap? = null
    private var profile: VesselProfile? = null
    private var mesh: VesselMesh? = null
    private var focus: Rect? = null
    private var seed: PointF? = null
    private var wallMm = ClayDefaults().wallMm
    private var baseMm = ClayDefaults().baseMm
    private var allowancePercent = ClayDefaults().allowancePercent
    private var handleOptions = HandleOptions()
    private var currentSavedId: String? = null
    private var vesselName: String? = null

    private var photoVersion = 0
    private var extractionVersion = 0
    private var mlConfidence: FloatArray? = null
    private var mlModelState = ""
    private var mlRequestVersion = -1
    private var mlPoint: PointF? = null
    /** Set while code, not the user, moves a control, so listeners do not echo. */
    private var syncingControls = false

    private val requestCamera = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) camera.start() else setStatus("Camera permission denied. You can still pick a photo.")
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
            setStatus("3D vessel saved as OBJ.")
        } catch (error: Exception) {
            setStatus("Could not save OBJ: ${error.localizedMessage ?: "unknown error"}")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        // Target SDK 35+ draws edge to edge: keep content clear of the status and
        // navigation bars and of the keyboard while typing a height.
        findViewById<View>(R.id.root).setOnApplyWindowInsetsListener { view, insets ->
            if (Build.VERSION.SDK_INT >= 30) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                WindowInsets.CONSUMED
            } else {
                // Older versions lay out below the system bars already and resize for the keyboard.
                insets
            }
        }
        vesselStore = VesselStore(this)
        defaultsStore = ClayDefaultsStore(this)
        bindViews()
        library = LibraryScreen(this, vesselStore, findViewById(R.id.libraryContent),
            findViewById(R.id.categoryRow), findViewById(R.id.recentRow), findViewById(R.id.recentEmpty),
            onOpen = ::openSavedVessel,
            onOpenCategory = { category ->
                library.showCategory(category)
                show(Screen.LIBRARY)
            },
            onDeleted = { id -> if (currentSavedId == id) currentSavedId = null },
            onStatus = ::setStatus)
        bindHeaderAndHome()
        bindOutlineStep()
        bindHandleStep()
        bindMeasureStep()
        bindSettings()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = goBack()
        })
        show(Screen.HOME)
    }

    private fun bindViews() {
        backButton = findViewById(R.id.backButton)
        brandGroup = findViewById(R.id.brandGroup)
        titleText = findViewById(R.id.titleText)
        ledGroup = findViewById(R.id.ledGroup)
        headerAction = findViewById(R.id.headerAction)
        settingsButton = findViewById(R.id.settingsButton)
        status = findViewById(R.id.status)
        panels = mapOf(
            Screen.HOME to findViewById(R.id.homePanel),
            Screen.LIBRARY to findViewById(R.id.libraryPanel),
            Screen.CAMERA to findViewById(R.id.cameraPanel),
            Screen.VESSEL to findViewById(R.id.vesselPanel),
            Screen.SETTINGS to findViewById(R.id.settingsPanel)
        )
        stepPanels = mapOf(
            Step.OUTLINE to findViewById(R.id.outlinePanel),
            Step.HANDLE to findViewById(R.id.handlePanel),
            Step.MEASURE to findViewById(R.id.measurePanel),
            Step.VIEW_3D to findViewById(R.id.viewerPanel)
        )
        stepButtons = mapOf(
            Step.OUTLINE to findViewById(R.id.stepOutline),
            Step.HANDLE to findViewById(R.id.stepHandle),
            Step.MEASURE to findViewById(R.id.stepMeasure),
            Step.VIEW_3D to findViewById(R.id.step3d)
        )
        galleryImage = findViewById(R.id.galleryImage)
        mlOutlineSwitch = findViewById(R.id.mlOutlineSwitch)
        sensitivity = findViewById(R.id.sensitivity)
        outlineStatus = findViewById(R.id.outlineStatus)
        handleView = findViewById(R.id.handleView)
        noHandleCard = findViewById(R.id.noHandleCard)
        handleControls = findViewById(R.id.handleControls)
        handleSideText = findViewById(R.id.handleSideText)
        handleSwitch = findViewById(R.id.handleSwitch)
        detectHandleSwitch = findViewById(R.id.detectHandleSwitch)
        noHandleTitle = findViewById(R.id.noHandleTitle)
        noHandleText = findViewById(R.id.noHandleText)
        sectionButtons = mapOf(
            HandleSection.ROUND to findViewById(R.id.sectionRound),
            HandleSection.OVAL to findViewById(R.id.sectionOval),
            HandleSection.STRAP to findViewById(R.id.sectionStrap)
        )
        handleThicknessText = findViewById(R.id.handleThicknessText)
        handleWidthFader = findViewById(R.id.handleWidthFader)
        handleClayText = findViewById(R.id.handleClayText)
        profileView = findViewById(R.id.profileView)
        heightInput = findViewById(R.id.heightInput)
        ratioText = findViewById(R.id.ratioText)
        specList = findViewById(R.id.specList)
        claySettingsText = findViewById(R.id.claySettingsText)
        clayTotalText = findViewById(R.id.clayTotalText)
        clayBodyText = findViewById(R.id.clayBodyText)
        clayHandleText = findViewById(R.id.clayHandleText)
        clayTrimText = findViewById(R.id.clayTrimText)
        vessel3dView = findViewById(R.id.vessel3dView)
        viewerUnits = findViewById(R.id.viewerUnits)
        exportButton = findViewById(R.id.exportButton)
    }

    private fun bindHeaderAndHome() {
        backButton.setOnClickListener { goBack() }
        settingsButton.setOnClickListener { openSettings(fromVessel = screen == Screen.VESSEL) }
        headerAction.setOnClickListener {
            when (screen) {
                Screen.LIBRARY -> library.createFolderDialog()
                Screen.VESSEL -> saveVesselDialog()
                else -> Unit
            }
        }
        findViewById<View>(R.id.myVesselsButton).setOnClickListener { show(Screen.LIBRARY) }
        findViewById<View>(R.id.allVesselsLink).setOnClickListener { show(Screen.LIBRARY) }
        findViewById<View>(R.id.liveButton).setOnClickListener { showCamera() }
        findViewById<View>(R.id.pickButton).setOnClickListener {
            pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        stepButtons.forEach { (target, button) -> button.setOnClickListener { showStep(target) } }
        exportButton.setOnClickListener { saveObj.launch("pottery-vessel.obj") }
        camera = LiveCameraScreen(this, findViewById(R.id.preview), findViewById(R.id.overlay),
            findViewById(R.id.shutterButton), isVisible = { screen == Screen.CAMERA },
            onStatus = ::setStatus, onCaptured = ::tracePicture)
    }

    private fun bindOutlineStep() {
        galleryImage.onVesselTap = { point ->
            seed = point
            galleryImage.seed = point
            if (mlOutlineSwitch.isChecked) photo?.let { requestMlOutline(it, photoVersion) }
            runExtraction()
        }
        sensitivity.onRelease = { runExtraction() }
        mlOutlineSwitch.setOnCheckedChangeListener { _, checked ->
            if (checked) photo?.let { requestMlOutline(it, photoVersion) }
            runExtraction()
        }
        findViewById<View>(R.id.outlineNext).setOnClickListener { showStep(Step.HANDLE) }
    }

    private fun bindHandleStep() {
        listOf(handleSwitch, detectHandleSwitch).forEach { toggle ->
            toggle.setOnCheckedChangeListener { _, checked ->
                if (!syncingControls) setHandleDetection(checked)
            }
        }
        sectionButtons.forEach { (section, button) ->
            button.setOnClickListener { updateHandleOptions(handleOptions.copy(section = section)) }
        }
        handleWidthFader.onValueChange = { value, fromUser ->
            if (fromUser && !syncingControls) updateHandleOptions(handleOptions.copy(widthMm = value))
        }
    }

    private fun bindMeasureStep() {
        heightInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) { updateMeasurements() }
        })
        findViewById<View>(R.id.claySettingsStrip).setOnClickListener { openSettings(fromVessel = true) }
    }

    private fun bindSettings() {
        settings = SettingsScreen(findViewById(R.id.settingsPanel), defaultsStore) { values ->
            wallMm = values.wallMm
            baseMm = values.baseMm
            allowancePercent = values.allowancePercent
            updateMeasurements()
        }
    }

    // Navigation

    private fun show(target: Screen) {
        if (target != Screen.CAMERA) camera.stop()
        screen = target
        panels.forEach { (key, panel) -> panel.visibility = if (key == target) View.VISIBLE else View.GONE }
        val home = target == Screen.HOME
        brandGroup.visibility = if (home) View.VISIBLE else View.GONE
        ledGroup.visibility = if (home) View.VISIBLE else View.GONE
        backButton.visibility = if (home) View.GONE else View.VISIBLE
        titleText.visibility = if (home) View.GONE else View.VISIBLE
        settingsButton.visibility = if (target == Screen.HOME || target == Screen.VESSEL)
            View.VISIBLE else View.GONE
        titleText.text = when (target) {
            Screen.LIBRARY -> "vessels"
            Screen.CAMERA -> "live camera"
            Screen.VESSEL -> vesselName ?: "untitled vessel"
            Screen.SETTINGS -> "settings"
            Screen.HOME -> ""
        }
        headerAction.visibility = if (target == Screen.LIBRARY || target == Screen.VESSEL)
            View.VISIBLE else View.GONE
        headerAction.text = if (target == Screen.LIBRARY) "+ folder" else "save"
        headerAction.isEnabled = target != Screen.VESSEL || profile != null
        when (target) {
            Screen.HOME -> {
                library.renderCategories()
                library.renderRecent()
            }
            Screen.LIBRARY -> {
                library.render()
                setStatus("Choose a saved vessel or create a folder.")
            }
            Screen.VESSEL -> showStep(step)
            else -> Unit
        }
        updateStatusVisibility()
    }

    private fun showStep(target: Step) {
        step = target
        stepPanels.forEach { (key, panel) -> panel.visibility = if (key == target) View.VISIBLE else View.GONE }
        stepButtons.forEach { (key, button) -> button.isSelected = key == target }
        if (target == Step.HANDLE) renderHandleStep()
        updateStatusVisibility()
    }

    private fun goBack() {
        when (screen) {
            Screen.HOME -> finish()
            Screen.SETTINGS -> show(settingsReturn)
            else -> show(Screen.HOME)
        }
    }

    private fun setStatus(text: String) {
        status.text = text
        outlineStatus.text = text
    }

    /** The outline step shows status inside its panel; other working screens use the bar. */
    private fun updateStatusVisibility() {
        val inPanel = screen == Screen.VESSEL && step == Step.OUTLINE
        status.visibility = when {
            screen == Screen.HOME || screen == Screen.SETTINGS || inPanel -> View.GONE
            else -> View.VISIBLE
        }
    }

    // Live camera

    private fun showCamera() {
        show(Screen.CAMERA)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestCamera.launch(Manifest.permission.CAMERA)
            return
        }
        camera.start()
    }

    // Photo to profile

    private fun loadPhoto(uri: Uri) {
        startPhoto("Opening photo…") { PhotoInput.fromUri(contentResolver, uri) }
    }

    /** A still from the live camera, traced like a picked photo. */
    private fun tracePicture(image: ImageProxy) {
        if (isDestroyed || worker.isShutdown) {
            image.close()
            return
        }
        startPhoto("Reading picture…") { PhotoInput.fromCapture(image) }
    }

    /** Resets to a new vessel and traces the bitmap [decode] produces on the worker. */
    private fun startPhoto(message: String, decode: () -> Bitmap) {
        photoVersion++
        extractionVersion++
        val version = photoVersion
        resetVessel(defaultsStore.load())
        step = Step.OUTLINE
        show(Screen.VESSEL)
        setStatus(message)
        worker.execute {
            try {
                val bitmap = decode()
                runOnUiThread {
                    if (version != photoVersion || isDestroyed) return@runOnUiThread
                    photo = bitmap
                    galleryImage.bitmap = bitmap
                    setStatus("Finding the vessel…")
                    detectPhotoObject(bitmap, version)
                }
            } catch (error: Exception) {
                runOnUiThread {
                    if (version == photoVersion) setStatus(
                        "Could not open photo: ${error.localizedMessage ?: "unknown error"}")
                }
            }
        }
    }

    /** Clears the working vessel and starts from the given clay assumptions. */
    private fun resetVessel(defaults: ClayDefaults) {
        currentSavedId = null
        vesselName = null
        photo = null
        profile = null
        mesh = null
        focus = null
        seed = null
        mlConfidence = null
        mlModelState = ""
        mlRequestVersion = -1
        mlPoint = null
        wallMm = defaults.wallMm
        baseMm = defaults.baseMm
        allowancePercent = defaults.allowancePercent
        // Off until asked for: detection has nothing to find on bowls and vases.
        handleOptions = HandleOptions(enabled = false)
        galleryImage.bitmap = null
        galleryImage.seed = null
        heightInput.setText("")
        applyProfile(null)
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
                // ML Kit does not know about this activity; it may answer after onDestroy.
                if (version == photoVersion && !isDestroyed) {
                    focus = objects.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
                        ?.boundingBox
                    runExtraction()
                    if (mlOutlineSwitch.isChecked) requestMlOutline(bitmap, version)
                }
            }
            .addOnFailureListener {
                if (version == photoVersion && !isDestroyed) {
                    setStatus("ML Kit could not locate an object; trying the photo outline.")
                    runExtraction()
                    if (mlOutlineSwitch.isChecked) requestMlOutline(bitmap, version)
                }
            }
            .addOnCompleteListener { imageDetector.close() }
    }

    private fun requestMlOutline(bitmap: Bitmap, version: Int) {
        if (version != photoVersion || isDestroyed || worker.isShutdown) return
        val point = seed ?: focus?.let {
            PointF(it.centerX().toFloat(), it.centerY().toFloat())
        } ?: PointF(bitmap.width / 2f, bitmap.height / 2f)
        if (mlRequestVersion == version && mlPoint == point) return
        mlRequestVersion = version
        mlPoint = PointF(point.x, point.y)
        mlConfidence = null
        mlModelState = "Finding vessel edge with ML…"
        setStatus(mlModelState)
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
        if (isDestroyed || worker.isShutdown) return
        val image = photo ?: return
        val photoId = photoVersion
        val runId = ++extractionVersion
        val target = focus?.let(::Rect)
        val selectedPoint = seed?.let { PointF(it.x, it.y) }
        val level = sensitivity.value.toInt()
        val confidence = if (mlOutlineSwitch.isChecked) mlConfidence else null
        val findHandle = handleOptions.enabled
        setStatus("Tracing outline…")
        worker.execute {
            val mlProfile = confidence?.let {
                VesselProfileExtractor.extractFromConfidence(image.width, image.height,
                    it, target, selectedPoint, level, findHandle)
            }
            val traced = mlProfile ?: VesselProfileExtractor.extract(
                image, target, selectedPoint, level, findHandle)
            runOnUiThread {
                if (photoId != photoVersion || runId != extractionVersion || isDestroyed)
                    return@runOnUiThread
                applyProfile(traced)
                val handleNote = traced?.handle?.let { " Handle on the ${it.side.name.lowercase()}." } ?: ""
                setStatus(when {
                    traced == null ->
                        "No clear outline found. Try a plain background, tap the vessel, or adjust sensitivity."
                    mlProfile != null -> "ML outline found.$handleNote"
                    mlOutlineSwitch.isChecked && mlModelState.isNotEmpty() -> mlModelState
                    else -> "Colour outline found.$handleNote"
                })
            }
        }
    }

    private fun applyProfile(traced: VesselProfile?) {
        profile = traced
        galleryImage.profile = traced
        profileView.profile = traced
        handleView.profile = traced
        val ready = traced != null
        exportButton.isEnabled = ready
        if (screen == Screen.VESSEL) headerAction.isEnabled = ready
        renderHandleStep()
        updateMeasurements()
    }

    // Handle step

    private fun updateHandleOptions(options: HandleOptions) {
        handleOptions = options.copy(widthMm = options.widthMm.coerceIn(HandleOptions.WIDTH_RANGE))
        renderHandleStep()
        updateMeasurements()
    }

    /** Detection changes the traced outline itself, so switching it retraces the photo. */
    private fun setHandleDetection(on: Boolean) {
        handleOptions = handleOptions.copy(enabled = on)
        renderHandleStep()
        val image = photo ?: return
        if (mlOutlineSwitch.isChecked) requestMlOutline(image, photoVersion)
        runExtraction()
    }

    private fun renderHandleStep() {
        val handle = profile?.handle
        val on = handleOptions.enabled
        syncingControls = true
        handleSwitch.isChecked = on
        detectHandleSwitch.isChecked = on
        handleWidthFader.value = handleOptions.widthMm
        syncingControls = false
        noHandleCard.visibility = if (handle == null) View.VISIBLE else View.GONE
        handleControls.visibility = if (handle == null) View.GONE else View.VISIBLE
        handleView.handleEnabled = on
        galleryImage.showHandle = on
        profileView.showHandle = on
        if (handle == null) {
            handleSideText.text = if (on) "on · none found yet" else "off"
            noHandleTitle.text = if (on) "no handle found" else "handle detection is off"
            noHandleText.text = if (on)
                "the body is modelled on its own. photograph the vessel side-on so the handle " +
                    "sticks out to the left or right, then tap it in 01 outline."
            else "switch it on for mugs and cups. bowls, vases and jars are modelled from the body alone."
            return
        }
        handleSideText.text = "traced on the ${handle.side.name.lowercase()} · " +
            if (handle.hasEye) "open handle" else "solid lug"
        sectionButtons.forEach { (section, button) ->
            button.isSelected = section == handleOptions.section
            button.setTextColor(getColor(if (button.isSelected) R.color.key else R.color.graphite_soft))
            button.compoundDrawableTintList = button.textColors
        }
        handleWidthFader.isEnabled = handleOptions.section != HandleSection.ROUND
        handleWidthFader.setLabel(if (handleOptions.section == HandleSection.ROUND)
            "width · same as thickness for round" else "width front to back · assumed")
    }

    // Measurements

    private fun enteredHeight(): Double? =
        heightInput.text.toString().replace(',', '.').toDoubleOrNull()?.takeIf { it > 0.0 && it <= 500.0 }

    private fun updateMeasurements() {
        val shape = profile
        claySettingsText.text = VesselReadout.settingsSummary(wallMm, baseMm, allowancePercent)
        if (shape == null) {
            showMeasurements(null)
            ratioText.text = ""
            vessel3dView.mesh = null
            mesh = null
            return
        }
        val height = enteredHeight()
        val measured = height?.let {
            VesselMeasurementCalculator.calculate(shape, it, wallMm, allowancePercent, baseMm, handleOptions)
        }
        ratioText.text = if (height == null) String.format(Locale.getDefault(),
            "h : w = %.2f\nenter a height for sizes", shape.height / shape.width) else ""
        val newMesh = VesselMeshBuilder.build(shape, wallMm = wallMm,
            heightCm = measured?.heightCm, baseMm = baseMm, handle = handleOptions)
        mesh = newMesh
        vessel3dView.mesh = newMesh
        viewerUnits.text = if (measured == null) "relative units" else "calibrated · ${measured.heightCm} cm"
        showMeasurements(measured)
    }

    private fun showMeasurements(measured: VesselMeasurements?) {
        profileView.measurements = measured
        specList.removeAllViews()
        measured?.let { values ->
            VesselReadout.specRows(values).forEach { row ->
                specList.addView(View(this).apply { setBackgroundColor(getColor(R.color.rule)) },
                    LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1))
                specList.addView(specRow(row))
            }
        }
        val handleOn = profile?.handle != null && handleOptions.enabled
        if (measured == null) {
            clayTotalText.text = "—"
            clayBodyText.text = "body\n—"
            clayHandleText.text = "handle\n—"
            clayTrimText.text = "trim\n—"
            handleThicknessText.text = "enter height in 03"
            handleClayText.text = "—"
            return
        }
        val split = VesselReadout.claySplit(measured, allowancePercent)
        clayTotalText.text = VesselReadout.grams(split.totalGrams)
        clayBodyText.text = "body\n${VesselReadout.grams(split.bodyGrams)}"
        clayHandleText.text = "handle\n${if (handleOn) VesselReadout.grams(split.handleGrams) else "none"}"
        clayTrimText.text = "trim +${allowancePercent.toInt()}%\n${VesselReadout.grams(split.allowanceGrams)}"
        handleThicknessText.text = measured.handleThicknessCm?.let {
            "${VesselReadout.millimetres(Math.round(it * 100) / 10.0)} mm"
        } ?: "—"
        handleClayText.text = if (handleOn) "+ ${VesselReadout.grams(split.handleGrams)}" else "off"
    }

    private fun specRow(row: VesselReadout.SpecRow) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        val density = resources.displayMetrics.density
        setPadding(0, (9 * density).toInt(), 0, (9 * density).toInt())
        contentDescription = "${row.letter}, ${row.label}: ${row.value}"
        addView(TextView(this@MainActivity).apply {
            text = row.letter
            typeface = Typeface.MONOSPACE
            textSize = 11f
            setTextColor(getColor(R.color.label))
        }, LinearLayout.LayoutParams((24 * density).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT))
        addView(TextView(this@MainActivity).apply {
            text = row.label
            textSize = 13f
            setTextColor(getColor(R.color.graphite))
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        addView(TextView(this@MainActivity).apply {
            text = row.value
            typeface = Typeface.MONOSPACE
            textSize = 13f
            setTextColor(getColor(R.color.graphite))
        })
    }

    // Settings

    private fun openSettings(fromVessel: Boolean) {
        settingsReturn = if (screen == Screen.SETTINGS) settingsReturn else screen
        settings.open(profile?.let { ClayDefaults(wallMm, baseMm, allowancePercent) }, fromVessel)
        show(Screen.SETTINGS)
    }

    // Library

    private fun saveVesselDialog() {
        val bitmap = photo ?: return
        val traced = profile ?: return
        val saved = vesselStore.find(currentSavedId)
        SaveVesselDialog(this, vesselStore.folders(), saved, vesselName,
            VesselCategory.suggest(traced, handleOptions.enabled),
            numberedName = { category ->
                category.defaultName(vesselStore.recent(Int.MAX_VALUE).count { it.category == category })
            },
            onDelete = saved?.let { entry ->
                {
                    library.confirmDelete(entry) {
                        resetVessel(defaultsStore.load())
                        show(Screen.HOME)
                    }
                }
            }
        ) { name, category, folder ->
            try {
                val entry = vesselStore.save(currentSavedId, folder.id, name, bitmap, traced,
                    enteredHeight(), wallMm, allowancePercent, baseMm, handleOptions, category)
                currentSavedId = entry.id
                vesselName = entry.name
                titleText.text = entry.name
                setStatus("Saved ${entry.name} as a ${category.label} in ${folder.name}.")
            } catch (error: Exception) {
                setStatus("Could not save vessel: ${error.localizedMessage ?: "unknown error"}")
            }
        }.show()
    }

    private fun openSavedVessel(entry: SavedVessel) {
        val bitmap = vesselStore.loadImage(entry.id)
        if (bitmap == null) {
            setStatus("Saved photo is missing.")
            return
        }
        photoVersion++
        extractionVersion++
        resetVessel(ClayDefaults(entry.wallMm, entry.baseMm, entry.allowancePercent))
        currentSavedId = entry.id
        vesselName = entry.name
        handleOptions = entry.handleOptions
        photo = bitmap
        galleryImage.bitmap = bitmap
        heightInput.setText(entry.heightCm?.toString() ?: "")
        applyProfile(entry.profile)
        step = Step.MEASURE
        show(Screen.VESSEL)
        setStatus("Opened ${entry.name}. Tap inside the photo in 01 outline to retrace it.")
    }

    override fun onDestroy() {
        camera.release()
        worker.shutdownNow()
        interactiveSegmenter?.close()
        super.onDestroy()
    }
}
