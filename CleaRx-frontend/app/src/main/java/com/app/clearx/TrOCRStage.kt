package com.app.clearx

enum class TrOCRStage(val label: String, val displayIndex: Int) {
    INIT("Initializing TrOCR model...", 1),
    PREPROCESS("Preprocessing image...", 2),
    FEATURE_EXTRACTION("Running feature extraction...", 3),
    DECODING("Decoding token sequences...", 4),
    POSTPROCESS("Post-processing output...", 5),
    DONE("Done.", 5)
}
