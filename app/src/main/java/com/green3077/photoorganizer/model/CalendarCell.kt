package com.green3077.photoorganizer.model

import java.time.LocalDate

sealed class CalendarCell {
    /** 이전/다음 달로 삐져나온, 그려지지 않는 빈 칸. */
    object Blank : CalendarCell()

    data class Day(val date: LocalDate, val cover: Photo?, val count: Int) : CalendarCell()
}
