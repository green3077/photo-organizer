package com.green3077.photoorganizer.notification

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.green3077.photoorganizer.data.ChallengeSettings
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

object WorkScheduler {
    private const val WORK_NAME = "daily_memory_challenge"

    /**
     * 앱 시작 시 호출. 이미 예약돼 있으면 그대로 두고(KEEP), 꺼져 있으면 예약을 취소한다.
     * 매번 시각을 다시 계산해 덮어쓰지 않으므로 앱을 자주 켜도 기존 주기가 흐트러지지 않는다.
     */
    fun scheduleIfNeeded(context: Context) {
        if (!ChallengeSettings.isEnabled(context)) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            return
        }
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, buildRequest(context))
    }

    /**
     * 설정 화면에서 알림 on/off나 시간을 바꿨을 때 호출. 즉시 새 설정으로 다시 예약한다.
     */
    fun applySettings(context: Context) {
        if (!ChallengeSettings.isEnabled(context)) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            return
        }
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, buildRequest(context))
    }

    private fun buildRequest(context: Context): PeriodicWorkRequest {
        val notifyTime = ChallengeSettings.notifyTime(context)
        return PeriodicWorkRequestBuilder<DailyMemoryWorker>(Duration.ofDays(1))
            .setInitialDelay(computeInitialDelayMillis(notifyTime), TimeUnit.MILLISECONDS)
            .build()
    }

    private fun computeInitialDelayMillis(notifyTime: LocalTime): Long {
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(notifyTime)
        if (!next.isAfter(now)) next = next.plusDays(1)
        return Duration.between(now, next).toMillis()
    }
}
