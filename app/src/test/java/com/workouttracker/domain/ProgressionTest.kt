package com.workouttracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProgressionTest {

    private val kg = 1000

    @Test fun `bench press from the sheet - every set beat 4-6, so add 2,5 kg`() {
        // Session 4 of Bench Press in the old sheet: 8 × 25, 10 × 27.5, 7 × 30, target 4–6.
        val last = listOf(
            WorkingSet(reps = 8, weightGrams = 25 * kg, targetMaxReps = 6),
            WorkingSet(reps = 10, weightGrams = 27_500, targetMaxReps = 6),
            WorkingSet(reps = 7, weightGrams = 30 * kg, targetMaxReps = 6),
        )
        assertEquals(listOf(27_500, 30_000, 32_500), Progression.suggestWeights(last, 2_500))
    }

    @Test fun `one set short of the top keeps the weights`() {
        val last = listOf(
            WorkingSet(reps = 10, weightGrams = 30 * kg, targetMaxReps = 10),
            WorkingSet(reps = 8, weightGrams = 30 * kg, targetMaxReps = 10),
        )
        assertNull(Progression.suggestWeights(last, 2_000))
    }

    @Test fun `open-ended targets never trigger an increase`() {
        val last = listOf(WorkingSet(reps = 25, weightGrams = 0, targetMaxReps = null))
        assertNull(Progression.suggestWeights(last, 2_500))
    }

    @Test fun `no history, no suggestion`() {
        assertNull(Progression.suggestWeights(emptyList(), 2_500))
    }

    @Test fun `epley estimate`() {
        assertEquals(37_000, OneRepMax.epleyGrams(reps = 7, weightGrams = 30 * kg))
        assertEquals(30_000, OneRepMax.epleyGrams(reps = 1, weightGrams = 30 * kg))
        assertEquals(0, OneRepMax.epleyGrams(reps = 0, weightGrams = 30 * kg))
    }

    @Test fun `best estimate across sets`() {
        // Session 1 of Bench Press: 10 × 20, 8 × 25, 7 × 27.5 → best is 7 × 27.5 ≈ 33.9 kg.
        val sets = listOf(
            WorkingSet(10, 20 * kg, 6),
            WorkingSet(8, 25 * kg, 6),
            WorkingSet(7, 27_500, 6),
        )
        assertEquals(33_917, OneRepMax.best(sets))
    }
}
