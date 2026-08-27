package com.aiplacementos.app.alarm

import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmSchedulerTest {

    private fun calendarAt(hour: Int, minute: Int): Calendar =
        Calendar.getInstance().apply {
            set(2026, Calendar.AUGUST, 25, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }

    private fun resultOf(millis: Long): Calendar = Calendar.getInstance().apply { timeInMillis = millis }

    @Test
    fun `one-shot alarm later today fires today at the exact time`() {
        val now = calendarAt(hour = 6, minute = 0)

        val trigger = AlarmScheduler.nextTriggerMillis(hour = 7, minute = 30, repeatDays = "", now = now)
        val result = resultOf(trigger)

        assertEquals(now.get(Calendar.DAY_OF_YEAR), result.get(Calendar.DAY_OF_YEAR))
        assertEquals(7, result.get(Calendar.HOUR_OF_DAY))
        assertEquals(30, result.get(Calendar.MINUTE))
    }

    @Test
    fun `one-shot alarm whose time already passed today fires tomorrow`() {
        val now = calendarAt(hour = 8, minute = 0)

        val trigger = AlarmScheduler.nextTriggerMillis(hour = 7, minute = 0, repeatDays = "", now = now)
        val result = resultOf(trigger)

        assertEquals(now.get(Calendar.DAY_OF_YEAR) + 1, result.get(Calendar.DAY_OF_YEAR))
        assertEquals(7, result.get(Calendar.HOUR_OF_DAY))
    }

    @Test
    fun `repeating alarm fires today when today is an eligible day and the time has not passed`() {
        val now = calendarAt(hour = 6, minute = 0)
        val todayDayOfWeek = now.get(Calendar.DAY_OF_WEEK)

        val trigger = AlarmScheduler.nextTriggerMillis(
            hour = 7, minute = 0, repeatDays = "$todayDayOfWeek", now = now,
        )
        val result = resultOf(trigger)

        assertEquals(now.get(Calendar.DAY_OF_YEAR), result.get(Calendar.DAY_OF_YEAR))
    }

    @Test
    fun `repeating alarm skips to the next eligible day once today's time has passed`() {
        val now = calendarAt(hour = 8, minute = 0)
        val todayDayOfWeek = now.get(Calendar.DAY_OF_WEEK)

        val trigger = AlarmScheduler.nextTriggerMillis(
            hour = 7, minute = 0, repeatDays = "$todayDayOfWeek", now = now,
        )
        val result = resultOf(trigger)

        // Today is eligible but the time already passed -- must roll to next week's
        // occurrence (+7 days), never fire in the past and never fire on an off day.
        assertEquals(now.get(Calendar.DAY_OF_YEAR) + 7, result.get(Calendar.DAY_OF_YEAR))
        assertEquals(todayDayOfWeek, result.get(Calendar.DAY_OF_WEEK))
    }

    @Test
    fun `repeating alarm picks the nearest eligible day among several`() {
        val now = calendarAt(hour = 6, minute = 0)
        val today = now.get(Calendar.DAY_OF_WEEK)
        val twoDaysFromNow = ((today - Calendar.SUNDAY + 2) % 7) + Calendar.SUNDAY

        // Today is deliberately excluded so the result must land exactly 2 days out, not today.
        val trigger = AlarmScheduler.nextTriggerMillis(
            hour = 7, minute = 0, repeatDays = "$twoDaysFromNow", now = now,
        )
        val result = resultOf(trigger)

        assertTrue(result.timeInMillis > now.timeInMillis)
        assertEquals(now.get(Calendar.DAY_OF_YEAR) + 2, result.get(Calendar.DAY_OF_YEAR))
    }
}
