package com.app.clearx

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View

data class QuadPoints(
    val topLeft: PointF?,
    val topRight: PointF?,
    val bottomRight: PointF?,
    val bottomLeft: PointF?
) {
    fun isWorkable(): Boolean = listOfNotNull(topLeft, topRight, bottomRight, bottomLeft).size >= 3
    fun isValid(): Boolean = listOf(topLeft, topRight, bottomRight, bottomLeft).all { it != null }
    fun toNormalizedArray(): List<PointF> = listOfNotNull(topLeft, topRight, bottomRight, bottomLeft)
}

class FramingOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    enum class FramingState {
        IDLE, CONNECTING, BAD, PARTIAL, GOOD
    }

    private var state = FramingState.IDLE
    private var cornerAlpha = 1.0f
    private var isLocked = false
    internal val guideRect = RectF()   // changed to internal for access, but we'll keep private set
    private var frameInitialised = false

    private val MIN_FRAME_FRACTION = 0.25f
    private val MAX_FRAME_FRACTION = 1.0f
    private val minFrameHeightPx = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, 40f, context.resources.displayMetrics
    )

    // Touch slop to prevent shaky movements (8dp)
    private val touchSlopPx = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, 8f, context.resources.displayMetrics
    )
    private var downX = 0f
    private var downY = 0f
    private var pendingGestureMode = GestureMode.NONE
    private var pendingCorner = -1
    private var pendingEdge = Edge.NONE

    // Styling
    private val cornerLength = 80f
    private val cornerThickness = 12f
    private val cornerRadius = 30f

    private val shadowPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = cornerThickness + 4f
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
        color = Color.parseColor("#22000000")
    }

    private val cornerPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = cornerThickness
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
        color = Color.WHITE
    }

    private val connectionPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
        color = Color.parseColor("#60FFFFFF")
        pathEffect = DashPathEffect(floatArrayOf(30f, 30f), 0f)
    }

    private val dimPaint = Paint().apply {
        color = Color.argb(160, 0, 0, 0)
    }

    private val clearPaint = Paint().apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }

    private val handlePaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
        color = Color.argb(180, 255, 255, 255)
    }
    private val handleRadius = 14f

    private val stateColors = mapOf(
        FramingState.IDLE to Color.parseColor("#FFFFFF"),
        FramingState.CONNECTING to Color.parseColor("#9CA3AF"),
        FramingState.BAD to Color.parseColor("#EF4444"),
        FramingState.PARTIAL to Color.parseColor("#FBBF24"),
        FramingState.GOOD to Color.parseColor("#22C55E"),
    )

    private var colorAnimator: ValueAnimator? = null

    private var gestureMode = GestureMode.NONE
    private enum class GestureMode { NONE, DRAG, PINCH, EDGE_RESIZE, CORNER_RESIZE }

    private var dragLastX = 0f
    private var dragLastY = 0f

    private var pinchLastSpan = 0f
    private var pinchLastCx = 0f
    private var pinchLastCy = 0f

    private enum class Edge { NONE, LEFT, TOP, RIGHT, BOTTOM }
    private var activeEdge = Edge.NONE
    private var activeCorner = -1
    private val cornerHitSlop = 52f
    private val edgeHitSlop = 48f

    private val edgeHandleRadius = 20f
    private val edgeHandlePaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
        color = Color.argb(210, 255, 255, 255)
    }
    private val edgeHandleRingPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        isAntiAlias = true
        color = Color.argb(120, 0, 0, 0)
    }

    // --- New: Listener for frame changes ---
    private var frameChangeListener: ((RectF) -> Unit)? = null

    fun setOnFrameChangedListener(listener: (RectF) -> Unit) {
        frameChangeListener = listener
    }

    private fun notifyFrameChanged() {
        frameChangeListener?.invoke(RectF(guideRect))
    }

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
        isClickable = true
        isFocusable = true
    }

    fun setLocked(locked: Boolean) { isLocked = locked }

    fun setState(newState: FramingState) {
        if (state == newState) return
        val from = stateColors[state] ?: Color.WHITE
        val to = stateColors[newState] ?: Color.WHITE
        animateColor(from, to)
        state = newState
        invalidate()
    }

    fun updateDocumentQuad(points: QuadPoints?) {
        if (isLocked) return
        cornerAlpha = 1.0f
        invalidate()
    }

    fun isAcceptableForBackend(points: QuadPoints): Boolean {
        val p = points.toNormalizedArray()
        if (p.size < 3) return false
        val minX = p.minOf { it.x }
        val maxX = p.maxOf { it.x }
        val docWidth = maxX - minX
        return docWidth > 0.30f && minX > 0.01f && maxX < 0.99f
    }

    fun getFrameRectNormalized(): RectF {
        if (width == 0 || height == 0) return RectF(0.1f, 0.1f, 0.9f, 0.9f)
        return RectF(
            guideRect.left / width,
            guideRect.top / height,
            guideRect.right / width,
            guideRect.bottom / height
        )
    }

    /** Get current frame rectangle in absolute pixel coordinates. */
    fun getAbsoluteFrameRect(): RectF = RectF(guideRect)

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (!frameInitialised && w > 0 && h > 0) {
            resetFrameToDefault(w.toFloat(), h.toFloat())
            frameInitialised = true
        } else {
            // Also notify after size change (e.g. orientation)
            notifyFrameChanged()
        }
    }

    private fun resetFrameToDefault(w: Float, h: Float) {
        val frameW = w * 0.92f
        val frameH = frameW * 0.75f
        val left = (w - frameW) / 2f
        val top = (h - frameH) / 2f
        guideRect.set(left, top, left + frameW, top + frameH)
        notifyFrameChanged()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isLocked) return false

        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                pendingGestureMode = detectIntent(downX, downY)
                true
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount >= 2) {
                    pendingGestureMode = GestureMode.NONE
                    gestureMode = GestureMode.PINCH
                    pinchLastSpan = getSpan(event)
                    pinchLastCx = (event.getX(0) + event.getX(1)) / 2f
                    pinchLastCy = (event.getY(0) + event.getY(1)) / 2f
                }
                true
            }
            MotionEvent.ACTION_MOVE -> {
                if (gestureMode == GestureMode.PINCH && event.pointerCount >= 2) {
                    handlePinch(event)
                    true
                } else if (gestureMode == GestureMode.NONE && pendingGestureMode != GestureMode.NONE) {
                    val dx = event.x - downX
                    val dy = event.y - downY
                    if (hypot(dx, dy) >= touchSlopPx) {
                        gestureMode = pendingGestureMode
                        when (gestureMode) {
                            GestureMode.CORNER_RESIZE -> {
                                activeCorner = pendingCorner
                                dragLastX = event.x
                                dragLastY = event.y
                            }
                            GestureMode.EDGE_RESIZE -> {
                                activeEdge = pendingEdge
                                dragLastX = event.x
                                dragLastY = event.y
                            }
                            GestureMode.DRAG -> {
                                dragLastX = event.x
                                dragLastY = event.y
                            }
                            else -> {}
                        }
                    }
                    true
                } else {
                    when (gestureMode) {
                        GestureMode.CORNER_RESIZE -> handleCornerResize(event)
                        GestureMode.EDGE_RESIZE -> handleEdgeResize(event)
                        GestureMode.DRAG -> handleDrag(event)
                        else -> {}
                    }
                    true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                gestureMode = GestureMode.NONE
                activeEdge = Edge.NONE
                activeCorner = -1
                pendingGestureMode = GestureMode.NONE
                pendingCorner = -1
                pendingEdge = Edge.NONE
                true
            }
            else -> false
        }
    }

    private fun detectIntent(x: Float, y: Float): GestureMode {
        val corner = hitTestCorner(x, y)
        if (corner >= 0) {
            pendingCorner = corner
            return GestureMode.CORNER_RESIZE
        }
        val edge = hitTestEdge(x, y)
        if (edge != Edge.NONE) {
            pendingEdge = edge
            return GestureMode.EDGE_RESIZE
        }
        return if (guideRect.contains(x, y)) GestureMode.DRAG else GestureMode.NONE
    }

    private fun hitTestCorner(x: Float, y: Float): Int {
        val corners = arrayOf(
            guideRect.left to guideRect.top,
            guideRect.right to guideRect.top,
            guideRect.right to guideRect.bottom,
            guideRect.left to guideRect.bottom
        )
        for ((i, c) in corners.withIndex()) {
            if (hypot(x - c.first, y - c.second) <= cornerHitSlop) return i
        }
        return -1
    }

    private fun hitTestEdge(x: Float, y: Float): Edge {
        val left = guideRect.left
        val top = guideRect.top
        val right = guideRect.right
        val bottom = guideRect.bottom
        val slop = edgeHitSlop

        if (x in left - slop..left + slop && y in top - slop..bottom + slop) return Edge.LEFT
        if (x in right - slop..right + slop && y in top - slop..bottom + slop) return Edge.RIGHT
        if (y in top - slop..top + slop && x in left - slop..right + slop) return Edge.TOP
        if (y in bottom - slop..bottom + slop && x in left - slop..right + slop) return Edge.BOTTOM

        return Edge.NONE
    }

    private fun hypot(dx: Float, dy: Float) = Math.hypot(dx.toDouble(), dy.toDouble()).toFloat()

    private fun handleCornerResize(event: MotionEvent) {
        val dx = event.x - dragLastX
        val dy = event.y - dragLastY
        dragLastX = event.x
        dragLastY = event.y

        val vw = width.toFloat()
        val vh = height.toFloat()
        val minW = vw * MIN_FRAME_FRACTION
        val minH = minFrameHeightPx

        val newRect = RectF(guideRect)
        when (activeCorner) {
            0 -> {
                newRect.left = (newRect.left + dx).coerceIn(0f, newRect.right - minW)
                newRect.top = (newRect.top + dy).coerceIn(0f, newRect.bottom - minH)
            }
            1 -> {
                newRect.right = (newRect.right + dx).coerceIn(newRect.left + minW, vw)
                newRect.top = (newRect.top + dy).coerceIn(0f, newRect.bottom - minH)
            }
            2 -> {
                newRect.right = (newRect.right + dx).coerceIn(newRect.left + minW, vw)
                newRect.bottom = (newRect.bottom + dy).coerceIn(newRect.top + minH, vh)
            }
            3 -> {
                newRect.left = (newRect.left + dx).coerceIn(0f, newRect.right - minW)
                newRect.bottom = (newRect.bottom + dy).coerceIn(newRect.top + minH, vh)
            }
            else -> return
        }
        guideRect.set(newRect)
        invalidate()
        notifyFrameChanged()
    }

    private fun handleEdgeResize(event: MotionEvent) {
        val dx = event.x - dragLastX
        val dy = event.y - dragLastY
        dragLastX = event.x
        dragLastY = event.y

        val vw = width.toFloat()
        val vh = height.toFloat()
        val minW = vw * MIN_FRAME_FRACTION
        val maxW = vw * MAX_FRAME_FRACTION
        val minH = minFrameHeightPx
        val maxH = vh * MAX_FRAME_FRACTION

        val newRect = RectF(guideRect)
        when (activeEdge) {
            Edge.LEFT -> newRect.left = (guideRect.left + dx).coerceIn(0f, guideRect.right - minW)
            Edge.RIGHT -> newRect.right = (guideRect.right + dx).coerceIn(guideRect.left + minW, vw)
            Edge.TOP -> newRect.top = (guideRect.top + dy).coerceIn(0f, guideRect.bottom - minH)
            Edge.BOTTOM -> newRect.bottom = (guideRect.bottom + dy).coerceIn(guideRect.top + minH, vh)
            Edge.NONE -> return
        }

        if (newRect.width() > maxW) {
            when (activeEdge) {
                Edge.LEFT -> newRect.left = newRect.right - maxW
                Edge.RIGHT -> newRect.right = newRect.left + maxW
                else -> {}
            }
        }
        if (newRect.height() > maxH) {
            when (activeEdge) {
                Edge.TOP -> newRect.top = newRect.bottom - maxH
                Edge.BOTTOM -> newRect.bottom = newRect.top + maxH
                else -> {}
            }
        }

        guideRect.set(newRect)
        invalidate()
        notifyFrameChanged()
    }

    private fun handleDrag(event: MotionEvent) {
        val dx = event.x - dragLastX
        val dy = event.y - dragLastY
        dragLastX = event.x
        dragLastY = event.y

        val newRect = RectF(
            guideRect.left + dx,
            guideRect.top + dy,
            guideRect.right + dx,
            guideRect.bottom + dy
        )
        clampAndApply(newRect)
    }

    private fun handlePinch(event: MotionEvent) {
        val span = getSpan(event)
        if (pinchLastSpan == 0f) {
            pinchLastSpan = span
            return
        }

        val scale = span / pinchLastSpan
        pinchLastSpan = span

        val cx = (event.getX(0) + event.getX(1)) / 2f
        val cy = (event.getY(0) + event.getY(1)) / 2f

        val newW = guideRect.width() * scale
        val newH = guideRect.height() * scale

        val newRect = RectF(
            cx - newW / 2f,
            cy - newH / 2f,
            cx + newW / 2f,
            cy + newH / 2f
        )
        clampAndApply(newRect)
    }

    private fun clampAndApply(rect: RectF) {
        val vw = width.toFloat()
        val vh = height.toFloat()

        val minW = vw * MIN_FRAME_FRACTION
        val maxW = vw * MAX_FRAME_FRACTION
        val minH = minFrameHeightPx
        val maxH = vh * MAX_FRAME_FRACTION

        val cx = rect.centerX().coerceIn(0f, vw)
        val cy = rect.centerY().coerceIn(0f, vh)
        val halfW = (rect.width() / 2f).coerceIn(minW / 2f, maxW / 2f)
        val halfH = (rect.height() / 2f).coerceIn(minH / 2f, maxH / 2f)

        val proposed = RectF(cx - halfW, cy - halfH, cx + halfW, cy + halfH)

        val shiftX = when {
            proposed.left < 0f -> -proposed.left
            proposed.right > vw -> vw - proposed.right
            else -> 0f
        }
        val shiftY = when {
            proposed.top < 0f -> -proposed.top
            proposed.bottom > vh -> vh - proposed.bottom
            else -> 0f
        }
        proposed.offset(shiftX, shiftY)

        guideRect.set(proposed)
        invalidate()
        notifyFrameChanged()
    }

    private fun getSpan(event: MotionEvent): Float {
        val dx = event.getX(0) - event.getX(1)
        val dy = event.getY(0) - event.getY(1)
        return hypot(dx, dy)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawDimming(canvas)
        drawStaticGuide(canvas)
        drawHandleHints(canvas)
    }

    private fun drawDimming(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dimPaint)
        canvas.drawRoundRect(guideRect, cornerRadius, cornerRadius, clearPaint)
    }

    private fun drawStaticGuide(canvas: Canvas) {
        cornerPaint.alpha = 255
        val l = guideRect.left
        val t = guideRect.top
        val r = guideRect.right
        val b = guideRect.bottom

        drawTopLeftCorner(canvas, l, t)
        drawTopRightCorner(canvas, r, t)
        drawBottomRightCorner(canvas, r, b)
        drawBottomLeftCorner(canvas, l, b)
    }

    private fun drawHandleHints(canvas: Canvas) {
        val l = guideRect.left
        val t = guideRect.top
        val r = guideRect.right
        val b = guideRect.bottom
        val cx = guideRect.centerX()
        val cy = guideRect.centerY()

        canvas.drawCircle(l, t, handleRadius, handlePaint)
        canvas.drawCircle(r, t, handleRadius, handlePaint)
        canvas.drawCircle(r, b, handleRadius, handlePaint)
        canvas.drawCircle(l, b, handleRadius, handlePaint)

        canvas.drawCircle(cx, t, edgeHandleRadius, edgeHandlePaint)
        canvas.drawCircle(r, cy, edgeHandleRadius, edgeHandlePaint)
        canvas.drawCircle(cx, b, edgeHandleRadius, edgeHandlePaint)
        canvas.drawCircle(l, cy, edgeHandleRadius, edgeHandlePaint)

        canvas.drawCircle(cx, t, edgeHandleRadius, edgeHandleRingPaint)
        canvas.drawCircle(r, cy, edgeHandleRadius, edgeHandleRingPaint)
        canvas.drawCircle(cx, b, edgeHandleRadius, edgeHandleRingPaint)
        canvas.drawCircle(l, cy, edgeHandleRadius, edgeHandleRingPaint)

        drawEdgeTick(canvas, cx, t, false)
        drawEdgeTick(canvas, r, cy, true)
        drawEdgeTick(canvas, cx, b, false)
        drawEdgeTick(canvas, l, cy, true)
    }

    private fun drawEdgeTick(canvas: Canvas, cx: Float, cy: Float, isVertical: Boolean) {
        val tickLen = edgeHandleRadius * 0.55f
        val tickPaint = edgeHandleRingPaint
        if (isVertical) {
            canvas.drawLine(cx - tickLen, cy, cx + tickLen, cy, tickPaint)
        } else {
            canvas.drawLine(cx, cy - tickLen, cx, cy + tickLen, tickPaint)
        }
    }

    private fun drawTopLeftCorner(canvas: Canvas, x: Float, y: Float) {
        canvas.drawLine(x, y, x + cornerLength, y, shadowPaint)
        canvas.drawLine(x, y, x, y + cornerLength, shadowPaint)
        canvas.drawLine(x, y, x + cornerLength, y, cornerPaint)
        canvas.drawLine(x, y, x, y + cornerLength, cornerPaint)
    }

    private fun drawTopRightCorner(canvas: Canvas, x: Float, y: Float) {
        canvas.drawLine(x, y, x - cornerLength, y, shadowPaint)
        canvas.drawLine(x, y, x, y + cornerLength, shadowPaint)
        canvas.drawLine(x, y, x - cornerLength, y, cornerPaint)
        canvas.drawLine(x, y, x, y + cornerLength, cornerPaint)
    }

    private fun drawBottomRightCorner(canvas: Canvas, x: Float, y: Float) {
        canvas.drawLine(x, y, x - cornerLength, y, shadowPaint)
        canvas.drawLine(x, y, x, y - cornerLength, shadowPaint)
        canvas.drawLine(x, y, x - cornerLength, y, cornerPaint)
        canvas.drawLine(x, y, x, y - cornerLength, cornerPaint)
    }

    private fun drawBottomLeftCorner(canvas: Canvas, x: Float, y: Float) {
        canvas.drawLine(x, y, x + cornerLength, y, shadowPaint)
        canvas.drawLine(x, y, x, y - cornerLength, shadowPaint)
        canvas.drawLine(x, y, x + cornerLength, y, cornerPaint)
        canvas.drawLine(x, y, x, y - cornerLength, cornerPaint)
    }

    private fun animateColor(from: Int, to: Int) {
        colorAnimator?.cancel()
        colorAnimator = ValueAnimator.ofObject(ArgbEvaluator(), from, to).apply {
            duration = 300
            addUpdateListener { anim ->
                val color = anim.animatedValue as Int
                cornerPaint.color = color
                handlePaint.color = color
                edgeHandlePaint.color = color
                invalidate()
            }
            start()
        }
    }
}