package com.green3077.photoorganizer.data

import android.content.Context
import java.time.LocalTime

/**
 * 사용자가 정리 챌린지 알림을 켜고 끄거나 알림 시간을 직접 정할 수 있게 저장하는 설정.
 */
object ChallengeSettings {
    private const val PREFS_NAME = "challenge_settings"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_HOUR = "hour"
    private const val KEY_MINUTE = "minute"
    const val DEFAULT_HOUR = 9
    const val DEFAULT_MINUTE = 0

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun notifyTime(context: Context): LocalTime {
        val p = prefs(context)
        return LocalTime.of(p.getInt(KEY_HOUR, DEFAULT_HOUR), p.getInt(KEY_MINUTE, DEFAULT_MINUTE))
    }

    fun setNotifyTime(context: Context, time: LocalTime) {
        prefs(context).edit()
            .putInt(KEY_HOUR, time.hour)
            .putInt(KEY_MINUTE, time.minute)
            .apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
