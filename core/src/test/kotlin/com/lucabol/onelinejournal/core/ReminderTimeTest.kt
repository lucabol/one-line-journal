package com.lucabol.onelinejournal.core

import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue
import org.junit.Test

class ReminderTimeTest {
    @Test fun `next reminder stays at local time across spring daylight saving`() {
        val now = ZonedDateTime.of(2026, 3, 28, 22, 0, 0, 0, ZoneId.of("Europe/Rome"))
        val next = ReminderTime.next(now, 21 * 60)
        assertEquals(29, next.dayOfMonth)
        assertEquals(21, next.hour)
        assertEquals(22, Duration.between(now, next).toHours())
    }

    @Test fun `next reminder stays at local time across autumn daylight saving`() {
        val now = ZonedDateTime.of(2026, 10, 24, 22, 0, 0, 0, ZoneId.of("Europe/Rome"))
        val next = ReminderTime.next(now, 21 * 60)
        assertEquals(25, next.dayOfMonth)
        assertEquals(24, Duration.between(now, next).toHours())
    }

    @Test fun `nonexistent local clock time shifts forward through the gap`() {
        val now = ZonedDateTime.of(2026, 3, 28, 22, 0, 0, 0, ZoneId.of("Europe/Rome"))
        val next = ReminderTime.next(now, 150)
        assertEquals(3, next.hour)
        assertEquals(30, next.minute)
    }

    @Test fun `exact trigger time schedules tomorrow not an immediate duplicate`() {
        val now = ZonedDateTime.parse("2026-10-19T21:00:00+02:00[Europe/Rome]")
        assertEquals(now.toLocalDate().plusDays(1), ReminderTime.next(now, 1260).toLocalDate())
    }

    @Test fun `invalid time is rejected and returned time is always future`() {
        assertFails { ReminderTime.next(ZonedDateTime.now(), 1440) }
        assertTrue(ReminderTime.next(ZonedDateTime.now(), 0).isAfter(ZonedDateTime.now()))
    }
}
