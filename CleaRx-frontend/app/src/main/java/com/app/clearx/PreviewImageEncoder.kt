package com.app.clearx

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File

internal object PreviewImageEncoder {
    /** Reads a sampled bitmap and re-encodes baseline JPEG; never rewrites the source. */
    fun encode(file: File): ByteArray {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val options = BitmapFactory.Options().apply {
            inSampleSize = PreviewSizing.sampleSize(bounds.outWidth, bounds.outHeight)
        }
        val bitmaps = mutableSetOf<Bitmap>()
        try {
            val decoded = checkNotNull(BitmapFactory.decodeFile(file.absolutePath, options)) {
                "Unable to decode camera preview"
            }.also { bitmaps.add(it) }
            val orientation = ExifInterface(file.absolutePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL,
            )
            val matrix = Matrix().apply {
                when (orientation) {
                    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
                    ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
                    ExifInterface.ORIENTATION_TRANSPOSE -> { setRotate(90f); postScale(-1f, 1f) }
                    ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
                    ExifInterface.ORIENTATION_TRANSVERSE -> { setRotate(270f); postScale(-1f, 1f) }
                    ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(270f)
                }
            }
            val oriented = if (matrix.isIdentity) decoded else Bitmap.createBitmap(
                decoded, 0, 0, decoded.width, decoded.height, matrix, true,
            ).also { bitmaps.add(it) }
            val (width, height) = PreviewSizing.dimensions(oriented.width, oriented.height)
            val preview = if (width == oriented.width && height == oriented.height) oriented else
                Bitmap.createScaledBitmap(oriented, width, height, true).also { bitmaps.add(it) }
            ByteArrayOutputStream().use { output ->
                for (quality in listOf(80, 65, 50, 35)) {
                    output.reset()
                    check(preview.compress(Bitmap.CompressFormat.JPEG, quality, output)) {
                        "Unable to encode camera preview"
                    }
                    if (output.size() <= PreviewSizing.MAX_BYTES) return output.toByteArray()
                }
            }
            error("Camera preview exceeds upload limit after compression")
        } finally {
            bitmaps.forEach { it.recycle() }
        }
    }
}
