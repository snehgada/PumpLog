package com.workouttracker.domain

/** One planned set as typed in the editor. Text, so a half-typed or cleared field can be represented. */
data class SetDraft(
    val minText: String,
    /** Blank = open-ended ("10+"). */
    val maxText: String,
    val note: String? = null,
)

data class ExerciseDraft(
    /** Stable key for list items while editing; not stored. */
    val key: Long,
    /** Null for an exercise typed in the editor that may not exist yet. */
    val exerciseId: Long?,
    val name: String,
    val kind: ExerciseKind,
    val isRpt: Boolean,
    val restSec: Int,
    val sets: List<SetDraft>,
    /** Blank = no alternative. */
    val alternativeName: String,
)

data class RoutineDraft(
    /** Null for a new routine. */
    val routineId: Long?,
    val name: String,
    val exercises: List<ExerciseDraft>,
)

enum class NameIssue { BLANK, TOO_LONG }
enum class ExerciseIssue { NAME_BLANK, NAME_TOO_LONG, NO_SETS, TOO_MANY_SETS, ALTERNATIVE_TOO_LONG, ALTERNATIVE_SAME_AS_EXERCISE }
enum class SetIssue { MIN_INVALID, MAX_INVALID, MAX_BELOW_MIN }

data class DraftIssues(
    val name: NameIssue? = null,
    val noExercises: Boolean = false,
    val tooManyExercises: Boolean = false,
    /** Keyed by [ExerciseDraft.key]. */
    val exercises: Map<Long, ExerciseIssue> = emptyMap(),
    /** Keyed by [ExerciseDraft.key], then set index. */
    val sets: Map<Long, Map<Int, SetIssue>> = emptyMap(),
) {
    val isValid: Boolean
        get() = name == null && !noExercises && !tooManyExercises && exercises.isEmpty() && sets.isEmpty()
}

/** A validated routine, ready to store. */
data class RoutinePlan(val routineId: Long?, val name: String, val exercises: List<ExercisePlan>)

data class ExercisePlan(
    val exerciseId: Long?,
    val name: String,
    val kind: ExerciseKind,
    val isRpt: Boolean,
    val restSec: Int,
    val sets: List<PlannedSet>,
    val alternativeName: String?,
)

data class PlannedSet(val minReps: Int, val maxReps: Int?, val note: String?)

object RoutineEditing {
    const val MAX_ROUTINES = 10
    const val MAX_ROUTINE_NAME = 40
    const val MAX_EXERCISE_NAME = 60
    const val MAX_EXERCISES = 20
    const val MAX_SETS = 10
    const val MAX_TARGET_REPS = 100
    const val MIN_REST_SEC = 15
    const val MAX_REST_SEC = 600
    const val REST_STEP_SEC = 15
    private const val MAX_REP_INPUT = 3

    fun validate(draft: RoutineDraft): DraftIssues {
        val name = draft.name.trim()
        val nameIssue = when {
            name.isEmpty() -> NameIssue.BLANK
            name.length > MAX_ROUTINE_NAME -> NameIssue.TOO_LONG
            else -> null
        }
        val exerciseIssues = mutableMapOf<Long, ExerciseIssue>()
        val setIssues = mutableMapOf<Long, Map<Int, SetIssue>>()
        draft.exercises.forEach { ex ->
            exerciseIssue(ex)?.let { exerciseIssues[ex.key] = it }
            val perSet = ex.sets.mapIndexedNotNull { i, set -> setIssue(set)?.let { i to it } }.toMap()
            if (perSet.isNotEmpty()) setIssues[ex.key] = perSet
        }
        return DraftIssues(
            name = nameIssue,
            noExercises = draft.exercises.isEmpty(),
            tooManyExercises = draft.exercises.size > MAX_EXERCISES,
            exercises = exerciseIssues,
            sets = setIssues,
        )
    }

    /** Returns the stored form of [draft], or null if it has any issue. */
    fun toPlan(draft: RoutineDraft): RoutinePlan? {
        if (!validate(draft).isValid) return null
        return RoutinePlan(
            routineId = draft.routineId,
            name = draft.name.trim(),
            exercises = draft.exercises.map { ex ->
                ExercisePlan(
                    exerciseId = ex.exerciseId,
                    name = ex.name.trim(),
                    kind = ex.kind,
                    isRpt = ex.isRpt,
                    restSec = clampRest(ex.restSec),
                    sets = ex.sets.map { s ->
                        PlannedSet(parseReps(s.minText)!!, s.maxText.takeIf { it.isNotBlank() }?.let { parseReps(it)!! }, s.note)
                    },
                    alternativeName = ex.alternativeName.trim().ifEmpty { null },
                )
            },
        )
    }

    /** A usable exercise name: trimmed, 1 to [MAX_EXERCISE_NAME] characters, or null. */
    fun cleanExerciseName(name: String): String? =
        name.trim().takeIf { it.isNotEmpty() && it.length <= MAX_EXERCISE_NAME }

    /** Keeps only digits, at most 3 of them: what a rep field accepts while typing. */
    fun filterRepInput(text: String): String = text.filter { it in '0'..'9' }.take(MAX_REP_INPUT)

    /** A target rep count: 1 to [MAX_TARGET_REPS]. */
    fun parseReps(text: String): Int? {
        val t = text.trim()
        if (t.isEmpty() || t.length > MAX_REP_INPUT || t.any { it !in '0'..'9' }) return null
        return t.toInt().takeIf { it in 1..MAX_TARGET_REPS }
    }

    fun clampRest(sec: Int): Int = sec.coerceIn(MIN_REST_SEC, MAX_REST_SEC)

    fun adjustRest(sec: Int, steps: Int): Int = clampRest(sec + steps * REST_STEP_SEC)

    /** Moves the item at [from] one place by [delta] (-1 up, +1 down). Out-of-range moves return the list unchanged. */
    fun <T> move(list: List<T>, from: Int, delta: Int): List<T> {
        val to = from + delta
        if (from !in list.indices || to !in list.indices) return list
        return list.toMutableList().apply { add(to, removeAt(from)) }
    }

    /** Appends a set copying the last one's targets, up to [MAX_SETS]. */
    fun addSet(ex: ExerciseDraft): ExerciseDraft {
        if (ex.sets.size >= MAX_SETS) return ex
        val template = ex.sets.lastOrNull()?.copy(note = null) ?: SetDraft("8", "12")
        return ex.copy(sets = ex.sets + template)
    }

    fun removeSet(ex: ExerciseDraft, index: Int): ExerciseDraft {
        if (index !in ex.sets.indices || ex.sets.size <= 1) return ex
        return ex.copy(sets = ex.sets.toMutableList().apply { removeAt(index) })
    }

    /** "3 × 8–10", "2 × 10+", or "4–6 / 6–8 / 8–10" when sets differ. */
    fun scheme(sets: List<SetDraft>): String {
        if (sets.isEmpty()) return ""
        val ranges = sets.map { range(it) }
        return if (ranges.distinct().size == 1) "${sets.size} × ${ranges.first()}" else ranges.joinToString(" / ")
    }

    private fun range(set: SetDraft): String {
        val min = set.minText.trim().ifEmpty { "?" }
        val max = set.maxText.trim()
        return when {
            max.isEmpty() -> "$min+"
            max == min -> min
            else -> "$min–$max"
        }
    }

    private fun exerciseIssue(ex: ExerciseDraft): ExerciseIssue? {
        val name = ex.name.trim()
        val alt = ex.alternativeName.trim()
        return when {
            name.isEmpty() -> ExerciseIssue.NAME_BLANK
            name.length > MAX_EXERCISE_NAME -> ExerciseIssue.NAME_TOO_LONG
            ex.sets.isEmpty() -> ExerciseIssue.NO_SETS
            ex.sets.size > MAX_SETS -> ExerciseIssue.TOO_MANY_SETS
            alt.length > MAX_EXERCISE_NAME -> ExerciseIssue.ALTERNATIVE_TOO_LONG
            alt.isNotEmpty() && alt.equals(name, ignoreCase = true) -> ExerciseIssue.ALTERNATIVE_SAME_AS_EXERCISE
            else -> null
        }
    }

    private fun setIssue(set: SetDraft): SetIssue? {
        val min = parseReps(set.minText) ?: return SetIssue.MIN_INVALID
        if (set.maxText.isBlank()) return null
        val max = parseReps(set.maxText) ?: return SetIssue.MAX_INVALID
        return if (max < min) SetIssue.MAX_BELOW_MIN else null
    }
}
