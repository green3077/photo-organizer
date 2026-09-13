package com.green3077.filefriend.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/** 받은 파일을 기기의 다운로드 > downloadfriend 폴더에 저장한다. */
object DownloadUtils {
    private const val SUBFOLDER = "downloadfriend"

    fun saveToDownloads(
        context: Context,
        fileName: String,
        input: InputStream,
        onProgress: (Long) -> Unit
    ): Uri {
        val safeName = sanitizeFileName(fileName)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveViaMediaStore(context, safeName, input, onProgress)
        } else {
            saveViaLegacyFile(context, safeName, input, onProgress)
        }
    }

    private fun saveViaMediaStore(
        context: Context,
        fileName: String,
        input: InputStream,
        onProgress: (Long) -> Unit
    ): Uri {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, guessMimeType(fileName))
            put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$SUBFOLDER")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val itemUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("다운로드 위치를 만들 수 없습니다")

        resolver.openOutputStream(itemUri)?.use { out ->
            copyStream(input, out, onProgress)
        } ?: throw IOException("파일을 쓸 수 없습니다")

        values.clear()
        values.put(MediaStore.Downloads.IS_PENDING, 0)
        resolver.update(itemUri, values, null, null)
        return itemUri
    }

    private fun saveViaLegacyFile(
        context: Context,
        fileName: String,
        input: InputStream,
        onProgress: (Long) -> Unit
    ): Uri {
        @Suppress("DEPRECATION")
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val targetDir = File(downloadsDir, SUBFOLDER)
        if (!targetDir.exists() && !targetDir.mkdirs()) {
            throw IOException("다운로드 폴더를 만들 수 없습니다")
        }
        val targetFile = uniqueFile(targetDir, fileName)
        FileOutputStream(targetFile).use { out ->
            copyStream(input, out, onProgress)
        }
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", targetFile)
    }

    private fun uniqueFile(dir: File, fileName: String): File {
        var candidate = File(dir, fileName)
        if (!candidate.exists()) return candidate
        val dot = fileName.lastIndexOf('.')
        val base = if (dot > 0) fileName.substring(0, dot) else fileName
        val ext = if (dot > 0) fileName.substring(dot) else ""
        var index = 1
        while (candidate.exists()) {
            candidate = File(dir, "$base(${index})$ext")
            index++
        }
        return candidate
    }

    private fun copyStream(input: InputStream, output: OutputStream, onProgress: (Long) -> Unit) {
        val buffer = ByteArray(8 * 1024)
        var bytesCopied = 0L
        var read: Int
        while (input.read(buffer).also { read = it } >= 0) {
            output.write(buffer, 0, read)
            bytesCopied += read
            onProgress(bytesCopied)
        }
    }

    private fun sanitizeFileName(fileName: String): String {
        val cleaned = fileName.replace(Regex("[/\\\\:*?\"<>|]"), "_").trim()
        return cleaned.ifEmpty { "file_${System.currentTimeMillis()}" }
    }

    private fun guessMimeType(fileName: String): String {
        val extension = fileName.substringAfterLast('.', "")
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase())
            ?: "application/octet-stream"
    }
}
