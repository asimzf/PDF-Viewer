package com.asimzf.asimpdf.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

/** Bitmap loading that keeps large photos from blowing up the heap. */
object Images {

    fun decode(context: Context, uri: Uri, maxDimension: Int = 2400): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, bounds)
        } ?: return null
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxDimension)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, options)
        } ?: return null

        val rotation = context.contentResolver.openInputStream(uri)?.use { input ->
            runCatching { ExifInterface(input).rotationDegrees() }.getOrDefault(0)
        } ?: 0
        return if (rotation == 0) decoded else rotate(decoded, rotation)
    }

    fun scaleTo(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val largest = maxOf(bitmap.width, bitmap.height)
        if (largest <= maxDimension || largest == 0) return bitmap
        val factor = maxDimension.toFloat() / largest
        val width = (bitmap.width * factor).toInt().coerceAtLeast(1)
        val height = (bitmap.height * factor).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    fun save(bitmap: Bitmap, target: File, format: Bitmap.CompressFormat, quality: Int): File {
        FileOutputStream(target).use { output -> bitmap.compress(format, quality, output) }
        return target
    }

    private fun rotate(bitmap: Bitmap, degrees: Int): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return runCatching {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }.getOrDefault(bitmap)
    }

    private fun ExifInterface.rotationDegrees(): Int =
        when (getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }

    private fun sampleSize(width: Int, height: Int, maxDimension: Int): Int {
        var sample = 1
        var currentWidth = width
        var currentHeight = height
        while (currentWidth / 2 >= maxDimension || currentHeight / 2 >= maxDimension) {
            currentWidth /= 2
            currentHeight /= 2
            sample *= 2
        }
        return sample
    }
}
