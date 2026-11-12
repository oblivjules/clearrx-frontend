package com.app.clearx

import android.Manifest
import android.animation.*
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.util.Size
import android.view.MotionEvent
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.extensions.ExtensionMode
import androidx.camera.extensions.ExtensionsManager
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.lifecycle.lifecycleScope
import com.app.clearx.network.ProcessImageResponse
import com.app.clearx.network.PreviewCheckResponse
import com.app.clearx.network.RetrofitClient
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import android.graphics.Bitmap
import android.graphics.BitmapRegionDecoder
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.RectF
import android.view.Surface
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    // Views
    private lateinit var previewView: PreviewView
    private lateinit var framingOverlay: FramingOverlayView
    private lateinit var laserScanner: LaserScannerView
    private lateinit var guidanceText: TextView
    private lateinit var captureButton: ImageButton
    private lateinit var selectFileButton: UploadLoaderView
    private lateinit var flashlightButton: ImageButton
    private lateinit var focusRipple: View
    private lateinit var cameraContainer: FrameLayout
    private lateinit var processingOverlay: LinearLayout
    private lateinit var processingStatusText: TextView
    private lateinit var terminalOverlay: FrameLayout
    private lateinit var terminalStatusText: TextView
    private lateinit var terminalProgressText: TextView
    private lateinit var terminalCursor: TextView
    private lateinit var terminalController: TrOCRTerminalController
    private lateinit var resultScreen: CardView
    private lateinit var enhancedImageView: ImageView
    private lateinit var originalImageView: com.google.android.material.imageview.ShapeableImageView
    private lateinit var qualityBadge: TextView
    private lateinit var qualityScoreText: TextView
    private lateinit var retakeButton: com.google.android.material.button.MaterialButton
    private lateinit var saveButton: com.google.android.material.button.MaterialButton

    // Prescription result views
    private lateinit var prescriptionCard: View
    private lateinit var medicinesRecyclerView: RecyclerView
    private lateinit var rxConfidenceBadge: TextView
    private lateinit var rawOcrSection: LinearLayout
    private lateinit var rawOcrText: TextView
    private lateinit var medicineAdapter: MedicineAdapter

    // Camera
    private var imageCapture: ImageCapture? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var cameraControl: CameraControl? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var activeCameraSelector: CameraSelector? = null
    private lateinit var cameraExecutor: ExecutorService

    // Gatekeeper
    private var gatekeeper: GatekeeperAnalyzer? = null

    @Volatile private var gatekeeperResult: GatekeeperResult? = null

    // Flashlight
    private var isFlashOn = false

    // State
    private var validationJob: Job? = null
    private var focusRetryJob: Job? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var cameraSuppressed = false
    private var cameraStartInProgress = false
    private var isActivityResumed = false
    private var permissionRequestInFlight = false
    private var isCapturing = false
    private var isUsable = false
    private var autoCaptureArmed = false
    private var goodStateStartMs: Long = 0L
    private var networkErrorCount = 0
    private var captureUserId: String? = null
    private var captureSessionId: String? = null
    private var captureAttemptNumber = 0
    private var sessionCreationJob: Deferred<String?>? = null

    // Pro features
    private lateinit var haptics: HapticFeedbackManager
    private var lastHapticState = FramingOverlayView.FramingState.IDLE

    private var consecutiveBadFrames = 0
    private val BAD_FRAME_DEBOUNCE = 3
    private var lastFramingState = FramingOverlayView.FramingState.IDLE

    @Volatile private var aeConverged = false
    @Volatile private var afConverged = false

    companion object {
        private const val TAG = "CleaRx"
        private const val BASE_URL = "http://192.168.1.3:8000/"
        private const val TEST_USER_ID = "b3affcc6-4ad0-4c25-bf07-b1fd325de935"
        private const val VALIDATION_INTERVAL_MS = 700L
        private const val AUTO_CAPTURE_HOLD_MS = 1500L
        private const val OFFLINE_THRESHOLD = 4
        private const val FOCUS_RETRY_MS = 4000L
        private const val FOCUS_RIPPLE_MS = 500L

        private val PREFERRED_EXTENSIONS = intArrayOf(
            ExtensionMode.HDR,
            ExtensionMode.NIGHT,
        )
    }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            permissionRequestInFlight = false
            if (granted) startCamera() else showPermissionDeniedDialog()
        }

    private val filePickerLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) handleSelectedFile(uri)
            else {
                cameraSuppressed = false
                resumeLiveCameraIfNeeded()
            }
        }

    private val cropLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                result.data?.getStringExtra(CropActivity.EXTRA_CROPPED)
                    ?.let { startCroppedProcessing(File(it)) }
            } else if (result.data?.getBooleanExtra(CropActivity.EXTRA_UPLOAD_AGAIN, false) == true) {
                chooseImage()
            } else {
                cameraSuppressed = false
                resumeLiveCameraIfNeeded()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "CleaRx build version=${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        setContentView(R.layout.activity_main)
        captureUserId = intent.getStringExtra("user_id") ?: TEST_USER_ID
        captureSessionId = intent.getStringExtra("session_id")
        if (captureSessionId == null && captureUserId != null) {
            sessionCreationJob = lifecycleScope.async(Dispatchers.IO) {
                try {
                    val response = RetrofitClient.getProcessingService(BASE_URL)
                        .createCaptureSession(
                            MultipartBody.Part.createFormData("user_id", captureUserId!!),
                        )
                    val body = response.body()
                    if (response.isSuccessful && body?.status == "created") body.session_id else null
                } catch (exception: Exception) {
                    Log.w(TAG, "Capture session creation failed", exception)
                    null
                }
            }
        }

        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        or View.SYSTEM_UI_FLAG_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                )

        bindViews()
        terminalController = TrOCRTerminalController(
            scope        = lifecycleScope,
            lifecycle    = lifecycle,
            overlay      = terminalOverlay,
            statusText   = terminalStatusText,
            progressText = terminalProgressText,
            cursorText   = terminalCursor
        )
        setupListeners()
        selectFileButton.setOnClickListener { chooseImage() }
        findViewById<View>(R.id.processingPrivacyNotice).setOnClickListener { showProcessingPrivacy() }
        cameraExecutor = Executors.newSingleThreadExecutor()
        haptics = HapticFeedbackManager(this)
        // Camera permission/startup runs when the activity resumes.
    }

    override fun onResume() {
        super.onResume()
        isActivityResumed = true
        resumeLiveCameraIfNeeded()
    }

    override fun onPause() {
        isActivityResumed = false
        releaseCamera()
        super.onPause()
    }

    override fun onDestroy() {
        isActivityResumed = false
        releaseCamera()
        captureSessionId?.let { sessionId ->
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    RetrofitClient.getProcessingService(BASE_URL).closeCaptureSession(
                        MultipartBody.Part.createFormData("session_id", sessionId),
                        MultipartBody.Part.createFormData("session_status", "abandoned"),
                    )
                } catch (exception: Exception) {
                    Log.w(TAG, "Capture session close failed", exception)
                }
            }
        }
        super.onDestroy()
        cameraControl?.enableTorch(false)
        validationJob?.cancel()
        focusRetryJob?.cancel()
        if (::terminalController.isInitialized) terminalController.cancel()
        cameraExecutor.shutdown()
        haptics.release()
        if (::laserScanner.isInitialized) laserScanner.stopScanning()
    }

    private fun bindViews() {
        previewView        = findViewById(R.id.previewView)
        framingOverlay     = findViewById(R.id.framingOverlay)
        guidanceText       = findViewById(R.id.guidanceText)
        captureButton      = findViewById(R.id.captureButton)
        selectFileButton   = findViewById(R.id.selectFileButton)
        flashlightButton   = findViewById(R.id.flashlightButton)
        focusRipple        = findViewById(R.id.focusRipple)
        cameraContainer    = findViewById(R.id.cameraContainer)
        processingOverlay    = findViewById(R.id.processingOverlay)
        processingStatusText = findViewById(R.id.processingStatusText)
        terminalOverlay      = findViewById(R.id.terminalOverlay)
        terminalStatusText   = findViewById(R.id.terminalStatusText)
        terminalProgressText = findViewById(R.id.terminalProgressText)
        terminalCursor       = findViewById(R.id.terminalCursor)
        resultScreen         = findViewById(R.id.resultScreen)
        enhancedImageView  = findViewById(R.id.enhancedImageView)
        originalImageView  = findViewById(R.id.originalImageView)
        qualityBadge       = findViewById(R.id.qualityBadge)
        qualityScoreText   = findViewById(R.id.qualityScoreText)
        retakeButton       = findViewById(R.id.retakeButton)
        saveButton         = findViewById(R.id.saveButton)

        // Bind prescription result views
        prescriptionCard      = findViewById(R.id.prescriptionCard)
        medicinesRecyclerView = findViewById(R.id.medicinesRecyclerView)
        rxConfidenceBadge     = findViewById(R.id.rxConfidenceBadge)
        rawOcrSection         = findViewById(R.id.rawOcrSection)
        rawOcrText            = findViewById(R.id.rawOcrText)

        medicineAdapter = MedicineAdapter()
        medicinesRecyclerView.layoutManager = LinearLayoutManager(this)
        medicinesRecyclerView.adapter = medicineAdapter

        laserScanner = LaserScannerView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            cameraContainer.addView(this)
        }

        // Connect frame changes to laser scanner
        framingOverlay.setOnFrameChangedListener { frameRect ->
            laserScanner.updateFrameBounds(frameRect)
            if (lastFramingState == FramingOverlayView.FramingState.GOOD ||
                lastFramingState == FramingOverlayView.FramingState.PARTIAL) {
                if (!laserScanner.isScanningActive()) laserScanner.startScanning()
            }
        }
    }

    private var frameResizeHintShown = false

    @SuppressLint("ClickableViewAccessibility")
    private fun setupListeners() {
        flashlightButton.setOnClickListener {
            isFlashOn = !isFlashOn
            cameraControl?.enableTorch(isFlashOn)
            flashlightButton.alpha = if (isFlashOn) 1.0f else 0.45f
            flashlightButton.setColorFilter(
                if (isFlashOn) android.graphics.Color.parseColor("#FF5252")
                else android.graphics.Color.WHITE
            )
        }

        captureButton.setOnClickListener {
            haptics.triggerHaptic(HapticFeedbackManager.HapticType.IMPACT)
            val canCapture = lastFramingState == FramingOverlayView.FramingState.GOOD ||
                    lastFramingState == FramingOverlayView.FramingState.PARTIAL
            if (canCapture && !isCapturing) captureAndProcess()
        }

        retakeButton.setOnClickListener { resetToCamera() }

        saveButton.setOnClickListener {
            Toast.makeText(this, "Image saved!", Toast.LENGTH_SHORT).show()
            resetToCamera()
        }

        previewView.setOnTouchListener { view, event ->
            if (event.action == MotionEvent.ACTION_UP && !isCapturing) {
                triggerFocusAt(event.x, event.y)
                showFocusRipple(event.x, event.y)
                view.performClick()
            }
            true
        }

        framingOverlay.setOnTouchListener { view, event ->
            if (!frameResizeHintShown && event.actionMasked == MotionEvent.ACTION_DOWN) {
                frameResizeHintShown = true
                showGuidance(UIStrings.FRAME_RESIZE_HINT)
                mainHandler.postDelayed({
                    showGuidance(UIStrings.ALIGN_INSTRUCTION)
                }, 2500)
            }
            view.onTouchEvent(event)
        }
    }

    private fun checkCameraPermission() {
        if (!isLiveCameraPage() || permissionRequestInFlight) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            permissionRequestInFlight = true
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    private fun isLiveCameraPage(): Boolean =
        !isFinishing && !isDestroyed && isActivityResumed && !cameraSuppressed &&
            ::cameraContainer.isInitialized && cameraContainer.visibility == View.VISIBLE &&
            ::resultScreen.isInitialized && resultScreen.visibility != View.VISIBLE && !isCapturing

    private fun resumeLiveCameraIfNeeded() {
        if (isLiveCameraPage()) checkCameraPermission()
    }

    private fun releaseCamera() {
        validationJob?.cancel()
        validationJob = null
        focusRetryJob?.cancel()
        focusRetryJob = null
        stopGatekeeperAnalysis()
        cameraControl?.enableTorch(false)
        cameraProvider?.unbindAll()
        cameraControl = null
        imageCapture = null
        imageAnalysis = null
        cameraStartInProgress = false
        if (::laserScanner.isInitialized) laserScanner.stopScanning()
    }

    private fun startCamera() {
        if (!isLiveCameraPage() || cameraStartInProgress) return
        if (!hasCameraPermission()) {
            showPermissionDeniedDialog()
            return
        }
        cameraStartInProgress = true
        aeConverged = false
        afConverged = false
        gatekeeperResult = null

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            if (!isLiveCameraPage()) {
                cameraStartInProgress = false
                return@addListener
            }
            val provider = cameraProviderFuture.get()
            cameraProvider = provider

            val cachedSelector = activeCameraSelector
            if (cachedSelector != null) {
                bindCameraUseCases(provider, cachedSelector)
                return@addListener
            }

            val extensionsManagerFuture =
                ExtensionsManager.getInstanceAsync(this, provider)

            extensionsManagerFuture.addListener({
                if (!isLiveCameraPage()) {
                    cameraStartInProgress = false
                    return@addListener
                }
                val extensionsManager = extensionsManagerFuture.get()
                val baseCameraSelector  = CameraSelector.DEFAULT_BACK_CAMERA

                val (selector, activeExtension) =
                    selectBestCameraSelector(extensionsManager, baseCameraSelector)

                Log.i(TAG, "Camera extension selected: " +
                        if (activeExtension == ExtensionMode.NONE) "NONE (standard)"
                        else extensionModeName(activeExtension))

                activeCameraSelector = selector
                bindCameraUseCases(provider, selector)

            }, ContextCompat.getMainExecutor(this))

        }, ContextCompat.getMainExecutor(this))
    }

    private fun selectBestCameraSelector(
        extensionsManager: ExtensionsManager,
        baseCameraSelector: CameraSelector,
    ): Pair<CameraSelector, Int> {
        for (mode in PREFERRED_EXTENSIONS) {
            if (extensionsManager.isExtensionAvailable(baseCameraSelector, mode)) {
                val extSelector = extensionsManager
                    .getExtensionEnabledCameraSelector(baseCameraSelector, mode)
                return extSelector to mode
            }
        }
        return baseCameraSelector to ExtensionMode.NONE
    }

    private fun bindCameraUseCases(
        provider: ProcessCameraProvider,
        cameraSelector: CameraSelector,
    ) {
        val preview = Preview.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .build()
            .also { it.setSurfaceProvider(previewView.surfaceProvider) }

        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setFlashMode(ImageCapture.FLASH_MODE_OFF)
            .setJpegQuality(100)
            .setTargetRotation(previewView.display?.rotation ?: Surface.ROTATION_0)
            .build()

        stopGatekeeperAnalysis()
        GatekeeperAnalyzer.initOpenCV()
        gatekeeper = GatekeeperAnalyzer(::onGatekeeperResult)

        imageAnalysis = ImageAnalysis.Builder()
            .setTargetResolution(Size(640, 480))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
            .build()
            .also { it.setAnalyzer(cameraExecutor, gatekeeper!!) }

        try {
            provider.unbindAll()
            val camera = provider.bindToLifecycle(
                this,
                cameraSelector,
                preview,
                imageCapture,
                imageAnalysis,
            )
            cameraControl = camera.cameraControl
            if (isFlashOn) cameraControl?.enableTorch(true)

            mainHandler.postDelayed({
                if (previewView.width > 0 && previewView.height > 0) {
                    triggerFocusAt(previewView.width / 2f, previewView.height / 2f)
                }
            }, 500)

            camera.cameraInfo.let { observeCameraState(it) }
            startValidationPolling()
            startFocusRetryLoop()
            cameraStartInProgress = false

        } catch (e: Exception) {
            cameraStartInProgress = false
            Log.e(TAG, "Camera bind failed", e)
            runOnUiThread { showGuidance(UIStrings.ERROR_CAMERA) }
        }
    }

    private fun extensionModeName(mode: Int) = when (mode) {
        ExtensionMode.HDR   -> "HDR"
        ExtensionMode.NIGHT -> "NIGHT"
        ExtensionMode.BOKEH -> "BOKEH"
        ExtensionMode.FACE_RETOUCH -> "FACE_RETOUCH"
        ExtensionMode.AUTO  -> "AUTO"
        else                -> "UNKNOWN($mode)"
    }

    private fun onGatekeeperResult(result: GatekeeperResult) {
        if (!isLiveCameraPage() || !hasCameraPermission()) return
        gatekeeperResult = result

        if (isCapturing) return

        if (!result.passesGate) {
            setCaptureButtonEnabled(false)
            val msg = result.activeErrors.joinToString("\n")
                .ifBlank { UIStrings.HOLD_STEADY }
            showGuidance(msg)

            if (lastFramingState != FramingOverlayView.FramingState.CONNECTING) {
                framingOverlay.setState(FramingOverlayView.FramingState.BAD)
                lastFramingState = FramingOverlayView.FramingState.BAD
                laserScanner.stopScanning()
            }

            Log.d(TAG, "Gate FAIL — blur=${result.laplacianVariance.toInt()} " +
                    "edges=${String.format("%.3f", result.edgeDensity)} " +
                    "cnr=${String.format("%.2f", result.cnr)} " +
                    "light=${result.lightingOk}")
        }
    }

    private fun stopGatekeeperAnalysis() {
        imageAnalysis?.clearAnalyzer()
        val stoppedAnalyzer = gatekeeper
        stoppedAnalyzer?.stop()
        gatekeeper = null
        imageAnalysis = null
        if (stoppedAnalyzer != null && ::cameraExecutor.isInitialized) {
            // Native Mats must not be freed on the UI thread while analysis uses them.
            cameraExecutor.execute { stoppedAnalyzer.release() }
        }
    }

    private fun triggerFocusAt(x: Float, y: Float) {
        if (previewView.width == 0 || previewView.height == 0) return

        val factory = previewView.meteringPointFactory
        val point   = factory.createPoint(x, y)

        val action = FocusMeteringAction.Builder(
            point,
            FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE,
        )
            .setAutoCancelDuration(3, TimeUnit.SECONDS)
            .build()

        val resultFuture = cameraControl?.startFocusAndMetering(action) ?: return

        resultFuture.addListener({
            try {
                val focusResult = resultFuture.get()
                if (focusResult.isFocusSuccessful) {
                    afConverged = true
                    Log.d(TAG, "AF/AE converged ✓ at (${x.toInt()}, ${y.toInt()})")
                } else {
                    afConverged = false
                    Log.d(TAG, "AF/AE did not converge at (${x.toInt()}, ${y.toInt()})")
                }
            } catch (e: Exception) {
                Log.d(TAG, "AF result exception (likely auto-cancel): ${e.message}")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun observeCameraState(info: CameraInfo) {
        info.cameraState.observe(this) { state ->
            if (state?.type == CameraState.Type.OPEN && !aeConverged) {
                mainHandler.postDelayed({
                    if (!isCapturing) {
                        aeConverged = true
                        Log.d(TAG, "AE settled after camera OPEN")
                    }
                }, 800)
            }
        }
    }

    private fun startFocusRetryLoop() {
        focusRetryJob?.cancel()
        focusRetryJob = lifecycleScope.launch {
            while (isActive && !isCapturing) {
                delay(FOCUS_RETRY_MS)
                if (!isCapturing && lastFramingState != FramingOverlayView.FramingState.GOOD) {
                    val cx = previewView.width / 2f
                    val cy = previewView.height / 2f
                    if (cx > 0f && cy > 0f) triggerFocusAt(cx, cy)
                }
            }
        }
    }

    private fun showFocusRipple(x: Float, y: Float) {
        focusRipple.visibility = View.VISIBLE
        focusRipple.x = x - focusRipple.width / 2f
        focusRipple.y = y - focusRipple.height / 2f

        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(focusRipple, "scaleX", 0.5f, 1.5f),
                ObjectAnimator.ofFloat(focusRipple, "scaleY", 0.5f, 1.5f),
                ObjectAnimator.ofFloat(focusRipple, "alpha", 1.0f, 0.0f),
            )
            duration = FOCUS_RIPPLE_MS
            start()
        }

        mainHandler.postDelayed({
            focusRipple.visibility = View.GONE
            focusRipple.alpha  = 1.0f
            focusRipple.scaleX = 1.0f
            focusRipple.scaleY = 1.0f
        }, FOCUS_RIPPLE_MS)
    }

    private fun startValidationPolling() {
        validationJob?.cancel()
        networkErrorCount    = 0
        consecutiveBadFrames = 0

        validationJob = lifecycleScope.launch(Dispatchers.IO) {
            val api = RetrofitClient.getService(BASE_URL)
            while (isActive && !isCapturing) {
                if (aeConverged) runPreviewCheck(api)
                delay(VALIDATION_INTERVAL_MS)
            }
        }
    }

    private suspend fun runPreviewCheck(api: com.app.clearx.network.ApiService) {
        if (isCapturing) return

        val gate = gatekeeperResult
        if (gate != null && !gate.passesGate) {
            Log.d(TAG, "Preview-check skipped — gatekeeper not passed")
            return
        }

        val file     = File(cacheDir, "prev_${System.currentTimeMillis()}.jpg")
        val deferred = CompletableDeferred<Boolean>()

        try {
            imageCapture?.takePicture(
                ImageCapture.OutputFileOptions.Builder(file).build(),
                cameraExecutor,
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                        // A canceled polling job may receive a late camera callback.
                        if (!deferred.complete(true)) file.delete()
                    }
                    override fun onError(exc: ImageCaptureException) { deferred.complete(false) }
                },
            ) ?: run { deferred.complete(false) }

            if (!deferred.await() || !file.exists()) return
            val jpeg = PreviewImageEncoder.encode(file)
            val part = MultipartBody.Part.createFormData(
                "file", "preview.jpg",
                jpeg.toRequestBody("image/jpeg".toMediaTypeOrNull()),
            )
            val resp = api.previewCheck(part)
            if (resp.isSuccessful) {
                networkErrorCount = 0
                resp.body()?.let { updatePreviewUI(it) }
            } else if (resp.code() in listOf(400, 413, 415)) {
                // Bad/oversized camera images do not mean the server is offline.
                networkErrorCount = 0
                Log.w(TAG, "Preview image rejected: HTTP ${resp.code()}, ${jpeg.size} bytes")
                withContext(Dispatchers.Main) {
                    showGuidance("Preview could not be checked. Hold steady or reopen the camera.")
                }
            } else {
                handleNetworkError("HTTP ${resp.code()}")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleNetworkError(e.message ?: "unknown")
        } finally {
            deferred.cancel()
            file.delete()
        }
    }

    private fun handleNetworkError(reason: String) {
        networkErrorCount++
        Log.w(TAG, "Validation error #$networkErrorCount: $reason")
        if (networkErrorCount >= OFFLINE_THRESHOLD) {
            runOnUiThread {
                framingOverlay.setState(FramingOverlayView.FramingState.CONNECTING)
                lastFramingState = FramingOverlayView.FramingState.CONNECTING
                showGuidance(UIStrings.SERVER_OFFLINE)
                val gateOk = gatekeeperResult?.passesGate ?: false
                setCaptureButtonEnabled(gateOk, dimmed = gateOk)
                laserScanner.stopScanning()
            }
        }
    }

    private fun updatePreviewUI(result: PreviewCheckResponse) {
        runOnUiThread {
            val gateOk = gatekeeperResult?.passesGate ?: true
            isUsable = result.readable && gateOk

            val targetState = when {
                !gateOk        -> FramingOverlayView.FramingState.BAD
                result.readable -> FramingOverlayView.FramingState.GOOD
                result.score >= 35 -> FramingOverlayView.FramingState.PARTIAL
                else           -> FramingOverlayView.FramingState.BAD
            }

            val newState = resolveStateWithDebounce(targetState)

            if (newState.ordinal > lastHapticState.ordinal) {
                haptics.triggerHaptic(HapticFeedbackManager.HapticType.TICK)
            }

            framingOverlay.setState(newState)
            lastHapticState  = newState
            lastFramingState = newState

            when (newState) {
                FramingOverlayView.FramingState.GOOD,
                FramingOverlayView.FramingState.PARTIAL -> {
                    val currentFrame = framingOverlay.getAbsoluteFrameRect()
                    laserScanner.updateFrameBounds(currentFrame)
                    laserScanner.startScanning()
                }
                else -> laserScanner.stopScanning()
            }

            val guidanceMsg = when {
                !gateOk -> gatekeeperResult?.activeErrors.orEmpty()
                    .joinToString("\n").ifBlank { UIStrings.HOLD_STEADY }
                else -> result.instruction.ifBlank { UIStrings.ALIGN_INSTRUCTION }
            }
            showGuidance(guidanceMsg)

            if (newState == FramingOverlayView.FramingState.PARTIAL && gateOk) {
                val typeHint = if (result.text_type == "handwritten") " " else ""
                showGuidance("${typeHint}${result.score}% legible — ${result.instruction}")
            }

            val canCapture = gateOk && (newState == FramingOverlayView.FramingState.GOOD ||
                    newState == FramingOverlayView.FramingState.PARTIAL)
            setCaptureButtonEnabled(canCapture, dimmed = newState == FramingOverlayView.FramingState.PARTIAL)

            if (isUsable) {
                if (goodStateStartMs == 0L) goodStateStartMs = System.currentTimeMillis()
                val held = System.currentTimeMillis() - goodStateStartMs
                val autoHold = if (result.text_type == "handwritten" && result.handwriting_confidence >= 0.7f) {
                    AUTO_CAPTURE_HOLD_MS / 2
                } else {
                    AUTO_CAPTURE_HOLD_MS
                }
                if (held >= autoHold && !autoCaptureArmed) {
                    autoCaptureArmed = true
                    pulseCaptureButton()
                }
            } else {
                goodStateStartMs = 0L
                autoCaptureArmed = false
                captureButton.clearAnimation()
            }

            Log.d(TAG, "Preview-check: score=${result.score} readable=${result.readable} " +
                    "state=$newState gateOk=$gateOk type=${result.text_type}")
        }
    }

    private fun updateDocumentQuad(points: QuadPoints?) {
        framingOverlay.updateDocumentQuad(points)
    }

    private fun resolveStateWithDebounce(target: FramingOverlayView.FramingState): FramingOverlayView.FramingState {
        val isImprovement = when {
            target == FramingOverlayView.FramingState.GOOD -> true
            target == FramingOverlayView.FramingState.PARTIAL &&
                    lastFramingState == FramingOverlayView.FramingState.BAD -> true
            else -> false
        }

        return if (isImprovement) {
            consecutiveBadFrames = 0
            target
        } else if (target == FramingOverlayView.FramingState.BAD ||
            target == FramingOverlayView.FramingState.PARTIAL) {
            consecutiveBadFrames++
            if (consecutiveBadFrames >= BAD_FRAME_DEBOUNCE) target else lastFramingState
        } else {
            target
        }
    }

    private fun setCaptureButtonEnabled(enabled: Boolean, dimmed: Boolean = false) {
        captureButton.isEnabled = enabled
        val targetAlpha = when {
            !enabled -> 0.25f
            dimmed   -> 0.65f
            else     -> 1.0f
        }
        captureButton.animate().alpha(targetAlpha).setDuration(200).start()
    }

    private fun showGuidance(text: String) {
        if (guidanceText.text.toString() != text) {
            guidanceText.animate().alpha(0f).setDuration(100).withEndAction {
                guidanceText.text = text
                guidanceText.animate().alpha(1f).setDuration(150).start()
            }.start()
        }
    }

    private fun pulseCaptureButton() {
        captureButton.startAnimation(
            AlphaAnimation(1.0f, 0.5f).apply {
                duration     = 600
                repeatCount  = Animation.INFINITE
                repeatMode   = Animation.REVERSE
            }
        )
    }

    private fun handleSelectedFile(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            var source: File? = null
            try {
                val type = contentResolver.getType(uri)?.lowercase()
                require(type in setOf("image/jpeg", "image/png", "image/webp")) {
                    "Choose a JPEG, PNG, or WebP image."
                }
                val selected = File.createTempFile("selected_", ".jpg", cacheDir)
                source = selected
                contentResolver.openInputStream(uri).use { input ->
                    requireNotNull(input) { "Unable to open the selected image" }
                    FileOutputStream(selected).use { output -> ImageInputPolicy.copyWithinLimit(input, output) }
                }
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(selected.absolutePath, bounds)
                ImageInputPolicy.validateDimensions(bounds.outWidth, bounds.outHeight)
                normalizeSelectedImage(selected)
                require(selected.length() <= ImageInputPolicy.MAX_BYTES) { "The image must be 10 MB or smaller." }
                withContext(Dispatchers.Main) { showCropDialog(selected) }
                source = null // CropActivity now owns cleanup.
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (exception: Exception) {
                Log.w(TAG, "Selected image preparation failed", exception)
                withContext(Dispatchers.Main) {
                    cameraSuppressed = false
                    resumeLiveCameraIfNeeded()
                    showFileError("We could not open that image. Choose a JPEG, PNG, or WebP image under 10 MB and 24 megapixels.")
                }
            } finally {
                source?.delete()
            }
        }
    }

    private fun normalizeSelectedImage(file: File) {
        val bitmaps = mutableSetOf<Bitmap>()
        try {
            val bitmap = checkNotNull(BitmapFactory.decodeFile(file.absolutePath)).also { bitmaps.add(it) }
            val orientation = ExifInterface(file).getAttributeInt(
                ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            val matrix = Matrix().apply {
                when (orientation) {
                    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
                    ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
                    ExifInterface.ORIENTATION_TRANSPOSE -> { setRotate(90f); postScale(-1f, 1f) }
                    ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
                    ExifInterface.ORIENTATION_TRANSVERSE -> { setRotate(270f); postScale(-1f, 1f) }
                    ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(270f)
                }
            }
            val normalized = if (matrix.isIdentity) bitmap else Bitmap.createBitmap(
                bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true).also { bitmaps.add(it) }
            file.outputStream().use { check(normalized.compress(Bitmap.CompressFormat.JPEG, 95, it)) }
        } finally {
            bitmaps.forEach { it.recycle() }
        }
    }

    private fun chooseImage() {
        if (isCapturing) return
        cameraSuppressed = true
        releaseCamera()
        filePickerLauncher.launch(arrayOf("image/jpeg", "image/png", "image/webp"))
    }

    private fun showCropDialog(sourceFile: File) {
        cameraSuppressed = true
        releaseCamera()
        cropLauncher.launch(
            android.content.Intent(this, CropActivity::class.java)
                .putExtra(CropActivity.EXTRA_SOURCE, sourceFile.absolutePath)
                .putExtra(CropActivity.EXTRA_BASE_URL, BASE_URL)
        )
    }

    private fun showProcessingPrivacy() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.processing_privacy_title)
            .setMessage(R.string.processing_privacy_body)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun startCroppedProcessing(croppedFile: File) {
        if (!croppedFile.isFile) {
            showFileError("The cropped image is no longer available.")
            return
        }
        selectFileButton.setSelectedState(true)
        isCapturing = true
        validationJob?.cancel()
        focusRetryJob?.cancel()
        laserScanner.stopScanning()
        cameraContainer.visibility = View.GONE
        processingOverlay.visibility = View.VISIBLE
        processingStatusText.text = UIStrings.ENHANCING
        lifecycleScope.launch(Dispatchers.IO) {
            sendToBackend(croppedFile, uploadedFile = true)
        }
    }

    private fun showFileError(message: String) {
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun captureAndProcess() {
        if (isCapturing) return

        val gate = gatekeeperResult
        if (gate != null && !gate.passesGate) {
            gate.guidance?.let { showGuidance(it) }
            Log.d(TAG, "Capture blocked by gatekeeper at tap time")
            return
        }

        haptics.triggerHaptic(HapticFeedbackManager.HapticType.SUCCESS)

        isCapturing = true
        validationJob?.cancel()
        focusRetryJob?.cancel()
        captureButton.clearAnimation()
        laserScanner.stopScanning()

        cameraContainer.visibility  = View.GONE
        processingOverlay.visibility = View.VISIBLE
        processingStatusText.text   = UIStrings.CAPTURING
        isFlashOn = false
        cameraControl?.enableTorch(false)
        flashlightButton.alpha = 0.45f
        flashlightButton.setColorFilter(android.graphics.Color.WHITE)

        mainHandler.postDelayed({
            val file    = File(cacheDir, "capture_${System.currentTimeMillis()}.jpg")
            val options = ImageCapture.OutputFileOptions.Builder(file).build()

            imageCapture?.takePicture(options, cameraExecutor,
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                        if (!normalizeImageOrientation(file)) {
                            file.delete()
                            runOnUiThread { showError(UIStrings.ERROR_CAPTURE) }
                            return
                        }
                        mainHandler.post { processingStatusText.text = UIStrings.ENHANCING }
                        lifecycleScope.launch(Dispatchers.IO) { sendToBackend(file) }
                    }

                    override fun onError(exc: ImageCaptureException) {
                        Log.e(TAG, "Capture error", exc)
                        runOnUiThread { showError(UIStrings.ERROR_CAPTURE) }
                    }
                }
            )
        }, 250)
    }

    private suspend fun sendToBackend(file: File, uploadedFile: Boolean = false) {
        withContext(Dispatchers.Main) {
            processingOverlay.visibility = View.GONE
            terminalController.showTerminal()
            terminalController.updateStatus(TrOCRStage.INIT)
        }
        var processingFile: File? = null
        try {
            if (captureSessionId == null) {
                captureSessionId = sessionCreationJob?.await()
            }
            check(!captureSessionId.isNullOrBlank()) {
                "Could not register capture session. Reopen the camera and check the server connection and user account."
            }
            val croppedFile = if (uploadedFile) {
                file
            } else {
                cropToGuideFrame(file)
                    ?: throw IllegalStateException("Unable to crop capture to the visible document border")
            }
            processingFile = croppedFile
            withContext(Dispatchers.Main) {
                terminalController.updateStatus(TrOCRStage.PREPROCESS)
            }

            val originalPart = MultipartBody.Part.createFormData(
                "file", croppedFile.name,
                croppedFile.asRequestBody(
                    when (croppedFile.extension.lowercase()) {
                        "png" -> "image/png"
                        "webp" -> "image/webp"
                        else -> "image/jpeg"
                    }.toMediaTypeOrNull()
                ),
            )
            val processingService = RetrofitClient.getProcessingService(BASE_URL)
            val imageIdPart = captureSessionId?.let { sessionId ->
                val imageId = if (uploadedFile) {
                    val response = processingService.uploadFile(
                        originalPart,
                        MultipartBody.Part.createFormData("session_id", sessionId),
                    )
                    if (response.code() == 422) {
                        val detail = org.json.JSONObject(response.errorBody()?.string().orEmpty())
                            .optJSONObject("detail")
                        val guidance = detail?.optString("instruction")
                            ?.takeIf { it.isNotBlank() }
                            ?: "Image quality check failed. Please upload again with a clearer photo."
                        withContext(Dispatchers.Main) {
                            terminalController.forceHide()
                            showError(guidance, uploadedFile = true)
                        }
                        return
                    }
                    check(response.isSuccessful) {
                        "Image registration failed: HTTP ${response.code()} ${response.errorBody()?.string()}"
                    }
                    response.body()?.takeIf { it.status == "created" }?.image_id
                } else {
                    captureAttemptNumber++
                    val response = processingService.captureImage(
                        originalPart,
                        MultipartBody.Part.createFormData("session_id", sessionId),
                        MultipartBody.Part.createFormData(
                            "capture_attempt_number", captureAttemptNumber.toString(),
                        ),
                    )
                    check(response.isSuccessful) {
                        "Image registration failed: HTTP ${response.code()} ${response.errorBody()?.string()}"
                    }
                    response.body()?.takeIf { it.status == "created" }?.image_id
                }
                imageId?.let { MultipartBody.Part.createFormData("image_id", it) }
            }

            checkNotNull(imageIdPart) { "Image registration returned no image ID. Please retry." }
            // Uploads are validated by upload-file before it returns an image ID.
            if (!uploadedFile) {
                val validationResponse = processingService.validateCapture(originalPart, imageIdPart)
                check(validationResponse.isSuccessful) {
                    "Capture validation failed: HTTP ${validationResponse.code()} ${validationResponse.errorBody()?.string()}"
                }
                val validation = validationResponse.body()
                if (validation?.is_usable != true) {
                    withContext(Dispatchers.Main) {
                        showError(validation?.instruction ?: "The photo could not pass quality checks.")
                    }
                    return
                }
            }

            val processingPart = MultipartBody.Part.createFormData(
                "file", croppedFile.name,
                croppedFile.asRequestBody(
                    when (croppedFile.extension.lowercase()) {
                        "png" -> "image/png"
                        "webp" -> "image/webp"
                        else -> "image/jpeg"
                    }.toMediaTypeOrNull()
                ),
            )

            withContext(Dispatchers.Main) {
                terminalController.updateStatus(TrOCRStage.FEATURE_EXTRACTION)
            }

            val response = processingService.processImage(
                processingPart, imageIdPart,
            )

            if (response.isSuccessful) file.delete()
            if (response.isSuccessful && croppedFile != file) croppedFile.delete()

            withContext(Dispatchers.Main) {
                terminalController.updateStatus(TrOCRStage.DECODING)
            }

            withContext(Dispatchers.Main) {
                terminalController.updateStatus(TrOCRStage.POSTPROCESS)
            }

            withContext(Dispatchers.Main) {
                terminalController.onHidden = {
                    if (response.isSuccessful) {
                        val body = response.body()
                        when (body?.status) {
                            "success"          -> showResult(body)
                            "rejected",
                            "no_text_detected" -> showRejectionResult(body)
                            else               -> showError(UIStrings.ERROR_EMPTY, uploadedFile)
                        }
                    } else {
                        showError("We could not read this photo right now. Check your connection and try again.", uploadedFile)
                    }
                }
                terminalController.updateStatus(TrOCRStage.DONE)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Backend error", e)
            withContext(Dispatchers.Main) {
                terminalController.forceHide()
                showError(UIStrings.ERROR_NETWORK, uploadedFile)
            }
        } finally {
            file.delete()
            processingFile?.takeIf { it != file }?.delete()
        }
    }

    private fun normalizeImageOrientation(file: File): Boolean {
        return try {
            val exif = ExifInterface(file)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
            if (orientation == ExifInterface.ORIENTATION_NORMAL ||
                orientation == ExifInterface.ORIENTATION_UNDEFINED
            ) return true

            val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return false
            val matrix = Matrix().apply {
                when (orientation) {
                    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
                    ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
                    ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
                    ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(-90f)
                    else -> return false
                }
            }
            val normalized = Bitmap.createBitmap(
                bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true,
            )
            file.outputStream().use { output ->
                if (!normalized.compress(Bitmap.CompressFormat.JPEG, 100, output)) return false
            }
            if (normalized !== bitmap) normalized.recycle()
            bitmap.recycle()
            true
        } catch (exception: Exception) {
            Log.e(TAG, "Image orientation normalization failed", exception)
            false
        }
    }

    private fun cropToGuideFrame(fullFile: File): File? {
        return try {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(fullFile.absolutePath, opts)
            val jpegW = opts.outWidth
            val jpegH = opts.outHeight
            if (jpegW <= 0 || jpegH <= 0) return null

            val viewW = previewView.width.toFloat()
            val viewH = previewView.height.toFloat()
            if (viewW <= 0f || viewH <= 0f) return null

            val frame = framingOverlay.getAbsoluteFrameRect()
            if (frame.isEmpty || frame.left < 0f || frame.top < 0f ||
                frame.right > viewW || frame.bottom > viewH
            ) {
                Log.w(TAG, "Visible frame is outside preview bounds: frame=$frame view=${viewW}x$viewH")
                return null
            }

            val scaleToFill = maxOf(viewW / jpegW, viewH / jpegH)
            val scaledJpegW = jpegW * scaleToFill
            val scaledJpegH = jpegH * scaleToFill
            val offsetX = (scaledJpegW - viewW) / 2f
            val offsetY = (scaledJpegH - viewH) / 2f

            // PreviewView.ScaleType.FILL_CENTER scales the oriented JPEG until it
            // fills the view, then clips the centered excess. Reverse that exact
            // transform to map the visible overlay border into JPEG coordinates.
            val left = kotlin.math.floor((frame.left + offsetX) / scaleToFill).toInt()
            val top = kotlin.math.floor((frame.top + offsetY) / scaleToFill).toInt()
            val right = kotlin.math.ceil((frame.right + offsetX) / scaleToFill).toInt()
            val bottom = kotlin.math.ceil((frame.bottom + offsetY) / scaleToFill).toInt()

            val cropRect = Rect(
                left.coerceIn(0, jpegW),
                top.coerceIn(0, jpegH),
                right.coerceIn(0, jpegW),
                bottom.coerceIn(0, jpegH),
            )

            if (cropRect.width() < 64 || cropRect.height() < 64 ||
                cropRect.left >= cropRect.right || cropRect.top >= cropRect.bottom
            ) {
                Log.w(TAG, "Crop rect too small: $cropRect — skipping crop")
                return null
            }

            Log.d(TAG, "Frame crop: jpeg=${jpegW}x${jpegH} " +
                    "scale=${"%.3f".format(scaleToFill)} " +
                    "offset=(${offsetX.toInt()},${offsetY.toInt()}) " +
                    "cropRect=$cropRect")

            val cropped: Bitmap = fullFile.inputStream().use { stream ->
                val decoder = BitmapRegionDecoder.newInstance(stream, false)
                    ?: return null
                decoder.decodeRegion(cropRect, BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                })
            }

            val outFile = File(cacheDir, "crop_${System.currentTimeMillis()}.jpg")
            outFile.outputStream().use { out ->
                cropped.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }
            cropped.recycle()

            Log.d(TAG, "Cropped JPEG saved: ${outFile.name} " +
                    "${cropRect.width()}x${cropRect.height()}px")
            outFile

        } catch (e: Exception) {
            Log.e(TAG, "cropToGuideFrame failed: ${e.message}", e)
            null
        }
    }

    private fun showResult(result: ProcessImageResponse) {
        processingOverlay.visibility = View.GONE
        resultScreen.visibility      = View.VISIBLE

        // Display enhanced and original images
        result.enhanced_image?.let { b64 ->
            val bytes = Base64.decode(b64, Base64.DEFAULT)
            enhancedImageView.setImageBitmap(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
        }
        result.original_image?.let { b64 ->
            val bytes = Base64.decode(b64, Base64.DEFAULT)
            originalImageView.setImageBitmap(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
        }

        val conf = if (result.trocr_confidence > 0) result.trocr_confidence
        else result.quality_score

        val isBestEffort = result.detection == "full_frame" ||
                result.detection == "frame_region"

        val badgeText = when {
            isBestEffort         -> UIStrings.qualityBadge(conf)
            conf >= 95           -> "✔ ${conf}% — TrOCR Ready"
            conf >= 80           -> "⚠ ${conf}% — Usable"
            conf >= 60           -> "⚠ ${conf}% — Poor"
            else                 -> "✖ ${conf}% — Retake"
        }

        val badgeColor = when {
            conf >= 95 -> Color.parseColor("#4CAF50")
            conf >= 80 -> Color.parseColor("#FFC107")
            else       -> Color.parseColor("#F44336")
        }

        val passesUsed = result.passes_used.takeIf { it > 0 } ?: 1
        val subLabel   = UIStrings.trocrSubLabel(conf, passesUsed, result.ocr_source)

        qualityBadge.text      = badgeText
        qualityBadge.setTextColor(badgeColor)
        qualityScoreText.text  = subLabel

        // Populate prescription section
        val hasPrescriptionData = !result.extracted_text.isNullOrBlank()

        if (hasPrescriptionData) {
            prescriptionCard.visibility = View.VISIBLE
            medicineAdapter.submitList(result.medicines)
            rawOcrSection.visibility = View.VISIBLE
            rawOcrText.text = result.extracted_text
            val confidencePct = (result.drug_confidence * 100).toInt()
            val matchLabel    = if (result.drug_matched) "✔ Matched" else "✖ No match"
            rxConfidenceBadge.text = "Drug match: $matchLabel ($confidencePct%)"
            rxConfidenceBadge.setTextColor(
                if (result.drug_matched) Color.parseColor("#4CAF50")
                else Color.parseColor("#F44336")
            )
        } else {
            prescriptionCard.visibility = View.GONE
            rawOcrSection.visibility = View.GONE
        }

        Log.i(TAG, "Result: trocr=$conf%, passes=$passesUsed, " +
                "drug=${result.drug_name}, extracted=${result.extracted_text}, " +
                "detection=${result.detection}, time=${result.processing_time_ms}ms")
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Parsed medicines[]: ${result.medicines}")
        }
    }

    private fun showRejectionResult(result: ProcessImageResponse) {
        processingOverlay.visibility = View.GONE
        resultScreen.visibility      = View.VISIBLE

        enhancedImageView.setImageBitmap(null)
        originalImageView.setImageBitmap(null)
        prescriptionCard.visibility = View.GONE  // hide prescription card on rejection
        rawOcrSection.visibility = View.GONE

        val reasonLabel = when (result.reason ?: "") {
            "too_dark"    -> "⚠ TOO DARK"
            "too_bright"  -> "⚠ TOO BRIGHT"
            "blurry"      -> "⚠ BLURRY"
            "poor_framing"-> "⚠ POOR FRAMING"
            "no_text"     -> "⚠ NO TEXT FOUND"
            else          -> "⚠ RETAKE NEEDED"
        }

        qualityBadge.text = reasonLabel
        qualityBadge.setTextColor(Color.parseColor("#F44336"))
        qualityScoreText.text = result.message ?: "Please retake the photo."

        retakeButton.requestFocus()
    }

    private fun showError(message: String, uploadedFile: Boolean = false) {
        terminalController.forceHide()
        processingOverlay.visibility = View.GONE
        MaterialAlertDialogBuilder(this)
            .setTitle("Could not read the prescription")
            .setMessage(message)
            .setPositiveButton(if (uploadedFile) "Upload again" else "Retake") { _, _ ->
                resetToCamera()
                if (uploadedFile) chooseImage()
            }

            .setOnCancelListener { resetToCamera() }
            .show()
    }

    private fun resetToCamera() {
        cameraSuppressed = false
        selectFileButton.setSelectedState(false)
        isCapturing          = false
        autoCaptureArmed     = false
        goodStateStartMs     = 0L
        networkErrorCount    = 0
        consecutiveBadFrames = 0
        gatekeeperResult     = null
        lastFramingState     = FramingOverlayView.FramingState.IDLE
        lastHapticState      = FramingOverlayView.FramingState.IDLE
        frameResizeHintShown = false

        resultScreen.visibility      = View.GONE
        processingOverlay.visibility = View.GONE
        terminalController.forceHide()
        cameraContainer.visibility   = View.VISIBLE

        // reset prescription card for next scan
        prescriptionCard.visibility = View.GONE
        rawOcrSection.visibility = View.GONE

        startCamera()
    }

    private fun showPermissionDeniedDialog() {
        AlertDialog.Builder(this)
            .setTitle(UIStrings.PERMISSION_TITLE)
            .setMessage(UIStrings.PERMISSION_MESSAGE)
            .setPositiveButton(UIStrings.OPEN_SETTINGS) { _, _ ->
                startActivity(
                    android.content.Intent(
                        android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                    ).apply { data = Uri.parse("package:$packageName") }
                )
            }
            .setNegativeButton(R.string.select_file) { _, _ -> chooseImage() }
            .show()
    }

    private fun com.app.clearx.network.QuadPointsResponse.toNormalized(): QuadPoints =
        QuadPoints(
            topLeft     = this.top_left,
            topRight    = this.top_right,
            bottomRight = this.bottom_right,
            bottomLeft  = this.bottom_left
        )
}