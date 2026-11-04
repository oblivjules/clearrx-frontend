package com.app.clearx

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import org.opencv.android.OpenCVLoader
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfDouble
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class GatekeeperResult(
    val isSharp: Boolean,
    val hasEdges: Boolean,
    val lightingOk: Boolean,
    val hasText: Boolean,
    val isScreen: Boolean,
    val laplacianVariance: Double,
    val edgeDensity: Float,
    val cnr: Double,
    val localVariance: Double,
    val guidance: String?,
    val activeErrors: List<String> = emptyList(),
) {
    val passesGate: Boolean get() = isSharp && hasEdges && lightingOk && hasText && !isScreen
}

private class HysteresisGate(
    private val failThreshold: Int = 3,
    private val passThreshold: Int = 2,
    initialState: Boolean = true,
) {
    private var consecutiveFails  = 0
    private var consecutivePasses = 0
    private var stableState       = initialState

    fun feed(passes: Boolean): Boolean {
        if (passes) {
            consecutivePasses++
            consecutiveFails = 0
            if (consecutivePasses >= passThreshold) stableState = true
        } else {
            consecutiveFails++
            consecutivePasses = 0
            if (consecutiveFails >= failThreshold) stableState = false
        }
        return stableState
    }

    fun reset(resetState: Boolean = true) {
        consecutiveFails  = 0
        consecutivePasses = 0
        stableState       = resetState
    }
}

class GatekeeperAnalyzer(
    private val onResult: (GatekeeperResult) -> Unit,
) : ImageAnalysis.Analyzer {

    companion object {
        private const val TAG = "Gatekeeper"
        private const val BLUR_THRESHOLD         = 12.0   // unchanged (forgivable)

        private const val EDGE_DENSITY_THRESHOLD = 0.012f  // lowered: handwriting is sparser than print
        private const val CANNY_LOW              = 40.0
        private const val CANNY_HIGH             = 120.0
        private const val CNR_TOO_LOW            = 0.8
        private const val CNR_TOO_HIGH           = 45.0
        private const val MEAN_ABSOLUTE_DARK     = 18.0

        // Text detection — tuned for sparse handwriting on white paper
        private const val TEXT_LOCAL_VAR_THRESHOLD = 45.0
        private const val TEXT_EDGE_DENSITY_MIN    = 0.010f
        private const val TEXT_BLOCK_VAR_MIN       = 25.0
        private const val TEXT_UNIFORMITY_RATIO    = 0.10f

        // Screen detection – much stricter now
        private const val SCREEN_LAP_MIN         = 3000.0   // lowered from 5000 to catch more screens
        private const val SCREEN_EDGE_MIN         = 0.08f    // lowered from 0.11
        private const val SCREEN_CNR_MAX          = 2.0      // slightly increased from 1.8

        private const val ANALYSIS_SCALE         = 0.4
        private const val ROI_FRACTION           = 0.60
        private const val MIN_INTERVAL_MS        = 120L

        fun initOpenCV(): Boolean {
            val ok = OpenCVLoader.initLocal()
            if (ok) Log.d(TAG, "OpenCV native library loaded OK")
            else    Log.e(TAG, "OpenCV failed to load — gatekeeper will be disabled")
            return ok
        }
    }

    private val mainHandler    = Handler(Looper.getMainLooper())
    private var lastAnalysisMs = 0L

    private val sharpnessGate = HysteresisGate(failThreshold = 3, passThreshold = 2)
    private val edgeGate      = HysteresisGate(failThreshold = 3, passThreshold = 2)
    private val lightingGate  = HysteresisGate(failThreshold = 3, passThreshold = 2)
    private val screenGate    = HysteresisGate(failThreshold = 3, passThreshold = 2, initialState = false)
    private val textGate      = HysteresisGate(failThreshold = 3, passThreshold = 2, initialState = false)

    private var matsAllocated = false
    private lateinit var scaledMat: Mat
    private lateinit var roiMat:    Mat
    private lateinit var lapMat:    Mat
    private lateinit var cannyMat:  Mat

    private fun allocateMats() {
        if (matsAllocated) return
        try {
            scaledMat     = Mat()
            roiMat        = Mat()
            lapMat        = Mat()
            cannyMat      = Mat()
            matsAllocated = true
            Log.d(TAG, "Native Mats allocated")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Mat allocation failed — OpenCV native library not loaded. " +
                    "Gatekeeper will pass all frames silently. " +
                    "Ensure initOpenCV() returned true before constructing GatekeeperAnalyzer.", e)
        }
    }

    override fun analyze(image: ImageProxy) {
        val now = System.currentTimeMillis()
        if (now - lastAnalysisMs < MIN_INTERVAL_MS) {
            image.close()
            return
        }
        lastAnalysisMs = now

        try {
            allocateMats()
            if (!matsAllocated) {
                image.close()
                return
            }
            val result = runChecks(image)
            mainHandler.post { onResult(result) }
        } catch (e: Exception) {
            Log.w(TAG, "Frame analysis error: ${e.message}")
        } finally {
            image.close()
        }
    }

    private fun runChecks(image: ImageProxy): GatekeeperResult {
        val lumaBytes = extractLumaBytes(image)
        val fullW = image.width
        val fullH = image.height

        val fullMat = Mat(fullH, fullW, CvType.CV_8UC1)
        fullMat.put(0, 0, lumaBytes)

        val scW = (fullW * ANALYSIS_SCALE).roundToInt()
        val scH = (fullH * ANALYSIS_SCALE).roundToInt()
        Imgproc.resize(
            fullMat, scaledMat,
            Size(scW.toDouble(), scH.toDouble()),
            0.0, 0.0, Imgproc.INTER_LINEAR,
        )
        fullMat.release()

        val roiW    = (scW * ROI_FRACTION).roundToInt()
        val roiH    = (scH * ROI_FRACTION).roundToInt()
        val roiX    = (scW - roiW) / 2
        val roiY    = (scH - roiH) / 2
        val roiRect = org.opencv.core.Rect(
            max(0, roiX),
            max(0, roiY),
            min(roiW, scW - roiX),
            min(roiH, scH - roiY),
        )
        scaledMat.submat(roiRect).copyTo(roiMat)

        // Laplacian variance → sharpness
        Imgproc.Laplacian(roiMat, lapMat, CvType.CV_64F)
        val meanM   = MatOfDouble()
        val stdDevM = MatOfDouble()
        Core.meanStdDev(lapMat, meanM, stdDevM)
        val lapSd  = stdDevM.get(0, 0)[0]
        val lapVar = lapSd * lapSd
        meanM.release()
        stdDevM.release()

        val rawSharp  = lapVar >= BLUR_THRESHOLD
        val isSharp   = sharpnessGate.feed(rawSharp)

        // Edge density
        Imgproc.GaussianBlur(roiMat, cannyMat, Size(5.0, 5.0), 0.0)
        Imgproc.Canny(cannyMat, cannyMat, CANNY_LOW, CANNY_HIGH)
        val edgePixels  = Core.countNonZero(cannyMat).toLong()
        val totalPixels = (roiMat.rows() * roiMat.cols()).toLong()
        val edgeDensity = if (totalPixels > 0) edgePixels.toFloat() / totalPixels else 0f

        val rawEdges  = edgeDensity >= EDGE_DENSITY_THRESHOLD
        val hasEdges  = edgeGate.feed(rawEdges)

        // CNR & lighting
        val roiMeanM   = MatOfDouble()
        val roiStdDevM = MatOfDouble()
        Core.meanStdDev(roiMat, roiMeanM, roiStdDevM)
        val roiMean   = roiMeanM.get(0, 0)[0]
        val roiStdDev = roiStdDevM.get(0, 0)[0]
        roiMeanM.release()
        roiStdDevM.release()

        val cnr = if (roiStdDev > 0.5) roiMean / roiStdDev else 0.0

        val rawLighting = roiMean >= MEAN_ABSOLUTE_DARK && cnr in CNR_TOO_LOW..CNR_TOO_HIGH
        val lightingOk  = lightingGate.feed(rawLighting)

        // Screen detection – stricter thresholds
        val rawIsScreen = lapVar        >= SCREEN_LAP_MIN &&
                edgeDensity   >= SCREEN_EDGE_MIN &&
                cnr           <= SCREEN_CNR_MAX
        val isScreen = screenGate.feed(rawIsScreen)

        // Text presence (unchanged, forgiving)
        val blockSize      = 8
        val cols           = roiMat.cols()
        val rows           = roiMat.rows()
        var blockCount     = 0
        var activeBlocks   = 0
        var varSum         = 0.0
        val blockMeanM     = MatOfDouble()
        val blockStdM      = MatOfDouble()
        var bx = 0
        while (bx + blockSize <= cols) {
            var by = 0
            while (by + blockSize <= rows) {
                val blockRect = org.opencv.core.Rect(bx, by, blockSize, blockSize)
                val block = roiMat.submat(blockRect)
                Core.meanStdDev(block, blockMeanM, blockStdM)
                val bStd = blockStdM.get(0, 0)[0]
                val bVar = bStd * bStd
                varSum += bVar
                if (bVar >= TEXT_BLOCK_VAR_MIN) activeBlocks++
                blockCount++
                by += blockSize
            }
            bx += blockSize
        }
        blockMeanM.release()
        blockStdM.release()
        val meanLocalVariance  = if (blockCount > 0) varSum / blockCount else 0.0
        val uniformityRatio    = if (blockCount > 0) activeBlocks.toFloat() / blockCount else 0f

        val rawHasText = meanLocalVariance >= TEXT_LOCAL_VAR_THRESHOLD &&
                edgeDensity        >= TEXT_EDGE_DENSITY_MIN &&
                uniformityRatio    >= TEXT_UNIFORMITY_RATIO
        val hasText = textGate.feed(rawHasText)

        val activeErrors = buildList {
            if (isScreen) {
                add(UIStrings.NO_PHYSICAL_DOCUMENT)
            } else {
                when {
                    !lightingOk && (roiMean < MEAN_ABSOLUTE_DARK || cnr < CNR_TOO_LOW) ->
                        add(UIStrings.INCREASE_LIGHTING)
                    !lightingOk && cnr > CNR_TOO_HIGH ->
                        add(UIStrings.REDUCE_GLARE)
                }
                if (!isSharp)  add(UIStrings.HOLD_STEADY)
                if (!hasEdges && lightingOk && isSharp) add(UIStrings.NO_DOCUMENT)
                if (!hasText && lightingOk && isSharp && hasEdges) add(UIStrings.NO_TEXT)
            }
        }

        val guidance = activeErrors.firstOrNull()

        Log.d(TAG, "lap=${lapVar.toInt()} edges=${"%.3f".format(edgeDensity)} " +
                "mean=${roiMean.toInt()} cnr=${"%.2f".format(cnr)} " +
                "localVar=${"%.1f".format(meanLocalVariance)} uni=${"%.2f".format(uniformityRatio)} " +
                "screen=$isScreen(raw=$rawIsScreen) " +
                "sharp=$isSharp doc=$hasEdges light=$lightingOk text=$hasText(raw=$rawHasText)")

        return GatekeeperResult(
            isSharp           = isSharp,
            hasEdges          = hasEdges,
            lightingOk        = lightingOk,
            hasText           = hasText,
            isScreen          = isScreen,
            laplacianVariance = lapVar,
            edgeDensity       = edgeDensity,
            cnr               = cnr,
            localVariance     = meanLocalVariance,
            guidance          = guidance,
            activeErrors      = activeErrors,
        )
    }

    private fun extractLumaBytes(image: ImageProxy): ByteArray {
        val plane  = image.planes[0]
        val buffer = plane.buffer
        val stride = plane.rowStride
        val w      = image.width
        val h      = image.height

        return if (stride == w) {
            ByteArray(buffer.remaining()).also { buffer.get(it) }
        } else {
            val out = ByteArray(w * h)
            val row = ByteArray(stride)
            for (r in 0 until h) {
                buffer.get(row, 0, stride)
                row.copyInto(out, r * w, 0, w)
            }
            out
        }
    }

    fun release() {
        if (!matsAllocated) return
        scaledMat.release()
        roiMat.release()
        lapMat.release()
        cannyMat.release()
        matsAllocated = false
        sharpnessGate.reset()
        edgeGate.reset()
        lightingGate.reset()
        screenGate.reset(resetState = false)
        textGate.reset(resetState = false)
        Log.d(TAG, "Native Mats released")
    }
}