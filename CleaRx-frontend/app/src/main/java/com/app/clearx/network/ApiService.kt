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

data class FileUploadResponse(
    val status: String = "error",
    val image_id: String? = null,
    val attempt_number: Int = 0,
    val content_type: String? = null,
    val size_bytes: Long = 0,
)

data class CerResult(
    val cer: Float = 0f,
    val cer_percent: Float = 0f,
    val substitutions: Int = 0,
    val deletions: Int = 0,
    val insertions: Int = 0,
    val edit_distance: Int = 0,
    val ref_length: Int = 0,
    val hyp_length: Int = 0,
    val passes_objective: Boolean = false,
)

data class ProcessImageResponse(
    val ocr_confidence: Float? = null,
    val ocr_confidence_basis: String? = null,
    val name_similarity: Float? = null,
    val review_required: Boolean = false,
    val persistence_status: String? = null,
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
    val ocr_source: String? = null,
    val passes_used: Int = 1,
    val extracted_text: String? = null,
    val drug_name: String? = null,             // generic (top‑level)
    val brand_name: String? = null,            // brand (top‑level)
    val drug_confidence: Float = 0f,
    val drug_matched: Boolean = false,
    val medicines: List<ParsedPrescription> = emptyList(),
    // QA/testing only — present when a tester supplied ground_truth for this capture.
    val cer: CerResult? = null,
)

interface ApiService {
    @Multipart
    @POST("validate-upload")
    suspend fun validateUpload(
        @Part file: MultipartBody.Part,
    ): Response<ValidateCaptureResponse>

    @Multipart
    @POST("upload-file")
    suspend fun uploadFile(
        @Part file: MultipartBody.Part,
        @Part sessionId: MultipartBody.Part,
    ): Response<FileUploadResponse>



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

    // groundTruth is QA/testing-only: a tester types the exact prescription
    // text so the backend can compute CER (Objective 2.2) against TrOCR's
    // raw output. Omitted (null) for ordinary captures.
    @Multipart
    @POST("process-image")
    suspend fun processImage(
        @Part file: MultipartBody.Part,
        @Part imageId: MultipartBody.Part? = null,
        @Part groundTruth: MultipartBody.Part? = null,
        @Part model: MultipartBody.Part? = null,
    ): Response<ProcessImageResponse>
}
