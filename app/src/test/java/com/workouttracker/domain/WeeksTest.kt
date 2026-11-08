package com.workouttracker.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

class WeeksTest {

    private val monday = LocalDate.of(2026, 11, 2)

    private fun done(day: Int, minutes: Long, sets: Int, volumeKgReps: Long): SessionStats {
        val start = Instant.parse("2026-11-%02dT07:00:00Z".format(day))
        return SessionStats(
            date = LocalDate.of(2026, 11, day),
            status = SessionStatus.DONE,
            startedAt = start,
            finishedAt = start.plus(Duration.ofMinutes(minutes)),
            workingSets = sets,
            volumeGramReps = volumeKgReps * 1000,
        )
    }

    private fun skipped(day: Int) = SessionStats(LocalDate.of(2026, 11, day), SessionStatus.SKIPPED, null, null, 0, 0)

    @Test fun `weeks start on Monday`() {
        assertEquals(monday, Weeks.startOf(LocalDate.of(2026, 11, 8))) // Sunday
        assertEquals(monday, Weeks.startOf(monday))
    }

    @Test fun `summarizes the week from the design`() {
        val sessions = listOf(
            done(2, 62, 21, 6_240),   // Pull
            done(4, 71, 18, 9_810),   // Legs
            done(6, 58, 19, 5_930),   // Upper
            skipped(7),               // Lower
            done(9, 60, 18, 7_000),   // next week: ignored
            SessionStats(LocalDate.of(2026, 11, 8), SessionStatus.IN_PROGRESS, Instant.parse("2026-11-08T07:00:00Z"), null, 5, 1_000_000),
        )

        val week = Weeks.summarize(monday, sessions)

        assertEquals(3, week.workouts)
        assertEquals(1, week.skipped)
        assertEquals(58, week.sets)
        assertEquals(21_980_000L, week.volumeGramReps)
        assertEquals(Duration.ofMinutes(191), week.time)
        assertEquals(
            listOf(DayStatus.DONE, DayStatus.REST, DayStatus.DONE, DayStatus.REST, DayStatus.DONE, DayStatus.SKIPPED, DayStatus.REST),
            week.days,
        )
    }

    @Test fun `past workouts without times count sets but not time`() {
        val backfilled = SessionStats(LocalDate.of(2026, 11, 3), SessionStatus.DONE, null, null, 17, 4_000_000)
        val week = Weeks.summarize(monday, listOf(backfilled))
        assertEquals(1, week.workouts)
        assertEquals(17, week.sets)
        assertEquals(Duration.ZERO, week.time)
    }

    @Test fun `a day with a workout and a skip counts as a workout day`() {
        val week = Weeks.summarize(monday, listOf(skipped(3), done(3, 45, 10, 1_000)))
        assertEquals(DayStatus.DONE, week.days[1])
    }

    @Test fun `recent weeks are oldest first and end with this week`() {
        val weeks = Weeks.recent(LocalDate.of(2026, 11, 8), 8, emptyList())
        assertEquals(8, weeks.size)
        assertEquals(LocalDate.of(2026, 9, 14), weeks.first().weekStart)
        assertEquals(monday, weeks.last().weekStart)
    }
}
