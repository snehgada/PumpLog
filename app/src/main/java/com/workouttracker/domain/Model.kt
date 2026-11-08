package com.workouttracker.domain

enum class SessionStatus { IN_PROGRESS, DONE, SKIPPED }

enum class SetType { NORMAL, DROP, WARMUP, FAILURE }

enum class ExerciseKind { BARBELL, DUMBBELL, CABLE, MACHINE, BODYWEIGHT }

enum class WeightUnit { KG, LB }

/** Weight added when a progression suggestion fires. Dumbbell increments are per dumbbell. */
data class Increments(val barbellGrams: Int, val dumbbellGrams: Int)
