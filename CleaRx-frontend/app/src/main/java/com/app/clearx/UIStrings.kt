package com.app.clearx

object UIStrings {

    // Camera screen — static
    const val ALIGN_INSTRUCTION = "Align prescription inside the frame"
    const val AUTO_CAPTURE_HINT = "Tap to capture"

    // Framing
    const val NO_DOCUMENT  = "Show all four edges of the document."
    const val MOVE_CLOSER  = "Move a little closer."
    const val FRAMING_OK   = "Framing looks good."

    // Text detection
    const val NO_TEXT              = "No text found — point at the prescription."
    const val NO_PHYSICAL_DOCUMENT = "Point camera at a physical prescription, not a screen."

    // Readability (preview-check)
    const val READING_TEXT      = "Reading text…"
    const val TEXT_CLEAR        = "Text looks clear — tap capture!"
    const val TEXT_MOSTLY_CLEAR = "Mostly readable — tap capture or hold steadier."
    const val TEXT_NOT_CLEAR    = "Text not clear enough — align and hold still."
    const val TEXT_TOO_CLOSE    = "Move closer for clearer text."

    // Lighting (CNR-aware)
    const val INCREASE_LIGHTING = "Too dark — move to a brighter spot."
    const val REDUCE_GLARE      = "Too bright — tilt slightly to reduce glare."
    const val LIGHTING_OK       = "Lighting looks good."

    // Focus / blur
    const val FOCUSING    = "Focusing… hold still."
    const val HOLD_STEADY = "Hold still a moment…"
    const val BLUR_OK     = "Image is sharp."

    // All checks pass
    const val READY_TO_CAPTURE = "Perfect! Tap capture."

    // Partial state
    const val PARTIAL_OK = "Looks usable — tap capture or adjust."

    // Frame resize hint
    const val FRAME_RESIZE_HINT = "Drag edges ↔ to resize · drag inside to move"

    // Server / network
    const val SERVER_OFFLINE  = "Server unreachable — tap to try anyway."
    const val SERVER_RETRYING = "Reconnecting…"

    // Post-capture processing
    const val CAPTURING = "Capturing…"
    const val ENHANCING = "Enhancing image…"

    // Result screen
    const val PERFECT_SCAN   = "✔ PERFECT SCAN"
    const val GOOD_FOR_OCR   = "✔ GOOD FOR OCR"
    const val LOW_LEGIBILITY = "⚠ LOW LEGIBILITY – RETAKE"
    const val BEST_EFFORT    = "⚠ BEST EFFORT"
    const val ENHANCED_LABEL = "Enhanced"
    const val ORIGINAL_LABEL = "Original"
    const val RETAKE         = "Retake"

    // Rejection messages
    const val REJECTED_DARK    = "Too dark — move to a brighter area and retake."
    const val REJECTED_BRIGHT  = "Too bright — reduce glare and retake."
    const val REJECTED_BLURRY  = "Too blurry — hold steady and retake."
    const val REJECTED_FRAMING = "Document edges unclear — show all four corners."

    // Errors
    const val ERROR_NETWORK     = "Network error — check server connection."
    const val ERROR_CAMERA      = "Camera not ready — please wait."
    const val ERROR_CAPTURE     = "Capture failed — please try again."
    const val ERROR_EMPTY       = "Empty response from server."
    const val NO_READABLE_TEXT  = "No readable text found — move closer and retake."

    // Permission dialog
    const val PERMISSION_TITLE   = "Camera Access Required"
    const val PERMISSION_MESSAGE = "CleaRx needs camera access to scan prescriptions."
    const val OPEN_SETTINGS      = "Open Settings"
    const val CANCEL             = "Cancel"

    fun trocrBadge(confidence: Int): String = when {
        confidence >= 95 -> "✔ $confidence% — TrOCR Ready"
        confidence >= 80 -> "⚠ $confidence% — Usable, may have errors"
        confidence >= 60 -> "⚠ $confidence% — Poor, retake recommended"
        else             -> "✖ $confidence% — Too low for TrOCR"
    }

    fun trocrSubLabel(confidence: Int, passesUsed: Int, ocrSource: String?): String {
        val passNote = if (passesUsed > 1) " (enhanced ×$passesUsed)" else ""
        val provider = when (ocrSource) {
            "claude-vision" -> "Claude Vision"
            "trocr-finetuned" -> "TrOCR"
            else -> "OCR"
        }
        return "$provider confidence: $confidence%$passNote"
    }

    fun qualityBadge(score: Int): String = when {
        score >= 75 -> PERFECT_SCAN
        score >= 50 -> GOOD_FOR_OCR
        else        -> LOW_LEGIBILITY
    }
    fun clarityLabel(score: Int): String = "Clarity: $score%"

    fun rejectionMessage(reason: String?): String = when (reason) {
        "too_dark"     -> REJECTED_DARK
        "too_bright"   -> REJECTED_BRIGHT
        "blurry"       -> REJECTED_BLURRY
        "poor_framing" -> REJECTED_FRAMING
        "no_text"      -> NO_READABLE_TEXT
        else           -> "Please retake the photo."
    }
}