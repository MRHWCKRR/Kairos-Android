package com.kairos.app.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.InputStream

object ImageUtils {
    fun compressImage(context: Context, uri: Uri, maxSize: Int = 1024 * 1024): ByteArray? {
        val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
        val originalBitmap = BitmapFactory.decodeStream(inputStream) ?: return null
        
        var quality = 100
        val outputStream = ByteArrayOutputStream()
        originalBitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
        
        while (outputStream.toByteArray().size > maxSize && quality > 10) {
            outputStream.reset()
            quality -= 10
            originalBitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
        }
        
        return outputStream.toByteArray()
    }

    fun convertToBase64(context: Context, uri: Uri, maxSize: Int = 800 * 1024): String? {
        val bytes = compressImage(context, uri, maxSize) ?: return null
        return "data:image/jpeg;base64," + android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
    }
}
