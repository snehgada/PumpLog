package com.workouttracker.domain

import java.time.LocalDate
import java.util.Locale

/** One finished workout's sets for a single exercise. */
data class ExerciseSession(val sessionId: Long, val date: LocalDate, val sets: List<PerformedSet>)

data class ProgressPoint(
    val sessionId: Long,
    val date: LocalDate,
    /** Best Epley estimate across the session's working sets (first block of each set). */
    val e1rmGrams: Int,
    /** Heaviest first-block weight of the session. */
    val topSetGrams: Int,
    /** Σ reps × weight over every block of every working set. */
    val volumeGramReps: Long,
)

data class BestSet(val reps: Int, val weightGrams: Int, val date: LocalDate, val sessionId: Long)

data class ExerciseProgressSummary(
    /** Oldest first; sessions with only warm-ups are left out. */
    val points: List<ProgressPoint>,
    val bestSet: BestSet?,
    val bestVolume: ProgressPoint?,
)

object ExerciseProgress {

    fun summarize(sessions: List<ExerciseSession>): ExerciseProgressSummary {
        var bestSet: BestSet? = null
        var bestSetE1rm = -1
        val points = sessions
            .sortedWith(compareBy({ it.date }, { it.sessionId }))
            .mapNotNull { session ->
                val working = session.sets.filter { it.type != SetType.WARMUP && it.segments.isNotEmpty() }
                if (working.isEmpty()) return@mapNotNull null
                var e1rm = 0
                var top = 0
                for (set in working) {
                    val first = set.segments.first()
                    val estimate = OneRepMax.epleyGrams(first.reps, first.weightGrams)
                    e1rm = maxOf(e1rm, estimate)
                    top = maxOf(top, first.weightGrams)
                    // ">=" so a later session matching the record becomes the one shown.
                    if (estimate >= bestSetE1rm && estimate > 0) {
                        bestSetE1rm = estimate
                        bestSet = BestSet(first.reps, first.weightGrams, session.date, session.sessionId)
                    }
                }
                val volume = working.sumOf { set -> set.segments.sumOf { it.reps.toLong() * it.weightGrams } }
                ProgressPoint(session.sessionId, session.date, e1rm, top, volume)
            }
        return ExerciseProgressSummary(
            points = points,
            bestSet = bestSet,
            bestVolume = points.maxByOrNull { it.volumeGramReps }?.takeIf { it.volumeGramReps > 0 },
        )
    }

    /** Short volume label: "685 kg", "22.0 t"; in pounds "1,510 lb", "48.5k lb". */
    fun formatVolume(gramReps: Long, unit: WeightUnit, locale: Locale = Locale.getDefault()): String {
        val kg = gramReps / 1000.0
        return when (unit) {
            WeightUnit.KG -> if (kg >= 10_000) String.format(locale, "%.1f t", kg / 1000) else String.format(locale, "%,d kg", Math.round(kg))
            WeightUnit.LB -> {
                val lb = gramReps / Weights.GRAMS_PER_LB
                if (lb >= 10_000) String.format(locale, "%.1fk lb", lb / 1000) else String.format(locale, "%,d lb", Math.round(lb))
            }
        }
    }
}
