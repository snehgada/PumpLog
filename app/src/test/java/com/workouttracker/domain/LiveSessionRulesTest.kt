package com.workouttracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveSessionRulesTest {

    private fun set(index: Int, reps: Int, kg: Double, type: SetType = SetType.NORMAL) =
        PerformedSet(index.toLong(), index, type, null, listOf(Segment(reps, (kg * 1000).toInt())), performedExerciseId = 1)

    private val bench = List(3) { SetTarget(4, 6, null) }

    @Test fun `target and scheme text`() {
        assertEquals("4–6", LiveSessionRules.targetText(SetTarget(4, 6, null)))
        assertEquals("10+", LiveSessionRules.targetText(SetTarget(10, null, null)))
        assertEquals("5", LiveSessionRules.targetText(SetTarget(5, 5, null)))
        assertEquals("3 × 4–6", LiveSessionRules.scheme(bench))
        assertEquals("4–6 / 6–8", LiveSessionRules.scheme(listOf(SetTarget(4, 6, null), SetTarget(6, 8, null))))
    }

    @Test fun `last time text for normal and drop sets`() {
        assertEquals("8 × 25", LiveSessionRules.lastTimeText(set(0, 8, 25.0), WeightUnit.KG))
        val drop = PerformedSet(1, 2, SetType.DROP, null, listOf(Segment(5, 35_000), Segment(4, 30_000)), 1)
        assertEquals("5 × 35 → 4 × 30", LiveSessionRules.lastTimeText(drop, WeightUnit.KG))
        assertEquals("—", LiveSessionRules.lastTimeText(null, WeightUnit.KG))
    }

    @Test fun `suggestion from the sheet's bench press session 4`() {
        val last = listOf(set(0, 8, 25.0), set(1, 10, 27.5), set(2, 7, 30.0))
        assertEquals(listOf(27_500, 30_000, 32_500), LiveSessionRules.suggestion(last, bench, 2_500))
    }

    @Test fun `no suggestion when a planned set is missing or a warm-up stands in for it`() {
        assertNull(LiveSessionRules.suggestion(listOf(set(0, 8, 25.0), set(1, 10, 27.5)), bench, 2_500))
        val withWarmup = listOf(set(0, 8, 25.0), set(1, 10, 27.5), set(2, 12, 10.0, SetType.WARMUP))
        assertNull(LiveSessionRules.suggestion(withWarmup, bench, 2_500))
    }

    @Test fun `prefill uses the suggestion, else last weight for that set`() {
        val last = listOf(set(0, 8, 25.0), set(1, 10, 27.5))
        assertEquals(32_500, LiveSessionRules.prefillGrams(2, listOf(27_500, 30_000, 32_500), last))
        assertEquals(27_500, LiveSessionRules.prefillGrams(1, null, last))
        assertNull(LiveSessionRules.prefillGrams(3, null, last))
    }

    @Test fun `segments are parsed and bounded`() {
        assertEquals(Segment(6, 27_500), LiveSessionRules.parseSegment("27.5", "6", WeightUnit.KG))
        assertNull(LiveSessionRules.parseSegment("27.5", "", WeightUnit.KG))
        assertNull(LiveSessionRules.parseSegment("abc", "6", WeightUnit.KG))
        assertTrue(LiveSessionRules.isValid(listOf(Segment(5, 35_000), Segment(4, 30_000))))
        assertFalse(LiveSessionRules.isValid(emptyList()))
        assertFalse(LiveSessionRules.isValid(List(6) { Segment(1, 1_000) }))
    }

    @Test fun `notes are trimmed, capped and blank becomes null`() {
        assertNull(LiveSessionRules.cleanNote("   "))
        assertEquals("Perfect reps", LiveSessionRules.cleanNote("  Perfect reps "))
        assertEquals(LiveSessionRules.MAX_NOTE, LiveSessionRules.cleanNote("x".repeat(500))!!.length)
    }

    @Test fun `weight input keeps digits and one separator`() {
        assertEquals("27.5", LiveSessionRules.filterWeightInput("27.5kg"))
        assertEquals("27,50", LiveSessionRules.filterWeightInput("27,5,0")) // second separator dropped
        assertEquals("1234567", LiveSessionRules.filterWeightInput("123456789"))
        assertEquals("", LiveSessionRules.filterWeightInput("-"))
    }

    @Test fun `dumbbells use the dumbbell increment`() {
        val inc = Increments(barbellGrams = 2_500, dumbbellGrams = 2_000)
        assertEquals(2_000, LiveSessionRules.incrementFor(ExerciseKind.DUMBBELL, inc))
        assertEquals(2_500, LiveSessionRules.incrementFor(ExerciseKind.CABLE, inc))
    }
}
