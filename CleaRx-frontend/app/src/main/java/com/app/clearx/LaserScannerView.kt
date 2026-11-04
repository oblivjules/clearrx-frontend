package com.app.clearx

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator

class LaserScannerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private var laserAnimator: ValueAnimator? = null
    private var currentY = 0f
    private var isScanning = false

    // The active scan area – now updated dynamically from FramingOverlayView
    private var scanRect = RectF()

    private val laserPaint = Paint().apply {
        isAntiAlias = true
    }

    private val glowPaint = Paint().apply {
        color = Color.parseColor("#33FFC107")
        maskFilter = BlurMaskFilter(15f, BlurMaskFilter.Blur.NORMAL)
    }

    private val centerPaint = Paint().apply {
        color = Color.parseColor("#FFFFC107")
    }

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    /**
     * Returns whether the scanner is currently active.
     */
    fun isScanningActive(): Boolean = isScanning

    /**
     * Dynamically update the scanning bounds.
     * @param newBounds Absolute rectangle (in view coordinates) defining the scan area.
     */
    fun updateFrameBounds(newBounds: RectF) {
        if (newBounds == scanRect) return
        val oldTop = scanRect.top
        val oldBottom = scanRect.bottom
        val oldHeight = oldBottom - oldTop

        // Preserve relative position if scanning is active
        var relativePos = 0.5f
        if (isScanning && oldHeight > 0) {
            relativePos = ((currentY - oldTop) / oldHeight).coerceIn(0f, 1f)
        }

        scanRect.set(newBounds)

        // Ensure the line stays inside after the update
        if (isScanning) {
            val newTop = scanRect.top
            val newHeight = scanRect.height()
            if (newHeight > 0) {
                currentY = newTop + relativePos * newHeight
                restartAnimationWithCurrentPosition()
            } else {
                stopScanning()
            }
        }
        invalidate()
    }

    fun startScanning() {
        if (isScanning) return
        if (scanRect.height() <= 0) {
            // No valid bounds yet – delay start until bounds are set
            return
        }
        isScanning = true
        visibility = View.VISIBLE

        restartAnimationWithCurrentPosition()
    }

    private fun restartAnimationWithCurrentPosition() {
        laserAnimator?.cancel()
        val top = scanRect.top
        val bottom = scanRect.bottom
        if (bottom <= top) return

        // Ensure currentY is within [top, bottom]
        currentY = currentY.coerceIn(top, bottom)

        laserAnimator = ValueAnimator.ofFloat(top, bottom).apply {
            duration = 2000
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = LinearInterpolator()

            addUpdateListener { animation ->
                currentY = animation.animatedValue as Float
                updateShader(currentY)
                invalidate()
            }

            // Start from currentY to avoid jumps
            setCurrentFraction((currentY - top) / (bottom - top))
            start()
        }
    }

    fun stopScanning() {
        isScanning = false
        laserAnimator?.cancel()
        laserAnimator = null
        visibility = View.GONE
        invalidate()
    }

    private fun updateShader(y: Float) {
        val shader = LinearGradient(
            scanRect.left, y - 60f, scanRect.left, y + 60f,
            intArrayOf(
                Color.parseColor("#00FFC107"),
                Color.parseColor("#33FFC107"),
                Color.parseColor("#FFFFC107"),
                Color.parseColor("#FFFF9800"),
                Color.parseColor("#33FFC107"),
                Color.parseColor("#00FFC107")
            ),
            floatArrayOf(0f, 0.15f, 0.35f, 0.5f, 0.65f, 1f),
            Shader.TileMode.CLAMP
        )
        laserPaint.shader = shader
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (!isScanning || height == 0 || scanRect.width() <= 0) return

        // Draw laser strictly within scanRect
        canvas.drawRect(scanRect.left, currentY - 25f, scanRect.right, currentY + 25f, glowPaint)
        canvas.drawRect(scanRect.left, currentY - 3f, scanRect.right, currentY + 3f, laserPaint)
        canvas.drawRect(scanRect.left, currentY - 1f, scanRect.right, currentY + 1f, centerPaint)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopScanning()
    }
}