package com.green3077.photoorganizer.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest

/**
 * 다른 앱(카메라 등)이 만든 미디어를 옮기려면 Android 11+ scoped storage에서
 * MediaStore.createWriteRequest로 먼저 시스템 동의를 받아야 한다. 동의가 떨어지면
 * (런처 콜백에서) [applyMove]로 실제 RELATIVE_PATH를 바꿔 갤러리 폴더를 이동시킨다.
 */
class PhotoMover(
    private val context: Context,
    private val launcher: ActivityResultLauncher<IntentSenderRequest>
) {
    fun requestMove(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val pendingIntent = MediaStore.createWriteRequest(context.contentResolver, uris)
        launcher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
    }

    fun applyMove(uris: List<Uri>, folderName: String) {
        val safeName = folderName.trim().ifBlank { DEFAULT_FOLDER }
        val relativePath = "${Environment.DIRECTORY_PICTURES}/$safeName/"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.RELATIVE_PATH, relativePath)
        }
        uris.forEach { uri ->
            runCatching { context.contentResolver.update(uri, values, null, null) }
        }
    }

    companion object {
        const val DEFAULT_FOLDER = "정리한사진"
    }
}
