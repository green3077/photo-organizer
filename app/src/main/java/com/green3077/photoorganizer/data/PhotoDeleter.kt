package com.green3077.photoorganizer.data

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest

/**
 * "삭제"를 눌러도 바로 영구 삭제하지 않고 휴지통으로 옮긴다(createTrashRequest).
 * 시스템 갤러리/파일 앱의 휴지통에서 그대로 보이고 복구할 수 있으며, 일정 기간 뒤
 * 시스템이 알아서 영구 삭제한다. 휴지통에 들어간 항목은 MediaStore 기본 조회에서
 * 자동으로 빠지므로, 앱 목록에서도 바로 사라진다.
 */
class PhotoDeleter(
    private val context: Context,
    private val launcher: ActivityResultLauncher<IntentSenderRequest>
) {
    fun requestDelete(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val pendingIntent = MediaStore.createTrashRequest(context.contentResolver, uris, true)
        launcher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
    }
}
