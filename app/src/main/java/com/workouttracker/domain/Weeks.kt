package com.workouttracker.domain

import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** Per-session numbers the weekly view needs. Volume is Σ reps × grams over working (non warm-up) sets. */
data class SessionStats(
    val date: LocalDate,
    val status: SessionStatus,
    val startedAt: Instant?,
    val finishedAt: Instant?,
    val workingSets: Int,
    val volumeGramReps: Long,
)

enum class DayStatus { DONE, SKIPPED, REST }

data class WeekSummary(
    val weekStart: LocalDate,
    val workouts: Int,
    val skipped: Int,
    val sets: Int,
    val volumeGramReps: Long,
    val time: Duration,
    /** Monday..Sunday, always 7 entries. */
    val days: List<DayStatus>,
)

object Weeks {

    /** Weeks run Monday to Sunday. */
    fun startOf(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    fun summarize(weekStart: LocalDate, sessions: List<SessionStats>): WeekSummary {
        require(weekStart.dayOfWeek == DayOfWeek.MONDAY) { "weekStart must be a Monday" }
        val weekEnd = weekStart.plusDays(6)
        val inWeek = sessions.filter { !it.date.isBefore(weekStart) && !it.date.isAfter(weekEnd) }
        val done = inWeek.filter { it.status == SessionStatus.DONE }
        val skipped = inWeek.filter { it.status == SessionStatus.SKIPPED }

        val time = done.fold(Duration.ZERO) { total, s ->
            val start = s.startedAt
            val end = s.finishedAt
            if (start != null && end != null && end.isAfter(start)) total + Duration.between(start, end) else total
        }

        val days = (0L..6L).map { offset ->
            val day = weekStart.plusDays(offset)
            when {
                done.any { it.date == day } -> DayStatus.DONE
                skipped.any { it.date == day } -> DayStatus.SKIPPED
                else -> DayStatus.REST
            }
        }

        return WeekSummary(
            weekStart = weekStart,
            workouts = done.size,
            skipped = skipped.size,
            sets = done.sumOf { it.workingSets },
            volumeGramReps = done.sumOf { it.volumeGramReps },
            time = time,
            days = days,
        )
    }

    /** The last [count] weeks ending with the week of [today], oldest first. */
    fun recent(today: LocalDate, count: Int, sessions: List<SessionStats>): List<WeekSummary> {
        require(count > 0) { "count must be positive" }
        val current = startOf(today)
        return (count - 1 downTo 0).map { back -> summarize(current.minusWeeks(back.toLong()), sessions) }
    }
}
