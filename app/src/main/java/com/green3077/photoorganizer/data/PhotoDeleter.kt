package com.green3077.photoorganizer.data

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest

/**
 * 휴지통에 들어간 사진을 완전삭제한다([PhotoTrasher]가 담당하는 휴지통 이동과는 별개).
 * [TrashActivity]에서 14일 경과 항목 자동 정리 또는 사용자의 직접 완전삭제/휴지통 비우기 시 쓰인다.
 */
class PhotoDeleter(
    private val context: Context,
    private val launcher: ActivityResultLauncher<IntentSenderRequest>
) {
    fun requestDelete(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val pendingIntent = MediaStore.createDeleteRequest(context.contentResolver, uris)
        launcher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
    }
}
