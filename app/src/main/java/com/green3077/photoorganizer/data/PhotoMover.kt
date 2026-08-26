package com.green3077.photoorganizer.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

    /** [relativePath]는 끝에 "/"가 붙은 MediaStore RELATIVE_PATH 형식(예: "DCIM/Camera/"). */
    fun applyMove(uris: List<Uri>, relativePath: String) {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.RELATIVE_PATH, relativePath)
        }
        uris.forEach { uri ->
            runCatching { context.contentResolver.update(uri, values, null, null) }
        }
    }

    /**
     * 안드로이드 갤러리(MediaStore)에 실제로 존재하는 폴더(RELATIVE_PATH) 목록을 전부 모아
     * 정렬해서 돌려준다. "이동" 시 새 폴더명을 직접 타이핑하는 대신 기존 갤러리 폴더 중에서
     * 고를 수 있게 하기 위함.
     */
    suspend fun listExistingFolders(): List<String> = withContext(Dispatchers.IO) {
        val paths = sortedSetOf<String>()
        listOf(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            .forEach { collection ->
                val projection = arrayOf(MediaStore.MediaColumns.RELATIVE_PATH)
                context.contentResolver.query(collection, projection, null, null, null)?.use { cursor ->
                    val col = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
                    while (cursor.moveToNext()) {
                        cursor.getString(col)?.takeIf { it.isNotBlank() }?.let { paths.add(it) }
                    }
                }
            }
        paths.toList()
    }

    companion object {
        const val DEFAULT_FOLDER = "정리한사진"

        fun relativePathFor(folderName: String): String {
            val safeName = folderName.trim().ifBlank { DEFAULT_FOLDER }
            return "${Environment.DIRECTORY_PICTURES}/$safeName/"
        }
    }
}
