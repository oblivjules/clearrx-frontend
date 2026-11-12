package com.app.clearx

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

class UploadLoaderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val photoPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val photoFrame = RectF()
    private val mountainPath = Path()
    private val cornerPath = Path()
    private val checkPath = Path()
    private var selectedState = false

    init {
        isClickable = true
        contentDescription = context.getString(R.string.select_file)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val size = minOf(width, height).toFloat()
        val inset = size * 0.08f
        val radius = size * 0.1f
        photoFrame.set(inset, inset, size - inset, size - inset)

        photoPaint.style = Paint.Style.FILL
        photoPaint.color = Color.WHITE
        canvas.drawRoundRect(photoFrame, radius, radius, photoPaint)

        canvas.save()
        canvas.clipRect(photoFrame)

        photoPaint.color = Color.rgb(255, 147, 113)
        mountainPath.reset()
        mountainPath.moveTo(photoFrame.left, photoFrame.bottom)
        mountainPath.lineTo(size * 0.43f, size * 0.43f)
        mountainPath.lineTo(photoFrame.right, photoFrame.bottom)
        mountainPath.close()
        canvas.drawPath(mountainPath, photoPaint)

        photoPaint.color = Color.rgb(255, 61, 0)
        cornerPath.reset()
        cornerPath.moveTo(size * 0.58f, photoFrame.bottom)
        cornerPath.lineTo(photoFrame.right, size * 0.42f)
        cornerPath.lineTo(photoFrame.right, photoFrame.bottom)
        cornerPath.close()
        canvas.drawPath(cornerPath, photoPaint)

        photoPaint.color = Color.rgb(255, 61, 0)
        canvas.drawCircle(size * 0.36f, size * 0.3f, size * 0.14f, photoPaint)
        canvas.restore()

        if (selectedState) {
            photoPaint.color = Color.rgb(46, 125, 50)
            canvas.drawCircle(size * 0.78f, size * 0.78f, size * 0.17f, photoPaint)
            photoPaint.color = Color.WHITE
            photoPaint.style = Paint.Style.STROKE
            photoPaint.strokeWidth = size * 0.055f
            photoPaint.strokeCap = Paint.Cap.ROUND
            photoPaint.strokeJoin = Paint.Join.ROUND
            checkPath.reset()
            checkPath.moveTo(size * 0.7f, size * 0.78f)
            checkPath.lineTo(size * 0.76f, size * 0.84f)
            checkPath.lineTo(size * 0.87f, size * 0.71f)
            canvas.drawPath(checkPath, photoPaint)
        }
    }

    fun setSelectedState(selected: Boolean) {
        selectedState = selected
        contentDescription = context.getString(
            if (selected) R.string.image_selected else R.string.select_file
        )
        invalidate()
    }
}
