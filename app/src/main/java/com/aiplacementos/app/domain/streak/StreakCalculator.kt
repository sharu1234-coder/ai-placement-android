package com.aiplacementos.app.domain.streak

import java.time.LocalDate

/**
 * Pure function, no fabricated data -- computed from real completed-test/interview dates
 * (ProgressDashboardDto.trend's created_at). Counts consecutive calendar days working
 * backward from [today], "alive" only if today or yesterday has activity (standard streak
 * UX: a streak doesn't silently reset at midnight before the student has had a chance to act
 * today).
 *
 * Known, disclosed limitation: only counts days with a completed test/interview -- roadmap
 * task/topic completions have no timestamp column on the backend today, so a pure
 * practice-only day won't extend the streak.
 */
fun currentStreakDays(activityDates: List<LocalDate>, today: LocalDate = LocalDate.now()): Int {
    val distinctDates = activityDates.toSortedSet(compareByDescending { it })
    if (distinctDates.isEmpty()) return 0

    val mostRecent = distinctDates.first()
    if (mostRecent.isBefore(today.minusDays(1))) return 0

    var streak = 0
    var cursor = mostRecent
    for (date in distinctDates) {
        if (date == cursor) {
            streak++
            cursor = cursor.minusDays(1)
        } else if (date.isBefore(cursor)) {
            break
        }
    }
    return streak
}
