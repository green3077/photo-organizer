package com.green3077.photoorganizer.notification

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration

/** 미디어 접근 권한이 확인된 화면에서 호출해, 휴지통 만료 알림을 하루 주기로 예약한다. */
object TrashWorkScheduler {
    private const val WORK_NAME = "trash_purge_check"

    fun scheduleIfNeeded(context: Context) {
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<TrashPurgeWorker>(Duration.ofDays(1)).build()
            )
    }
}
