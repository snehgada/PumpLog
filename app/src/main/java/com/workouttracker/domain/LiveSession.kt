package com.workouttracker.domain

/** One weight × reps block. A normal set has one; a drop set has two or more. */
data class Segment(val reps: Int, val weightGrams: Int)

/** A set as it was logged. */
data class PerformedSet(
    val id: Long,
    val setIndex: Int,
    val type: SetType,
    val note: String?,
    val segments: List<Segment>,
    val performedExerciseId: Long,
)

/** What the routine asks for on one set. */
data class SetTarget(val minReps: Int, val maxReps: Int?, val note: String?)

object LiveSessionRules {
    const val MAX_SEGMENTS = 5
    const val MAX_NOTE = 200
    const val MAX_EXTRA_SETS = 10

    /** "4–6", "10+", or "5" when min equals max. */
    fun targetText(target: SetTarget): String = when (target.maxReps) {
        null -> "${target.minReps}+"
        target.minReps -> "${target.minReps}"
        else -> "${target.minReps}–${target.maxReps}"
    }

    /** "3 × 8–10", or "4–6 / 6–8 / 8–10" when the sets differ. */
    fun scheme(targets: List<SetTarget>): String {
        if (targets.isEmpty()) return ""
        val texts = targets.map(::targetText)
        return if (texts.distinct().size == 1) "${targets.size} × ${texts.first()}" else texts.joinToString(" / ")
    }

    /** "8 × 25", or "5 × 35 → 4 × 30" for a drop set; "—" if there is nothing. */
    fun lastTimeText(set: PerformedSet?, unit: WeightUnit): String =
        set?.segments?.takeIf { it.isNotEmpty() }
            ?.joinToString(" → ") { "${it.reps} × ${Weights.format(it.weightGrams, unit)}" }
            ?: "—"

    fun incrementFor(kind: ExerciseKind, increments: Increments): Int =
        if (kind == ExerciseKind.DUMBBELL) increments.dumbbellGrams else increments.barbellGrams

    /**
     * Suggested weight for each planned set, or null to keep last time's weights.
     * Fires only when last time covered every planned set and each reached the top of its range
     * (see [Progression.suggestWeights]). Drop sets are judged by their first block; warm-ups are ignored.
     */
    fun suggestion(last: List<PerformedSet>, targets: List<SetTarget>, incrementGrams: Int): List<Int>? {
        if (targets.isEmpty()) return null
        val bySet = last
            .filter { it.type != SetType.WARMUP && it.segments.isNotEmpty() && it.setIndex in targets.indices }
            .associateBy { it.setIndex }
        if (bySet.size < targets.size) return null
        val working = targets.indices.map { i ->
            val top = bySet.getValue(i).segments.first()
            WorkingSet(reps = top.reps, weightGrams = top.weightGrams, targetMaxReps = targets[i].maxReps)
        }
        return Progression.suggestWeights(working, incrementGrams)
    }

    /** Weight to pre-fill for set [index]: the suggestion, else last time's weight for that set. */
    fun prefillGrams(index: Int, suggestion: List<Int>?, last: List<PerformedSet>): Int? =
        suggestion?.getOrNull(index)
            ?: last.firstOrNull { it.setIndex == index && it.type != SetType.WARMUP }?.segments?.firstOrNull()?.weightGrams

    /** Parses one typed weight + reps pair; null if either is invalid. */
    fun parseSegment(weightText: String, repsText: String, unit: WeightUnit): Segment? {
        val grams = Weights.parse(weightText, unit) ?: return null
        val reps = Reps.parse(repsText) ?: return null
        return Segment(reps, grams)
    }

    fun isValid(segments: List<Segment>): Boolean =
        segments.size in 1..MAX_SEGMENTS &&
            segments.all { it.reps in 0..Reps.MAX && it.weightGrams in 0..Weights.MAX_GRAMS }

    /** What a weight field accepts while typing: digits and one decimal separator, at most 7 characters. */
    fun filterWeightInput(text: String): String {
        val out = StringBuilder()
        var separatorSeen = false
        for (c in text) {
            when {
                c in '0'..'9' -> out.append(c)
                (c == '.' || c == ',') && !separatorSeen -> { out.append(c); separatorSeen = true }
            }
            if (out.length == 7) break
        }
        return out.toString()
    }

    /** Trims a note and caps its length; blank becomes null. */
    fun cleanNote(note: String?): String? = note?.trim()?.take(MAX_NOTE)?.ifEmpty { null }
}
