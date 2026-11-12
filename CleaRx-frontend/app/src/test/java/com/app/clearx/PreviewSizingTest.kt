package com.app.clearx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewSizingTest {
    @Test fun highResolutionPhotoBecomesSmallPreview() {
        assertEquals(1280 to 960, PreviewSizing.dimensions(8000, 6000))
        assertEquals(4, PreviewSizing.sampleSize(8000, 6000))
    }

    @Test fun portraitAndRotatedPhotosRemainWithinPixelLimit() {
        for ((width, height) in listOf(6000 to 8000, 12000 to 9000, 4000 to 3000)) {
            val (w, h) = PreviewSizing.dimensions(width, height)
            assertTrue(w <= 1280 && h <= 1280)
            assertTrue(w.toLong() * h <= 1280L * 1280)
        }
    }

    @Test fun smallPhotosAreNotEnlarged() {
        assertEquals(640 to 480, PreviewSizing.dimensions(640, 480))
        assertEquals(1, PreviewSizing.sampleSize(640, 480))
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidCameraHeadersAreRejected() {
        PreviewSizing.sampleSize(-1, -1)
    }
}
