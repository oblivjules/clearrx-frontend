package com.app.clearx.network

import android.graphics.PointF
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

data class FeedbackDetail(
    val lighting: String = "good",
    val blur: String = "sharp",
    val framing: String = "unknown",
)

data class PreviewCheckResponse(
    val readable: Boolean = false,
    val score: Int = 0,
    val instruction: String = "",
    val text_type: String = "printed",
    val handwriting_confidence: Float = 0f,
)

data class QuadPointsResponse(
    val top_left: PointF?,
    val top_right: PointF?,
    val bottom_right: PointF?,
    val bottom_left: PointF?
)

data class ValidateCaptureResponse(
    val is_usable: Boolean = false,
    val rejection_reason: String? = null,
    val feedback: FeedbackDetail = FeedbackDetail(),
    val instruction: String = "",
    val quality_score: Int = 0,
    val document_quad: QuadPointsResponse? = null
)

data class ParsedPrescription(
    val brand_name: String? = null,
    val drug_name: String? = null,
    val dosage: String? = null,
    val frequency: String? = null,
    val route_of_administration: String? = null,
    val quantity: String? = null,
    val duration: String? = null,
    val meal_relation: String? = null,
    val dispense_quantity: String? = null,
)

data class ProcessImageResponse(
    val status: String = "error",
    val detection: String = "full_frame",
    val reason: String? = null,
    val message: String? = null,
    val enhanced_image: String? = null,
    val original_image: String? = null,
    val quality_score: Int = 0,
    val processing_time_ms: Float = 0f,
    val trocr_confidence: Int = 0,
    val trocr_label: String? = null,
    val passes_used: Int = 1,
    val extracted_text: String? = null,
    val drug_name: String? = null,             // generic (top‑level)
    val brand_name: String? = null,            // brand (top‑level)
    val drug_confidence: Float = 0f,
    val drug_matched: Boolean = false,
    val medicines: List<ParsedPrescription> = emptyList(),
)

data class CaptureImageResponse(
    val status: String = "error",
    val image_id: String? = null,
    val reason: String? = null,
)

data class CaptureSessionResponse(
    val status: String = "error",
    val session_id: String? = null,
    val reason: String? = null,
)

interface ApiService {

    @Multipart
    @POST("capture-session")
    suspend fun createCaptureSession(
        @Part userId: MultipartBody.Part,
    ): Response<CaptureSessionResponse>

    @Multipart
    @POST("preview-check")
    suspend fun previewCheck(
        @Part file: MultipartBody.Part,
    ): Response<PreviewCheckResponse>

    @Multipart
    @POST("validate-capture")
    suspend fun validateCapture(
        @Part file: MultipartBody.Part,
        @Part imageId: MultipartBody.Part? = null,
    ): Response<ValidateCaptureResponse>

    @Multipart
    @POST("capture-image")
    suspend fun captureImage(
        @Part file: MultipartBody.Part,
        @Part sessionId: MultipartBody.Part,
        @Part captureAttemptNumber: MultipartBody.Part? = null,
    ): Response<CaptureImageResponse>

    @Multipart
    @POST("capture-session/close")
    suspend fun closeCaptureSession(
        @Part sessionId: MultipartBody.Part,
        @Part sessionStatus: MultipartBody.Part,
    ): Response<Unit>

    @Multipart
    @POST("process-image")
    suspend fun processImage(
        @Part file: MultipartBody.Part,
        @Part imageId: MultipartBody.Part? = null,
    ): Response<ProcessImageResponse>
}