package com.green3077.photoorganizer.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.green3077.photoorganizer.R
import com.green3077.photoorganizer.TrashActivity

object NotificationHelper {
    private const val TRASH_CHANNEL_ID = "trash_reminder"
    private const val TRASH_NOTIFICATION_ID = 1002

    private fun ensureTrashChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                TRASH_CHANNEL_ID,
                context.getString(R.string.title_trash),
                NotificationManager.IMPORTANCE_DEFAULT
            )
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    /**
     * 백그라운드 워커는 시스템 완전삭제 확인 절차를 직접 띄울 수 없으므로, 14일이
     * 지난 사진이 있을 때 앱을 열어달라는 알림만 보낸다. 실제 완전삭제는
     * TrashActivity를 열었을 때 자동으로 시도된다.
     */
    fun showTrashReminder(context: Context, expiredCount: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        ensureTrashChannel(context)

        val intent = Intent(context, TrashActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, TRASH_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.trash_reminder_title))
            .setContentText(context.getString(R.string.trash_reminder_body, expiredCount))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(TRASH_NOTIFICATION_ID, notification)
    }
}
