package com.green3077.photoorganizer.update

import android.content.Context
import android.content.Intent
import android.os.Environment
import androidx.core.content.FileProvider
import com.green3077.photoorganizer.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * 스토어를 거치지 않는 사이드로드 앱이라, GitHub Release의 APK를 받아 시스템 패키지 설치
 * 화면으로 바로 넘긴다("출처를 알 수 없는 앱" 허용은 설치 화면이 알아서 안내함).
 *
 * 시스템 DownloadManager는 일부 기기(특히 삼성 계열)에서 백그라운드 다운로드가 조용히
 * 막히거나 실패해도 사용자에게 아무 표시가 없는 경우가 있어, 대신 앱이 직접 HTTP로
 * 받아서 실패하면 바로 예외로 드러나게 했다.
 */
class UpdateDownloader(private val context: Context) {

    suspend fun download(update: UpdateInfo): File = withContext(Dispatchers.IO) {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
        val file = File(dir, "photo-organizer-v${update.versionCode}.apk")

        val connection = URL(update.downloadUrl).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.connect()

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                throw IOException("다운로드 실패: HTTP $responseCode")
            }

            connection.inputStream.use { input ->
                FileOutputStream(file).use { output -> input.copyTo(output) }
            }
        } finally {
            connection.disconnect()
        }

        file
    }

    fun installApk(file: File) {
        val uri = FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }
}
