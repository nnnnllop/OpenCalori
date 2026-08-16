package com.opencalori.app.testing

import android.net.Uri
import com.opencalori.app.data.image.ImageProcessor
import com.opencalori.app.data.image.PreparedImage
import com.opencalori.app.domain.model.PhotoQuality

/** Skips all the bitmap work; the ViewModel only cares about the base64 payload. */
class FakeImageProcessor(
    private val base64: String = "BASE64PHOTO"
) : ImageProcessor {

    var failure: Throwable? = null
    var calls = 0
        private set

    var lastQuality: PhotoQuality? = null
        private set

    override suspend fun prepare(bytes: ByteArray, quality: PhotoQuality): PreparedImage {
        lastQuality = quality
        calls++
        failure?.let { throw it }
        return PreparedImage(jpeg = bytes, base64 = base64)
    }

    override suspend fun prepare(uri: Uri, quality: PhotoQuality): PreparedImage {
        lastQuality = quality
        calls++
        failure?.let { throw it }
        return PreparedImage(jpeg = ByteArray(4), base64 = base64)
    }
}
