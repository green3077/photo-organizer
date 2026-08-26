package com.green3077.photoorganizer.util

import java.time.LocalDate
import java.time.MonthDay

object DateFormat {
    fun monthDayLabel(monthDay: MonthDay): String = "${monthDay.monthValue}월 ${monthDay.dayOfMonth}일"

    fun dayLabel(day: Int): String = "${day}일"

    fun monthLabel(month: Int): String = "${month}월"

    fun fullDateLabel(date: LocalDate): String = "${date.year}년 ${date.monthValue}월 ${date.dayOfMonth}일"

    /** 여행 카드용 날짜 범위: 하루짜리는 "2026년 6월 6일", 여러 날이면 "2026년 6월 6일~6월 8일". */
    fun tripDateRangeLabel(start: LocalDate, end: LocalDate): String {
        val startLabel = fullDateLabel(start)
        if (start == end) return startLabel
        val endLabel = if (start.year == end.year) monthDayLabel(MonthDay.from(end)) else fullDateLabel(end)
        return "$startLabel~$endLabel"
    }

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
