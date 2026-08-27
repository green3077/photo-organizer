package com.green3077.photoorganizer.data

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest

/**
 * 사진을 바로 완전삭제하지 않고 시스템 휴지통으로 보내거나(trashed) 복원한다.
 * 실제 완전삭제는 [PhotoDeleter]가 맡는다.
 */
class PhotoTrasher(
    private val context: Context,
    private val launcher: ActivityResultLauncher<IntentSenderRequest>
) {
    fun requestTrash(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val pendingIntent = MediaStore.createTrashRequest(context.contentResolver, uris, true)
        launcher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
    }

    fun requestRestore(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val pendingIntent = MediaStore.createTrashRequest(context.contentResolver, uris, false)
        launcher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
    }
}
