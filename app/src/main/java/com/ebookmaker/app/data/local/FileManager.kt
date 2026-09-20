package com.ebookmaker.app.data.local

import android.content.Context
import android.graphics.Bitmap
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class FileManager(private val context: Context) {

    private val baseDir: File
        get() = File(context.filesDir, "sessions").apply { if (!exists()) mkdirs() }

    fun getSessionDir(sessionId: Long): File {
        return File(baseDir, "session_$sessionId").apply { if (!exists()) mkdirs() }
    }

    fun getImagesDir(sessionId: Long): File {
        return File(getSessionDir(sessionId), "images").apply { if (!exists()) mkdirs() }
    }

    fun getThumbnailsDir(sessionId: Long): File {
        return File(getSessionDir(sessionId), "thumbnails").apply { if (!exists()) mkdirs() }
    }

    fun createTempCaptureFile(): File {
        val tempDir = File(context.cacheDir, "camera_captures").apply { if (!exists()) mkdirs() }
        return File.createTempFile("capture_${System.currentTimeMillis()}_", ".jpg", tempDir)
    }

    fun saveProcessedImage(sessionId: Long, index: Int, bitmap: Bitmap): File {
        val file = File(getImagesDir(sessionId), "page_${index}_${System.currentTimeMillis()}.jpg")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            out.flush()
        }
        return file
    }

    fun saveThumbnail(sessionId: Long, index: Int, bitmap: Bitmap): File {
        val file = File(getThumbnailsDir(sessionId), "thumb_${index}_${System.currentTimeMillis()}.jpg")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 75, out)
            out.flush()
        }
        return file
    }

    fun getPdfOutputFile(sessionId: Long, title: String): File {
        val sanitizedTitle = title.replace(Regex("[^a-zA-Z0-9가-힣_-]"), "_")
        return File(getSessionDir(sessionId), "${sanitizedTitle}_${sessionId}.pdf")
    }

    fun deleteFile(path: String?) {
        if (path.isNullOrEmpty()) return
        val file = File(path)
        if (file.exists()) {
            file.delete()
        }
    }

    fun deleteSessionFiles(sessionId: Long) {
        val dir = File(baseDir, "session_$sessionId")
        if (dir.exists()) {
            dir.deleteRecursively()
        }
    }
}
