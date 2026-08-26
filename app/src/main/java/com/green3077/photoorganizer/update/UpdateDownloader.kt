package com.green3077.photoorganizer.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.green3077.photoorganizer.BuildConfig
import java.io.File

/**
 * 스토어를 거치지 않는 사이드로드 앱이라, GitHub Release의 APK를 DownloadManager로 받아
 * 시스템 패키지 설치 화면으로 바로 넘긴다("출처를 알 수 없는 앱" 허용은 설치 화면이 알아서 안내함).
 */
class UpdateDownloader(private val context: Context) {

    fun download(update: UpdateInfo, onFailure: () -> Unit) {
        val fileName = "photo-organizer-v${update.versionCode}.apk"
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(Uri.parse(update.downloadUrl))
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)

        val downloadId = downloadManager.enqueue(request)

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context, intent: Intent) {
                if (intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) != downloadId) return
                context.unregisterReceiver(this)

                val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)
                if (!file.exists()) {
                    onFailure()
                    return
                }
                installApk(file)
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_EXPORTED
        )
    }

    private fun installApk(file: File) {
        val uri = FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }
}
