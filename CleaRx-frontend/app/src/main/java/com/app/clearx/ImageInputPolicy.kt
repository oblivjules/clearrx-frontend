package com.app.clearx

import java.io.InputStream
import java.io.OutputStream

internal object ImageInputPolicy {
    const val MAX_BYTES = 10L * 1024 * 1024
    const val MAX_PIXELS = 24_000_000L

    fun validateDimensions(width: Int, height: Int) {
        require(width > 0 && height > 0 && width.toLong() * height <= MAX_PIXELS) {
            "Choose a valid image of at most 24 megapixels."
        }
    }

    fun copyWithinLimit(input: InputStream, output: OutputStream, limit: Long = MAX_BYTES) {
        require(limit > 0)
        val buffer = ByteArray(8192)
        var total = 0L
        while (true) {
            val count = input.read(buffer, 0, minOf(buffer.size.toLong(), limit - total + 1).toInt())
            if (count < 0) return
            total += count
            require(total <= limit) { "The image must be 10 MB or smaller." }
            output.write(buffer, 0, count)
        }
    }
}
