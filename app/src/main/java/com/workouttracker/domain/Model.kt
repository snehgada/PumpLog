package com.workouttracker.domain

enum class SessionStatus { IN_PROGRESS, DONE, SKIPPED }

enum class SetType { NORMAL, DROP, WARMUP, FAILURE }

enum class ExerciseKind { BARBELL, DUMBBELL, CABLE, MACHINE, BODYWEIGHT }

enum class WeightUnit { KG, LB }

/** Weight added when a progression suggestion fires. Dumbbell increments are per dumbbell. */
data class Increments(val barbellGrams: Int, val dumbbellGrams: Int)

object IncrementChoices {
    private val KG = listOf(1_000, 1_250, 2_000, 2_500, 5_000)

    // 2.5, 5 and 10 lb, rounded to whole grams.
    private val LB = listOf(1_134, 2_268, 4_536)

    /** The steps offered in Settings for [unit], in grams. */
    fun forUnit(unit: WeightUnit): List<Int> = if (unit == WeightUnit.KG) KG else LB

    /** The offered step closest to [grams], so a value saved in the other unit still shows a selection. */
    fun closest(grams: Int, unit: WeightUnit): Int = forUnit(unit).minBy { kotlin.math.abs(it - grams) }
}
