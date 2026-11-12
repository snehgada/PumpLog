package com.workouttracker.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class IncrementChoicesTest {

    @Test fun `pound steps format as round numbers`() {
        assertEquals(listOf("2.5", "5", "10"), IncrementChoices.forUnit(WeightUnit.LB).map { Weights.format(it, WeightUnit.LB) })
        assertEquals(listOf("1", "1.25", "2", "2.5", "5"), IncrementChoices.forUnit(WeightUnit.KG).map { Weights.format(it, WeightUnit.KG) })
    }

    @Test fun `a step saved in one unit maps to the closest in the other`() {
        assertEquals(2_268, IncrementChoices.closest(2_500, WeightUnit.LB)) // 2.5 kg ≈ 5.5 lb → 5 lb
        assertEquals(1_250, IncrementChoices.closest(1_134, WeightUnit.KG)) // 2.5 lb ≈ 1.13 kg → 1.25 kg
        assertEquals(2_500, IncrementChoices.closest(2_500, WeightUnit.KG))
    }
}
