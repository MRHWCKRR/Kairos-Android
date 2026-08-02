package com.kairos.app.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.InputStream

object ImageUtils {
    /**
     * Compresses an image from a Uri into a JPEG byte array.
     */
    fun compressImage(context: Context, uri: Uri, maxSize: Int = 500 * 1024): ByteArray? {
        return try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            val originalBitmap = BitmapFactory.decodeStream(inputStream) ?: return null
            
            var quality = 90
            var outputStream = ByteArrayOutputStream()
            originalBitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
            
            // Loop until the size is under maxSize or quality is too low
            while (outputStream.toByteArray().size > maxSize && quality > 10) {
                outputStream = ByteArrayOutputStream()
                quality -= 10
                originalBitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
            }
            
            outputStream.toByteArray()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Converts an image to a Base64 string safe for Firestore storage (under 1MB limit).
     */
    fun convertToBase64(context: Context, uri: Uri): String? {
        // Use 500KB as max binary size to ensure Base64 (~1.33x) stays well under 1MB Firestore limit
        val bytes = compressImage(context, uri, 500 * 1024) ?: return null
        return android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
    }

    /**
     * Decodes a Base64 string back into a ByteArray for Coil/Bitmap loading.
     */
    fun decodeBase64(base64: String): ByteArray? {
        return try {
            // Clean prefix if it exists
            val cleanBase64 = if (base64.contains(",")) {
                base64.substringAfter(",")
            } else {
                base64
            }
            android.util.Base64.decode(cleanBase64, android.util.Base64.DEFAULT)
        } catch (e: Exception) {
            null
        }
    }
}
