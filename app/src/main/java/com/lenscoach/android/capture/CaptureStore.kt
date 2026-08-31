package com.lenscoach.android.capture

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CaptureStore {
    fun save(context: Context, bitmap: Bitmap, styleLabel: String): Uri? {
        val name = "LENSCOACH_${styleLabel}_${
            SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        }.jpg"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/LensCoach")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        return try {
            resolver.openOutputStream(uri)?.use { stream ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 92, stream)) {
                    throw IllegalStateException("compress failed")
                }
            } ?: throw IllegalStateException("no output stream")
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            uri
        } catch (error: Exception) {
            resolver.delete(uri, null, null)
            null
        }
    }
}
