package com.app.clearx

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class ImageInputPolicyTest {
    @Test fun copyKeepsExactBytesAtTheLimit() {
        val bytes = byteArrayOf(1, 2, 3, 4)
        val output = ByteArrayOutputStream()
        ImageInputPolicy.copyWithinLimit(ByteArrayInputStream(bytes), output, 4)
        assertArrayEquals(bytes, output.toByteArray())
    }

    @Test fun oversizedStreamsStopWithoutCopyingExcess() {
        val input = ByteArrayInputStream(ByteArray(100))
        val output = ByteArrayOutputStream()
        try {
            ImageInputPolicy.copyWithinLimit(input, output, 4)
            throw AssertionError("Expected size rejection")
        } catch (_: IllegalArgumentException) {
            assertEquals(95, input.available())
            assertEquals(0, output.size())
        }
    }

    @Test fun emptyStreamCanBeRejectedByImageDecodingLater() {
        val output = ByteArrayOutputStream()
        ImageInputPolicy.copyWithinLimit(ByteArrayInputStream(byteArrayOf()), output)
        assertEquals(0, output.size())
    }

    @Test fun permitsPixelLimitBoundary() {
        ImageInputPolicy.validateDimensions(6000, 4000)
    }

    @Test(expected = IllegalArgumentException::class)
    fun oversizedHeadersAreRejectedBeforeBitmapAllocation() {
        ImageInputPolicy.validateDimensions(6001, 4000)
    }

    @Test(expected = IllegalArgumentException::class)
    fun overflowSizedHeadersAreRejected() {
        ImageInputPolicy.validateDimensions(Int.MAX_VALUE, Int.MAX_VALUE)
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidHeadersAreRejected() {
        ImageInputPolicy.validateDimensions(-1, -1)
    }
}
