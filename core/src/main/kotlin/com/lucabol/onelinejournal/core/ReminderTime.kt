package com.lucabol.onelinejournal.core

import java.time.LocalTime
import java.time.ZonedDateTime

object ReminderTime {
    fun next(now: ZonedDateTime, minuteOfDay: Int): ZonedDateTime {
        require(minuteOfDay in 0..1439) { "Invalid reminder time." }
        val time = LocalTime.of(minuteOfDay / 60, minuteOfDay % 60)
        val candidate = now.toLocalDate().atTime(time).atZone(now.zone)
        return if (candidate.isAfter(now)) candidate
        else now.toLocalDate().plusDays(1).atTime(time).atZone(now.zone)
    }
}
