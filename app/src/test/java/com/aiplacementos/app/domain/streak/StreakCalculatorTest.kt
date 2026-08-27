package com.aiplacementos.app.domain.streak

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class StreakCalculatorTest {
    private val today = LocalDate.of(2026, 1, 15)

    @Test
    fun `no activity yields a zero streak`() {
        assertEquals(0, currentStreakDays(emptyList(), today))
    }

    @Test
    fun `a single day of activity today yields a streak of one`() {
        assertEquals(1, currentStreakDays(listOf(today), today))
    }

    @Test
    fun `consecutive days ending today count all of them`() {
        val dates = listOf(today, today.minusDays(1), today.minusDays(2))
        assertEquals(3, currentStreakDays(dates, today))
    }

    @Test
    fun `a streak ending yesterday is still alive`() {
        val dates = listOf(today.minusDays(1), today.minusDays(2), today.minusDays(3))
        assertEquals(3, currentStreakDays(dates, today))
    }

    @Test
    fun `a streak with no activity yesterday or today is dead`() {
        val dates = listOf(today.minusDays(2), today.minusDays(3))
        assertEquals(0, currentStreakDays(dates, today))
    }

    @Test
    fun `a broken streak only counts the most recent consecutive run`() {
        val dates = listOf(today, today.minusDays(1), today.minusDays(5), today.minusDays(6))
        assertEquals(2, currentStreakDays(dates, today))
    }

    @Test
    fun `duplicate dates on the same day are not double-counted`() {
        val dates = listOf(today, today, today.minusDays(1))
        assertEquals(2, currentStreakDays(dates, today))
    }
}
