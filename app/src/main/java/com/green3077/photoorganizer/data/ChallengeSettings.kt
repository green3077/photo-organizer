package com.green3077.photoorganizer.data

import android.content.Context
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

/**
 * 정리 챌린지 설정과 진행 상태를 저장한다.
 *
 * 사용자가 연도/월을 한 번 고르면 그 달 1일부터 순서대로 하루씩 "오늘의 챌린지"가 된다.
 * [currentDay]는 완료 버튼을 눌러야만 앞으로 나아가는 커서라서, 하루이틀 건너뛰거나
 * 앱을 한참 안 열어도 항상 멈춰있던 그 날짜부터 다시 보여준다(달력 날짜가 아니라
 * 사용자의 진행 상태를 기준으로 삼기 때문).
 */
object ChallengeSettings {
    private const val PREFS_NAME = "challenge_settings"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_HOUR = "hour"
    private const val KEY_MINUTE = "minute"
    private const val KEY_YEAR = "challenge_year"
    private const val KEY_MONTH = "challenge_month"
    private const val KEY_DAY = "challenge_day"
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

    fun isConfigured(context: Context): Boolean {
        val p = prefs(context)
        return p.contains(KEY_YEAR) && p.contains(KEY_MONTH)
    }

    fun setChallengeTarget(context: Context, year: Int, month: Int) {
        prefs(context).edit()
            .putInt(KEY_YEAR, year)
            .putInt(KEY_MONTH, month)
            .putInt(KEY_DAY, 1)
            .apply()
    }

    fun clearChallengeTarget(context: Context) {
        prefs(context).edit()
            .remove(KEY_YEAR)
            .remove(KEY_MONTH)
            .remove(KEY_DAY)
            .apply()
    }

    /** 완료 버튼을 눌렀을 때 호출 — 다음 날짜로 커서를 하루 옮긴다. */
    fun advanceDay(context: Context) {
        val p = prefs(context)
        val day = p.getInt(KEY_DAY, 1)
        p.edit().putInt(KEY_DAY, day + 1).apply()
    }

    /** 설정된 달의 마지막 날까지 다 완료했으면 null (더 이상 오늘의 챌린지가 없음). */
    fun currentChallengeDate(context: Context): LocalDate? {
        val p = prefs(context)
        if (!isConfigured(context)) return null
        val year = p.getInt(KEY_YEAR, 0)
        val month = p.getInt(KEY_MONTH, 0)
        val day = p.getInt(KEY_DAY, 1)
        val lastDay = YearMonth.of(year, month).lengthOfMonth()
        if (day > lastDay) return null
        return LocalDate.of(year, month, day)
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
