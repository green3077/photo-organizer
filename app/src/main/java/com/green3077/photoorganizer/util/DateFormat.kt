package com.green3077.photoorganizer.util

import java.time.LocalDate
import java.time.MonthDay

object DateFormat {
    fun monthDayLabel(monthDay: MonthDay): String = "${monthDay.monthValue}월 ${monthDay.dayOfMonth}일"

    fun dayLabel(day: Int): String = "${day}일"

    fun fullDateLabel(date: LocalDate): String = "${date.year}년 ${date.monthValue}월 ${date.dayOfMonth}일"
}
