package com.app.clearx

/** Public recognition options accepted by the migrated backend. */
enum class RecognitionModel(val apiId: String, val displayName: String) {
    PRIMARY("clearx-trocr", "CleaRx TrOCR"),
    EXTERNAL("claude-sonnet-5-5", "Claude Sonnet 5.5");

    companion object {
        fun fromSavedId(value: String?): RecognitionModel =
            values().firstOrNull { it.apiId == value } ?: PRIMARY
    }
}
