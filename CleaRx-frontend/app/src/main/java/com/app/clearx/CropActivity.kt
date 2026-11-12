package com.app.clearx

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.app.clearx.network.RetrofitClient
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.File

class CropActivity : AppCompatActivity() {
    private lateinit var cropView: CropSelectionView
    private lateinit var resetButton: MaterialButton
    private lateinit var doneButton: MaterialButton
    private lateinit var preview: ImageView
    private lateinit var previewLabel: TextView
    private lateinit var sendButton: MaterialButton
    private lateinit var qualityGuidance: TextView
    private var qualityJob: Job? = null
    private var qualityPassed = false
    private var sourceFile: File? = null
    private var croppedFile: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sourceFile = intent.getStringExtra(EXTRA_SOURCE)?.let(::File)
        croppedFile = savedInstanceState?.getString("cropped_file")?.let(::File)
        val sourceBitmap = sourceFile?.let { BitmapFactory.decodeFile(it.absolutePath) }
        if (sourceBitmap == null) {
            finish()
            return
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
        }
        val toolbar = FrameLayout(this).apply {
            setBackgroundColor(Color.rgb(24, 24, 24))
            setPadding(dp(16), dp(8), dp(16), dp(8))
        }
        resetButton = cropButton(R.string.reset_crop).apply {
            text = getString(R.string.reset_crop)
            visibility = View.GONE
            setOnClickListener {
                if (preview.visibility == View.VISIBLE) {
                    preview.visibility = View.GONE
                    previewLabel.visibility = View.GONE
                    sendButton.visibility = View.GONE
                    cropView.visibility = View.VISIBLE
                    doneButton.visibility = View.VISIBLE
                    text = getString(R.string.reset_crop)
                    scheduleQualityCheck()
                } else {
                    cropView.resetCrop()
                    visibility = View.GONE
                }
            }
        }
        doneButton = cropButton(R.string.done_crop, primary = true).apply {
            setOnClickListener { showPreview() }
        }
        toolbar.addView(
            resetButton,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                dp(48),
                Gravity.TOP or Gravity.START,
            ),
        )
        toolbar.addView(
            doneButton,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                dp(48),
                Gravity.TOP or Gravity.END,
            ),
        )

        cropView = CropSelectionView(this).apply {
            setBitmap(sourceBitmap)
            setOnCropChangedListener {
                resetButton.visibility = View.VISIBLE
                scheduleQualityCheck()
            }
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            )
        }
        previewLabel = TextView(this).apply {
            text = getString(R.string.cropped_preview)
            textSize = 14f
            setTextColor(Color.WHITE)
            setPadding(dp(16), dp(12), dp(16), dp(4))
            visibility = View.GONE
        }
        preview = ImageView(this).apply {
            contentDescription = getString(R.string.cropped_preview)
            scaleType = ImageView.ScaleType.FIT_CENTER
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            )
        }
        sendButton = cropButton(R.string.send_image, primary = true).apply {
            text = getString(R.string.send_image)
            visibility = View.GONE
            setOnClickListener { sendCroppedFile() }
        }
        qualityGuidance = TextView(this).apply {
            text = "Checking image quality…"
            textSize = 15f
            setTextColor(Color.WHITE)
            setBackgroundColor(0xDD202020.toInt())
            setPadding(dp(16), dp(12), dp(16), dp(12))
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }

        root.addView(toolbar, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ))
        root.addView(previewLabel)
        val imageContainer = FrameLayout(this).apply {
            addView(cropView)
            addView(preview)
        }
        root.addView(imageContainer, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f,
        ))
        root.addView(qualityGuidance, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ))
        root.addView(TextView(this).apply {
            text = getString(R.string.processing_privacy_short)
            textSize = 12f
            setTextColor(Color.WHITE)
            setBackgroundColor(0xDD202020.toInt())
            setPadding(dp(16), dp(8), dp(16), dp(8))
            isClickable = true
            isFocusable = true
            setOnClickListener {
                MaterialAlertDialogBuilder(this@CropActivity)
                    .setTitle(R.string.processing_privacy_title)
                    .setMessage(R.string.processing_privacy_body)
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            }
        })
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.rgb(24, 24, 24))
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        actions.addView(cropButton(R.string.upload_again).apply {
            setOnClickListener {
                setResult(Activity.RESULT_CANCELED, Intent().putExtra(EXTRA_UPLOAD_AGAIN, true))
                sourceFile?.delete()
                croppedFile?.delete()
                finish()
            }
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        actions.addView(sendButton, LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f,
        ).apply { marginStart = dp(12) })
        root.addView(actions)
        setContentView(root)
        cropView.post { scheduleQualityCheck() }
        showCropInstruction()
    }

    private fun showCropInstruction() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.crop_instruction_title)
            .setMessage(R.string.crop_instruction)
            .setPositiveButton(R.string.crop_instruction_ok, null)
            .show()
    }

    private fun showPreview() {
        val cropped = cropView.croppedBitmap() ?: return
        val output = File(cacheDir, "cropped_${System.currentTimeMillis()}.jpg")
        output.outputStream().use { cropped.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        croppedFile?.delete()
        croppedFile = output
        cropView.visibility = View.GONE
        doneButton.visibility = View.GONE
        resetButton.visibility = View.GONE
        previewLabel.visibility = View.VISIBLE
        preview.visibility = View.VISIBLE
        preview.setImageBitmap(cropped)
        sendButton.visibility = View.VISIBLE
        resetButton.text = getString(R.string.edit_crop)
        resetButton.visibility = View.VISIBLE
        sendButton.isEnabled = qualityPassed
    }

    private fun scheduleQualityCheck() {
        qualityJob?.cancel()
        qualityPassed = false
        sendButton.isEnabled = false
        qualityGuidance.text = "Checking image quality…"
        qualityGuidance.setTextColor(Color.WHITE)
        qualityJob = lifecycleScope.launch {
            // Wait until the user pauses dragging; cancel checks for older crops.
            delay(600)
            val bitmap = cropView.croppedBitmap() ?: return@launch
            try {
                val response = withContext(Dispatchers.IO) {
                    val bytes = ByteArrayOutputStream().use { stream ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)
                        stream.toByteArray()
                    }
                    val baseUrl = BuildConfig.BACKEND_BASE_URL
                    RetrofitClient.getService(baseUrl).validateUpload(
                        MultipartBody.Part.createFormData(
                            "file", "crop.jpg", bytes.toRequestBody("image/jpeg".toMediaType()),
                        ),
                    )
                }
                ensureActive()
                val result = response.body()?.takeIf { response.isSuccessful }
                    ?: error("Quality check unavailable")
                qualityPassed = result.is_usable
                qualityGuidance.text = result.instruction
                qualityGuidance.setTextColor(if (qualityPassed) Color.GREEN else Color.YELLOW)
                sendButton.isEnabled = qualityPassed
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                qualityGuidance.text = "Could not check image quality. Check your connection and adjust the crop to retry."
            } finally {
                bitmap.recycle()
            }
        }
    }

    override fun onStop() {
        qualityJob?.cancel()
        super.onStop()
    }

    override fun onRestart() {
        super.onRestart()
        scheduleQualityCheck()
    }

    private fun sendCroppedFile() {
        if (!qualityPassed) return
        val result = croppedFile ?: return
        setResult(Activity.RESULT_OK, Intent().putExtra(EXTRA_CROPPED, result.absolutePath))
        sourceFile?.delete()
        croppedFile = null
        finish()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("cropped_file", croppedFile?.absolutePath)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        qualityJob?.cancel()
        if (isFinishing) {
            sourceFile?.delete()
            croppedFile?.delete()
        }
        super.onDestroy()
    }

    private fun cropButton(label: Int, primary: Boolean = false): MaterialButton {
        return MaterialButton(this).apply {
            text = getString(label)
            setTextColor(Color.WHITE)
            backgroundTintList = android.content.res.ColorStateList(
                arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
                intArrayOf(Color.rgb(80, 80, 80),
                    if (primary) ContextCompat.getColor(this@CropActivity, R.color.brand_primary)
                    else Color.rgb(24, 24, 24)),
            )
            strokeWidth = if (primary) 0 else dp(1)
            strokeColor = android.content.res.ColorStateList.valueOf(Color.rgb(189, 189, 189))
            cornerRadius = dp(12)
            minHeight = dp(48)
            minimumHeight = dp(48)
            minWidth = dp(96)
            insetTop = 0
            insetBottom = 0
            isAllCaps = false
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setPadding(dp(20), dp(12), dp(20), dp(12))
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_SOURCE = "source_file"
        const val EXTRA_UPLOAD_AGAIN = "upload_again"
        const val EXTRA_CROPPED = "cropped_file"
    }
}
