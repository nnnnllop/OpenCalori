package com.opencalori.app.testing

import android.net.Uri
import com.opencalori.app.data.image.ImageProcessor
import com.opencalori.app.data.image.PreparedImage

/** Skips all the bitmap work; the ViewModel only cares about the base64 payload. */
class FakeImageProcessor(
    private val base64: String = "BASE64PHOTO"
) : ImageProcessor {

    var failure: Throwable? = null
    var calls = 0
        private set

    override suspend fun prepare(bytes: ByteArray): PreparedImage {
        calls++
        failure?.let { throw it }
        return PreparedImage(jpeg = bytes, base64 = base64)
    }

    override suspend fun prepare(uri: Uri): PreparedImage {
        calls++
        failure?.let { throw it }
        return PreparedImage(jpeg = ByteArray(4), base64 = base64)
    }
}
