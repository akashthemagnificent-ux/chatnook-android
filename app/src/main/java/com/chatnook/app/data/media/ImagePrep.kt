package com.chatnook.app.data.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Longest edge kept on upload; a phone screen never shows more, and it caps upload time. */
const val MAX_EDGE = 1600

/** Hard cap the server also enforces (bucket limit is 8 MB, this is the soft target). */
const val MAX_BYTES = 1_000_000

/** What the repository needs to upload and describe an image. */
class PreparedImage(
    val bytes: ByteArray,
    val width: Int,
    val height: Int,
    val filename: String,
) {
    val contentType: String = "image/jpeg"
    val extension: String = "jpg"
}

/**
 * Fits [width]x[height] inside a [maxEdge] square. Never upscales, never returns 0,
 * and keeps the aspect ratio of the long edge.
 */
fun fitInside(width: Int, height: Int, maxEdge: Int = MAX_EDGE): Pair<Int, Int> {
    if (width <= 0 || height <= 0) return 1 to 1
    val longest = maxOf(width, height)
    if (longest <= maxEdge) return width to height
    val scale = maxEdge.toDouble() / longest
    return maxOf(1, (width * scale).toInt()) to maxOf(1, (height * scale).toInt())
}

/**
 * Reads [uri], corrects EXIF rotation, scales to [MAX_EDGE] and re-encodes as JPEG under
 * [MAX_BYTES]. Returns null when the content cannot be decoded as an image, so the caller
 * can show "could not read that picture" instead of sending a broken message.
 */
suspend fun prepareImage(context: Context, uri: Uri): PreparedImage? = withContext(Dispatchers.IO) {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

    val sample = sampleSize(bounds.outWidth, bounds.outHeight, MAX_EDGE)
    val decoded = context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
    } ?: return@withContext null

    val rotated = rotateToExif(context, uri, decoded)
    val (w, h) = fitInside(rotated.width, rotated.height)
    val scaled = if (rotated.width == w && rotated.height == h) {
        rotated
    } else {
        Bitmap.createScaledBitmap(rotated, w, h, true).also { if (it !== rotated) rotated.recycle() }
    }

    var quality = 88
    var bytes = scaled.toJpeg(quality)
    // One pass at 88 usually lands well under the cap; only drop quality if it does not.
    while (bytes.size > MAX_BYTES && quality > 60) {
        quality -= 10
        bytes = scaled.toJpeg(quality)
    }
    val result = PreparedImage(bytes, scaled.width, scaled.height, "image-${System.currentTimeMillis()}.jpg")
    scaled.recycle()
    result
}

/** Power-of-two subsample that keeps the decode near [maxEdge] before scaling in memory. */
internal fun sampleSize(width: Int, height: Int, maxEdge: Int): Int {
    var sample = 1
    while (maxOf(width, height) / (sample * 2) >= maxEdge) sample *= 2
    return sample
}

private fun Bitmap.toJpeg(quality: Int): ByteArray =
    ByteArrayOutputStream().use { out ->
        compress(Bitmap.CompressFormat.JPEG, quality, out)
        out.toByteArray()
    }

private fun rotateToExif(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
    val degrees = context.contentResolver.openInputStream(uri)?.use { stream ->
        when (
            ExifInterface(stream).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        ) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
    } ?: 0f
    if (degrees == 0f) return bitmap
    val matrix = Matrix().apply { postRotate(degrees) }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        .also { if (it !== bitmap) bitmap.recycle() }
}
