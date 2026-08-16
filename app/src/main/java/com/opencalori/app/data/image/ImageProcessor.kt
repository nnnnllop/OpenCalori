package com.opencalori.app.data.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Base64
import androidx.exifinterface.media.ExifInterface
import com.opencalori.app.di.IoDispatcher
import com.opencalori.app.domain.model.PhotoQuality
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Prepares a photo for the vision API.
 *
 * Behind an interface so the scanner ViewModel stays free of android.graphics and can be
 * unit-tested on the JVM.
 */
interface ImageProcessor {
    /** Downscales, fixes EXIF rotation and returns (jpegBytes, base64). */
    suspend fun prepare(bytes: ByteArray, quality: PhotoQuality = PhotoQuality.HIGH): PreparedImage

    /** Same, for an image the user picked from the gallery. */
    suspend fun prepare(uri: Uri, quality: PhotoQuality = PhotoQuality.HIGH): PreparedImage
}

data class PreparedImage(
    val jpeg: ByteArray,
    val base64: String
) {
    override fun equals(other: Any?): Boolean =
        this === other || (other is PreparedImage && base64 == other.base64)

    override fun hashCode(): Int = base64.hashCode()
}

@Singleton
class AndroidImageProcessor @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher
) : ImageProcessor {

    override suspend fun prepare(bytes: ByteArray, quality: PhotoQuality): PreparedImage = withContext(io) {
        encode(decode(bytes), rotationOf(bytes), quality)
    }

    override suspend fun prepare(uri: Uri, quality: PhotoQuality): PreparedImage = withContext(io) {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("Не удалось прочитать изображение")
        encode(decode(bytes), rotationOf(bytes), quality)
    }

    private fun decode(bytes: ByteArray): Bitmap =
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: error("Не удалось декодировать изображение")

    /**
     * Phone cameras record orientation in EXIF rather than rotating pixels, and
     * BitmapFactory ignores it - without this the model gets a sideways plate.
     */
    private fun rotationOf(bytes: ByteArray): Int = runCatching {
        val exif = ByteArrayInputStream(bytes).use { ExifInterface(it) }
        when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
    }.getOrDefault(0)

    private fun encode(source: Bitmap, rotationDegrees: Int, quality: PhotoQuality): PreparedImage {
        val scale = maxOf(source.width, source.height).toFloat() / quality.maxDimension
        val scaled = if (scale > 1f) {
            Bitmap.createScaledBitmap(
                source,
                (source.width / scale).toInt().coerceAtLeast(1),
                (source.height / scale).toInt().coerceAtLeast(1),
                true
            )
        } else {
            source
        }

        val oriented = if (rotationDegrees == 0) {
            scaled
        } else {
            val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
            Bitmap.createBitmap(scaled, 0, 0, scaled.width, scaled.height, matrix, true)
        }

        val output = ByteArrayOutputStream()
        oriented.compress(Bitmap.CompressFormat.JPEG, quality.jpegQuality, output)
        val jpeg = output.toByteArray()

        return PreparedImage(jpeg = jpeg, base64 = Base64.encodeToString(jpeg, Base64.NO_WRAP))
    }
}
