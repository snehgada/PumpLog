package com.workouttracker.data.seed

import com.workouttracker.domain.ExerciseKind
import com.workouttracker.domain.ExerciseKind.BARBELL
import com.workouttracker.domain.ExerciseKind.BODYWEIGHT
import com.workouttracker.domain.ExerciseKind.CABLE
import com.workouttracker.domain.ExerciseKind.DUMBBELL
import com.workouttracker.domain.ExerciseKind.MACHINE

data class SetSeed(val minReps: Int, val maxReps: Int?, val note: String? = null)

data class ExerciseSeed(
    val name: String,
    val kind: ExerciseKind,
    val sets: List<SetSeed>,
    val isRpt: Boolean = false,
    val alternative: Pair<String, ExerciseKind>? = null,
) {
    /**
     * Default rest from the heaviest (lowest top-rep) set: ≤6 reps 3:00, ≤10 reps 2:00, otherwise 1:30.
     * Open-ended sets ("10+") count as light work.
     */
    val restSec: Int
        get() {
            val top = sets.minOf { it.maxReps ?: Int.MAX_VALUE }
            return when {
                top <= 6 -> 180
                top <= 10 -> 120
                else -> 90
            }
        }
}

data class RoutineSeed(val name: String, val exercises: List<ExerciseSeed>)

/** The split from the old Google Sheet, loaded on first launch. Order = rotation order. */
object SeedRoutines {

    private fun sets(count: Int, min: Int, max: Int?) = List(count) { SetSeed(min, max) }

    val all: List<RoutineSeed> = listOf(
        RoutineSeed(
            "Upper",
            listOf(
                ExerciseSeed("Bench Press", BARBELL, sets(4, 4, 6)),
                ExerciseSeed("Lat Pulldown", CABLE, sets(3, 6, 8)),
                ExerciseSeed("Standing Overhead Press", BARBELL, sets(3, 8, 10)),
                ExerciseSeed("Dumbbell Pullover", DUMBBELL, sets(3, 10, 12)),
                ExerciseSeed("High to Low Cable Flies", CABLE, sets(3, 12, 15)),
                ExerciseSeed("Lying Face Pulls", CABLE, sets(3, 10, 12)),
            ),
        ),
        RoutineSeed(
            "Lower",
            listOf(
                ExerciseSeed("Front Squat", BARBELL, sets(3, 10, 12)),
                ExerciseSeed("Deadlift", BARBELL, sets(4, 6, 8)),
                ExerciseSeed("Barbell Hip Thrust", BARBELL, sets(4, 8, 12)),
                ExerciseSeed("Single Leg Weighted Calf Raise (per side)", DUMBBELL, sets(3, 6, 8)),
                ExerciseSeed("Leg Press Calf Raise", MACHINE, sets(3, 8, 12)),
            ),
        ),
        RoutineSeed(
            "Push",
            listOf(
                ExerciseSeed("Incline Dumbbell Press", DUMBBELL, sets(3, 8, 10)),
                ExerciseSeed(
                    "Flat Dumbbell Press", DUMBBELL,
                    listOf(SetSeed(6, 8), SetSeed(8, 10), SetSeed(10, 12)),
                    isRpt = true,
                ),
                ExerciseSeed("Lateral Raises (Cable & Dumbbell)", DUMBBELL, sets(4, 8, 12)),
                ExerciseSeed("Banded Push-Ups", BODYWEIGHT, sets(2, 10, null)),
                ExerciseSeed("Overhead Rope Extensions", CABLE, sets(3, 12, 15)),
                ExerciseSeed("Bar Triceps Pushdowns", CABLE, sets(3, 8, 12)),
            ),
        ),
        RoutineSeed(
            "Pull",
            listOf(
                ExerciseSeed(
                    "Weighted Pull-Ups", BODYWEIGHT,
                    listOf(SetSeed(4, 6), SetSeed(6, 8), SetSeed(8, 10)),
                    isRpt = true,
                ),
                ExerciseSeed("Seated Row", CABLE, sets(3, 8, 10), alternative = "Dumbbell Rows" to DUMBBELL),
                ExerciseSeed("Smith Machine Shrugs", MACHINE, sets(3, 10, 12)),
                ExerciseSeed("Kneeling Face Pulls", CABLE, sets(4, 10, 15)),
                ExerciseSeed("Incline Dumbbell Curls", DUMBBELL, sets(3, 8, 10)),
                ExerciseSeed("Hammer Curls", DUMBBELL, sets(3, 8, 10)),
                ExerciseSeed("Scapular Pull-Ups", BODYWEIGHT, sets(2, 5, null)),
            ),
        ),
        RoutineSeed(
            "Legs",
            listOf(
                ExerciseSeed(
                    "Back Squat", BARBELL,
                    sets(3, 6, 8) + SetSeed(8, 10, note = "Slow eccentric, about 60% of working weight"),
                ),
                ExerciseSeed("Bulgarian Split Squat (per side)", DUMBBELL, sets(4, 8, 10)),
                ExerciseSeed("Glute Ham Raise", BODYWEIGHT, sets(4, 10, 12)),
                ExerciseSeed("Smith Machine Calf Raises", MACHINE, sets(3, 10, 15)),
                ExerciseSeed("Seated Weighted Calf Raise", MACHINE, sets(3, 10, 15)),
            ),
        ),
    )
}
