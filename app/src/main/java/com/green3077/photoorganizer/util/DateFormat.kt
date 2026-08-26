package com.green3077.photoorganizer.util

import java.time.LocalDate
import java.time.MonthDay

object DateFormat {
    fun monthDayLabel(monthDay: MonthDay): String = "${monthDay.monthValue}월 ${monthDay.dayOfMonth}일"

    fun dayLabel(day: Int): String = "${day}일"

    fun fullDateLabel(date: LocalDate): String = "${date.year}년 ${date.monthValue}월 ${date.dayOfMonth}일"

    fun fileSize(bytes: Long): String = when {
        bytes <= 0 -> "-"
        bytes < 1024 -> "${bytes}B"
        bytes < 1024 * 1024 -> "%.0fKB".format(bytes / 1024.0)
        bytes < 1024 * 1024 * 1024 -> "%.1fMB".format(bytes / (1024.0 * 1024.0))
        else -> "%.2fGB".format(bytes / (1024.0 * 1024.0 * 1024.0))
    }

    fun duration(ms: Long): String {
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%d:%02d".format(minutes, seconds)
    }
}
