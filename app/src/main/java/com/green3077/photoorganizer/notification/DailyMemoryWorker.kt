package com.green3077.photoorganizer.notification

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.green3077.photoorganizer.data.PhotoRepository
import java.time.LocalDate
import java.time.MonthDay

class DailyMemoryWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

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
        val today = MonthDay.now()
        val allPhotos = repository.loadAllPhotos()
        val pastYearPhotos = repository.photosForMonthDay(allPhotos, today)
            .filterKeys { it != LocalDate.now().year }

        if (pastYearPhotos.isNotEmpty()) {
            val totalCount = pastYearPhotos.values.sumOf { it.size }
            NotificationHelper.showDailyChallenge(applicationContext, today, pastYearPhotos.size, totalCount)
        }
        return Result.success()
    }
}
