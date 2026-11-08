package com.workouttracker.domain

import kotlin.math.roundToInt

/** One working set as it was last performed, against its planned rep range. */
data class WorkingSet(
    val reps: Int,
    val weightGrams: Int,
    /** Top of the planned rep range; null for open-ended targets such as "10+". */
    val targetMaxReps: Int?,
)

object Progression {

    /**
     * If every working set last time reached the top of its rep range, returns each set's weight plus
     * [incrementGrams]. Otherwise returns null: keep the same weights and chase the reps.
     * Open-ended targets ("10+") never trigger a weight increase on their own.
     */
    fun suggestWeights(lastSets: List<WorkingSet>, incrementGrams: Int): List<Int>? {
        require(incrementGrams > 0) { "increment must be positive" }
        if (lastSets.isEmpty()) return null
        val allAtTop = lastSets.all { set -> set.targetMaxReps != null && set.reps >= set.targetMaxReps }
        if (!allAtTop) return null
        return lastSets.map { (it.weightGrams + incrementGrams).coerceAtMost(Weights.MAX_GRAMS) }
    }
}

object OneRepMax {

    /** Epley estimate in grams: w × (1 + reps / 30). One rep is the weight itself; zero reps estimate nothing. */
    fun epleyGrams(reps: Int, weightGrams: Int): Int = when {
        reps <= 0 || weightGrams <= 0 -> 0
        reps == 1 -> weightGrams
        else -> (weightGrams * (1 + reps / 30.0)).roundToInt()
    }

    fun best(sets: List<WorkingSet>): Int = sets.maxOfOrNull { epleyGrams(it.reps, it.weightGrams) } ?: 0
}
