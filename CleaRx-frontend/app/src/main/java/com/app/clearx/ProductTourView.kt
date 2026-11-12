package com.app.clearx

import android.content.Context
import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ScrollView
import androidx.core.view.ViewCompat
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton

/** A self-contained first-use tour that spotlights existing activity views. */
class ProductTourView(
    context: Context,
    private val targets: List<View>,
    private val fallbackTarget: View,
    private val onStepChanged: (Int) -> Unit = {},
    private val onFinished: () -> Unit,
) : FrameLayout(context) {

    private data class TourStep(
        val title: String,
        val body: String,
        val targetIndex: Int,
    )

    private val steps = listOf(
        TourStep(
            "Capture the prescription",
            "Place the prescription flat in even light, include the full medication instructions, and hold steady. You can also choose an existing image and crop it before sending. Tap Capture when the image is ready. Tap the preview to focus; use the flashlight if needed.\n\nImages may be processed by an external AI service. Read the privacy notice on the capture or crop screen before sending.\n\nPharmacists: start with one complete prescription.\n\nNurses: keep the original available when reading the medicine instructions.",
            0,
        ),
        TourStep(
            "Read the handwriting",
            "After capture, this status shows that CleaRx is reading the handwriting and preparing readable text. Clear photos help, but difficult handwriting can still produce errors. If reading fails, retake in better light or try again.",
            1,
        ),
        TourStep(
            "Look up medicine names",
            "The name-similarity label measures spelling agreement with the medicine list. Unmatched names may be missing or read incorrectly. A match does not establish that a medicine is appropriate or safe.",
            2,
        ),
        TourStep(
            "Read and compare results",
            "Review the medicine cards against the original prescription. Tap the highlighted Details button to open the original photo, enhanced photo, raw text, and confidence information. Use Result text size for larger text.\n\n" + context.getString(R.string.review_guidance) + "\n\n" + context.getString(R.string.reader_disclaimer),
            3,
        ),
    )

    private val dimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(145, 0, 0, 0)
    }
    private val clearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.brand_accent)
        style = Paint.Style.STROKE
        strokeWidth = dp(2).toFloat()
    }
    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = dp(2).toFloat()
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val highlightRect = RectF()
    private var highlightAnimator: ValueAnimator? = null
    private var currentStep = 0
    private lateinit var card: ViewGroup
    private lateinit var titleView: TextView
    private lateinit var bodyView: TextView
    private lateinit var progressView: TextView
    private lateinit var backButton: MaterialButton
    private lateinit var nextButton: MaterialButton

    init {
        setWillNotDraw(false)
        setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        isClickable = true
        isFocusable = true

        card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(14))
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = dp(10).toFloat()
            }
            elevation = dp(8).toFloat()
        }
        titleView = TextView(context).apply {
            setTextColor(ContextCompat.getColor(context, R.color.text_main))
            textSize = 19f
            setTypeface(typeface, Typeface.BOLD)
        }
        progressView = TextView(context).apply {
            setTextColor(ContextCompat.getColor(context, R.color.brand_primary))
            textSize = 12f
            setTypeface(typeface, Typeface.BOLD)
        }
        bodyView = TextView(context).apply {
            setTextColor(ContextCompat.getColor(context, R.color.text_main))
            textSize = 14f
            setLineSpacing(dp(2).toFloat(), 1f)
        }
        val actions = LinearLayout(context).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
        }
        backButton = MaterialButton(context).apply {
            text = "Back"
            setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            setBackgroundColor(Color.TRANSPARENT)
            minHeight = dp(44)
            setPadding(dp(8), 0, dp(8), 0)
        }
        nextButton = MaterialButton(context).apply {
            setTextColor(Color.WHITE)
            backgroundTintList = android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(context, R.color.brand_primary)
            )
            minHeight = dp(44)
            setPadding(dp(14), 0, dp(14), 0)
        }
        actions.addView(backButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(48)))
        actions.addView(nextButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(48)))
        card.addView(progressView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(24)))
        card.addView(titleView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        val bodyScroll = object : ScrollView(context) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val limit = (resources.displayMetrics.heightPixels * 0.45f).toInt()
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(limit, MeasureSpec.AT_MOST))
            }
        }.apply { addView(bodyView) }
        card.addView(bodyScroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(10)
        })
        card.addView(actions, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)).apply {
            topMargin = dp(8)
        })
        addView(card, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        backButton.setOnClickListener { if (currentStep == 0) finish() else showStep(currentStep - 1, true) }
        nextButton.setOnClickListener { if (currentStep == steps.lastIndex) finish() else showStep(currentStep + 1, true) }
    }

    fun show() {
        post { showStep(0, false) }
    }

    private fun showStep(step: Int, animate: Boolean) {
        currentStep = step
        onStepChanged(step)
        val tourStep = steps[step]
        progressView.text = "Step ${step + 1} of ${steps.size}"
        ViewCompat.setAccessibilityHeading(titleView, true)
        titleView.text = tourStep.title
        bodyView.text = tourStep.body
        announceForAccessibility("${progressView.text}. ${tourStep.title}")
        backButton.visibility = View.VISIBLE
        backButton.text = if (step == 0) "Skip" else "Back"
        nextButton.text = if (step == steps.lastIndex) "Start reading" else "Next"
        post {
            updateHighlight(animate)
            if (animate) {
                card.alpha = 0f
                card.translationY = dp(8).toFloat()
                card.animate().alpha(1f).translationY(0f).setDuration(220L).start()
            }
        }
    }

    private fun updateHighlight(animate: Boolean = false) {
        val target = targets.getOrNull(steps[currentStep].targetIndex)
        val chosenTarget = if (target != null && target.visibility == View.VISIBLE && target.width > 0 && target.height > 0) target else fallbackTarget
        val overlayLocation = IntArray(2)
        val targetLocation = IntArray(2)
        getLocationOnScreen(overlayLocation)
        chosenTarget.getLocationOnScreen(targetLocation)
        val left = (targetLocation[0] - overlayLocation[0]).toFloat()
        val top = (targetLocation[1] - overlayLocation[1]).toFloat()
        val right = left + chosenTarget.width
        val bottom = top + chosenTarget.height
        val newRect = RectF(left - dp(8), top - dp(8), right + dp(8), bottom + dp(8))

        val cardWidth = width - dp(32)
        val cardHeight = card.measuredHeight.coerceAtLeast(dp(220))
        val placeBelow = newRect.centerY() < height * 0.45f
        val cardTop = if (placeBelow) {
            (newRect.bottom + dp(24)).toInt()
        } else {
            (newRect.top - cardHeight - dp(24)).toInt()
        }.coerceIn(dp(16), (height - cardHeight - dp(16)).coerceAtLeast(dp(16)))
        val cardParams = card.layoutParams as LayoutParams
        cardParams.width = cardWidth
        cardParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
        cardParams.leftMargin = dp(16)
        cardParams.topMargin = cardTop
        card.layoutParams = cardParams
        highlightAnimator?.cancel()
        if (animate && !highlightRect.isEmpty) {
            val oldRect = RectF(highlightRect)
            highlightAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 260L
                addUpdateListener { animator ->
                    val fraction = animator.animatedValue as Float
                    highlightRect.set(
                        oldRect.left + (newRect.left - oldRect.left) * fraction,
                        oldRect.top + (newRect.top - oldRect.top) * fraction,
                        oldRect.right + (newRect.right - oldRect.right) * fraction,
                        oldRect.bottom + (newRect.bottom - oldRect.bottom) * fraction,
                    )
                    invalidate()
                }
                start()
            }
        } else {
            highlightRect.set(newRect)
        }
        invalidate()
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        if (width > 0 && height > 0) post { updateHighlight() }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val layer = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dimPaint)
        canvas.drawRoundRect(highlightRect, dp(10).toFloat(), dp(10).toFloat(), clearPaint)
        canvas.restoreToCount(layer)
        canvas.drawRoundRect(highlightRect, dp(10).toFloat(), dp(10).toFloat(), outlinePaint)
        drawArrow(canvas)
    }

    private fun drawArrow(canvas: Canvas) {
        if (highlightRect.isEmpty || card.height == 0) return
        val cardTop = card.top.toFloat()
        val cardBottom = card.bottom.toFloat()
        val path = Path()
        if (card.top > highlightRect.bottom) {
            val startX = card.left + card.width / 2f
            val startY = cardTop
            val endX = highlightRect.centerX()
            val endY = highlightRect.bottom + dp(4)
            path.moveTo(startX, startY)
            path.lineTo(endX, endY)
            path.moveTo(endX, endY)
            path.lineTo(endX - dp(6), endY - dp(10))
            path.moveTo(endX, endY)
            path.lineTo(endX + dp(6), endY - dp(10))
        } else {
            val startX = card.left + card.width / 2f
            val startY = cardBottom
            val endX = highlightRect.centerX()
            val endY = highlightRect.top - dp(4)
            path.moveTo(startX, startY)
            path.lineTo(endX, endY)
            path.moveTo(endX, endY)
            path.lineTo(endX - dp(6), endY + dp(10))
            path.moveTo(endX, endY)
            path.lineTo(endX + dp(6), endY + dp(10))
        }
        canvas.drawPath(path, arrowPaint)
    }

    private var finished = false

    fun finish() {
        if (finished) return
        finished = true
        onFinished()
    }

    override fun onDetachedFromWindow() {
        highlightAnimator?.cancel()
        card.animate().cancel()
        super.onDetachedFromWindow()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
