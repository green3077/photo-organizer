package com.green3077.photoorganizer.notification

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.green3077.photoorganizer.data.PhotoRepository
import com.green3077.photoorganizer.data.TrashTracker
import java.util.concurrent.TimeUnit

/**
 * 완전삭제는 시스템 확인 절차가 필요해 백그라운드에서 직접 처리할 수 없다.
 * 대신 하루에 한 번, 14일이 지난 사진이 휴지통에 있으면 앱을 열어달라는
 * 알림만 보낸다. 실제 완전삭제는 TrashActivity를 열 때 자동으로 시도된다.
 */
class TrashPurgeWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val mediaPermission =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_IMAGES
            else Manifest.permission.READ_EXTERNAL_STORAGE
        if (ContextCompat.checkSelfPermission(applicationContext, mediaPermission) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return Result.success()
        }

        val repository = PhotoRepository(applicationContext)
        val now = System.currentTimeMillis()
        val expiredCount = repository.loadTrashedPhotos().count { photo ->
            val trashedAt = TrashTracker.trashedAtOrNow(applicationContext, photo.id)
            TimeUnit.MILLISECONDS.toDays(now - trashedAt) >= TrashTracker.RETENTION_DAYS
        }

        if (expiredCount > 0) {
            NotificationHelper.showTrashReminder(applicationContext, expiredCount)
        }
        return Result.success()
    }
}
