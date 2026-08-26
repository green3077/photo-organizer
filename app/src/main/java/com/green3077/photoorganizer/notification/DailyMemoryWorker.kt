package com.green3077.photoorganizer.notification

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.green3077.photoorganizer.data.ChallengeSettings
import com.green3077.photoorganizer.data.PhotoRepository

class DailyMemoryWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!ChallengeSettings.isEnabled(applicationContext)) {
            return Result.success()
        }

        val mediaPermission =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_IMAGES
            else Manifest.permission.READ_EXTERNAL_STORAGE
        if (ContextCompat.checkSelfPermission(applicationContext, mediaPermission) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return Result.success()
        }

        val targetDate = ChallengeSettings.currentChallengeDate(applicationContext) ?: return Result.success()

        val repository = PhotoRepository(applicationContext)
        val allPhotos = repository.loadAllPhotos()
        val dayPhotos = repository.photosForExactDate(allPhotos, targetDate)

        if (dayPhotos.isNotEmpty()) {
            NotificationHelper.showDailyChallenge(applicationContext, targetDate, dayPhotos.size)
        }
        return Result.success()
    }
}
