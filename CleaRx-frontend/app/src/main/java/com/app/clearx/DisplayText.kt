package com.app.clearx

/** Hide OCR annotations only at display time; backend flags and raw data survive. */
internal fun withoutUncertaintyMarkers(value: String): String =
    value.replace(Regex("\\[\\s*(?:UNCERTAIN|CUTOFF)\\s*]", RegexOption.IGNORE_CASE), "").trim()
