package com.app.clearx

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.max
import kotlin.math.min

class CropSelectionView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val shadePaint = Paint().apply { color = 0x99000000.toInt() }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val imageRect = RectF()
    private val cropRect = RectF()
    private var bitmap: Bitmap? = null
    private var lastX = 0f
    private var lastY = 0f
    private var dragMode = DragMode.NONE
    private var cropChanged = false
    private var onCropChanged: (() -> Unit)? = null
    private val handleRadius = 44f
    private val minimumCropSize = 100f

    private enum class DragMode {
        NONE, MOVE, TOP, RIGHT, BOTTOM, LEFT,
        TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT
    }

    fun setBitmap(value: Bitmap) {
        bitmap = value
        requestLayout()
        invalidate()
    }

    fun setOnCropChangedListener(listener: () -> Unit) {
        onCropChanged = listener
    }

    fun resetCrop() {
        cropRect.set(
            imageRect.left + imageRect.width() * 0.1f,
            imageRect.top + imageRect.height() * 0.1f,
            imageRect.right - imageRect.width() * 0.1f,
            imageRect.bottom - imageRect.height() * 0.1f,
        )
        cropChanged = false
        onCropChanged?.invoke()
        invalidate()
    }

    fun hasCropChanged(): Boolean = cropChanged

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        updateImageRect()
        if (cropRect.isEmpty) {
            cropRect.set(
                imageRect.left + imageRect.width() * 0.1f,
                imageRect.top + imageRect.height() * 0.1f,
                imageRect.right - imageRect.width() * 0.1f,
                imageRect.bottom - imageRect.height() * 0.1f,
            )
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        updateImageRect()
        bitmap?.let { canvas.drawBitmap(it, null, imageRect, imagePaint) }
        canvas.save()
        canvas.clipRect(imageRect)
        canvas.drawRect(imageRect.left, imageRect.top, imageRect.right, cropRect.top, shadePaint)
        canvas.drawRect(imageRect.left, cropRect.bottom, imageRect.right, imageRect.bottom, shadePaint)
        canvas.drawRect(imageRect.left, cropRect.top, cropRect.left, cropRect.bottom, shadePaint)
        canvas.drawRect(cropRect.right, cropRect.top, imageRect.right, cropRect.bottom, shadePaint)
        canvas.restore()

        canvas.drawRect(cropRect, borderPaint)
        listOf(
            cropRect.left to cropRect.top,
            cropRect.right to cropRect.top,
            cropRect.left to cropRect.bottom,
            cropRect.right to cropRect.bottom,
            cropRect.centerX() to cropRect.top,
            cropRect.right to cropRect.centerY(),
            cropRect.centerX() to cropRect.bottom,
            cropRect.left to cropRect.centerY(),
        ).forEach { (x, y) -> canvas.drawCircle(x, y, handleRadius / 2f, handlePaint) }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x
                lastY = event.y
                dragMode = findDragMode(event.x, event.y)
                return dragMode != DragMode.NONE
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - lastX
                val dy = event.y - lastY
                when (dragMode) {
                    DragMode.MOVE -> moveCrop(dx, dy)
                    DragMode.TOP -> resizeTop(event.y)
                    DragMode.RIGHT -> resizeRight(event.x)
                    DragMode.BOTTOM -> resizeBottom(event.y)
                    DragMode.LEFT -> resizeLeft(event.x)
                    DragMode.TOP_LEFT -> resizeTopLeft(event.x, event.y)
                    DragMode.TOP_RIGHT -> resizeTopRight(event.x, event.y)
                    DragMode.BOTTOM_LEFT -> resizeBottomLeft(event.x, event.y)
                    DragMode.BOTTOM_RIGHT -> resizeBottomRight(event.x, event.y)
                    DragMode.NONE -> return false
                }
                lastX = event.x
                lastY = event.y
                cropChanged = true
                onCropChanged?.invoke()
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragMode = DragMode.NONE
                return true
            }
        }
        return true
    }

    fun croppedBitmap(): Bitmap? {
        val source = bitmap ?: return null
        if (imageRect.isEmpty || cropRect.isEmpty) return null
        val left = ((cropRect.left - imageRect.left) / imageRect.width() * source.width)
            .toInt().coerceIn(0, source.width - 1)
        val top = ((cropRect.top - imageRect.top) / imageRect.height() * source.height)
            .toInt().coerceIn(0, source.height - 1)
        val right = ((cropRect.right - imageRect.left) / imageRect.width() * source.width)
            .toInt().coerceIn(left + 1, source.width)
        val bottom = ((cropRect.bottom - imageRect.top) / imageRect.height() * source.height)
            .toInt().coerceIn(top + 1, source.height)
        val cropped = Bitmap.createBitmap(source, left, top, right - left, bottom - top)
        return if (cropped === source) source.copy(Bitmap.Config.ARGB_8888, false) else cropped
    }

    private fun updateImageRect() {
        val source = bitmap ?: return
        if (width == 0 || height == 0) return
        val scale = min(width.toFloat() / source.width, height.toFloat() / source.height)
        val displayWidth = source.width * scale
        val displayHeight = source.height * scale
        imageRect.set(
            (width - displayWidth) / 2f,
            (height - displayHeight) / 2f,
            (width + displayWidth) / 2f,
            (height + displayHeight) / 2f,
        )
        cropRect.set(
            cropRect.left.coerceIn(imageRect.left, imageRect.right),
            cropRect.top.coerceIn(imageRect.top, imageRect.bottom),
            cropRect.right.coerceIn(imageRect.left, imageRect.right),
            cropRect.bottom.coerceIn(imageRect.top, imageRect.bottom),
        )
    }

    private fun findDragMode(x: Float, y: Float): DragMode {
        if (near(x, cropRect.left) && near(y, cropRect.top)) return DragMode.TOP_LEFT
        if (near(x, cropRect.right) && near(y, cropRect.top)) return DragMode.TOP_RIGHT
        if (near(x, cropRect.left) && near(y, cropRect.bottom)) return DragMode.BOTTOM_LEFT
        if (near(x, cropRect.right) && near(y, cropRect.bottom)) return DragMode.BOTTOM_RIGHT
        if (near(y, cropRect.top)) return DragMode.TOP
        if (near(x, cropRect.right)) return DragMode.RIGHT
        if (near(y, cropRect.bottom)) return DragMode.BOTTOM
        if (near(x, cropRect.left)) return DragMode.LEFT
        return if (cropRect.contains(x, y)) DragMode.MOVE else DragMode.NONE
    }

    private fun near(first: Float, second: Float): Boolean = kotlin.math.abs(first - second) <= handleRadius

    private fun moveCrop(dx: Float, dy: Float) {
        val x = dx.coerceIn(imageRect.left - cropRect.left, imageRect.right - cropRect.right)
        val y = dy.coerceIn(imageRect.top - cropRect.top, imageRect.bottom - cropRect.bottom)
        cropRect.offset(x, y)
    }

    private fun resizeTopLeft(x: Float, y: Float) {
        cropRect.left = x.coerceIn(imageRect.left, cropRect.right - minimumCropSize)
        cropRect.top = y.coerceIn(imageRect.top, cropRect.bottom - minimumCropSize)
    }

    private fun resizeTopRight(x: Float, y: Float) {
        cropRect.right = x.coerceIn(cropRect.left + minimumCropSize, imageRect.right)
        cropRect.top = y.coerceIn(imageRect.top, cropRect.bottom - minimumCropSize)
    }

    private fun resizeBottomLeft(x: Float, y: Float) {
        cropRect.left = x.coerceIn(imageRect.left, cropRect.right - minimumCropSize)
        cropRect.bottom = y.coerceIn(cropRect.top + minimumCropSize, imageRect.bottom)
    }

    private fun resizeBottomRight(x: Float, y: Float) {
        cropRect.right = x.coerceIn(cropRect.left + minimumCropSize, imageRect.right)
        cropRect.bottom = y.coerceIn(cropRect.top + minimumCropSize, imageRect.bottom)
    }

    private fun resizeTop(y: Float) {
        cropRect.top = y.coerceIn(imageRect.top, cropRect.bottom - minimumCropSize)
    }

    private fun resizeRight(x: Float) {
        cropRect.right = x.coerceIn(cropRect.left + minimumCropSize, imageRect.right)
    }

    private fun resizeBottom(y: Float) {
        cropRect.bottom = y.coerceIn(cropRect.top + minimumCropSize, imageRect.bottom)
    }

    private fun resizeLeft(x: Float) {
        cropRect.left = x.coerceIn(imageRect.left, cropRect.right - minimumCropSize)
    }
}
