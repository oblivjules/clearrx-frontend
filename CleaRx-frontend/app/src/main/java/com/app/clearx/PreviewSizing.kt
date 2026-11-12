package com.app.clearx

/** Preview quality checks need a thumbnail, not the full-resolution OCR image. */
internal object PreviewSizing {
    const val MAX_EDGE = 1280
    const val MAX_BYTES = 1024 * 1024

    fun sampleSize(width: Int, height: Int): Int {
        require(width > 0 && height > 0) { "Invalid preview image dimensions" }
        var sample = 1
        while (width / sample > MAX_EDGE * 2 || height / sample > MAX_EDGE * 2) {
            sample *= 2
        }
        return sample
    }

    fun dimensions(width: Int, height: Int): Pair<Int, Int> {
        require(width > 0 && height > 0) { "Invalid preview image dimensions" }
        val scale = minOf(1.0, MAX_EDGE.toDouble() / maxOf(width, height))
        return maxOf(1, (width * scale).toInt()) to maxOf(1, (height * scale).toInt())
    }
}
