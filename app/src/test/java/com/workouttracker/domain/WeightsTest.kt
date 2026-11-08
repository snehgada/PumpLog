package com.workouttracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WeightsTest {

    @Test fun `parses kg with dot or comma`() {
        assertEquals(27_500, Weights.parse("27.5", WeightUnit.KG))
        assertEquals(27_500, Weights.parse("27,5", WeightUnit.KG))
        assertEquals(30_000, Weights.parse("  30 ", WeightUnit.KG))
        assertEquals(0, Weights.parse("0", WeightUnit.KG))
    }

    @Test fun `parses lb into grams`() {
        assertEquals(45_359, Weights.parse("100", WeightUnit.LB))
    }

    @Test fun `rejects anything that is not a plain in-range number`() {
        listOf("", " ", "abc", "-1", "1e3", "1.2.3", ".", "1.234", "1001", "27.5kg", "1234567890").forEach {
            assertNull("expected null for '$it'", Weights.parse(it, WeightUnit.KG))
        }
    }

    @Test fun `formats without trailing zeros`() {
        assertEquals("27.5", Weights.format(27_500, WeightUnit.KG))
        assertEquals("30", Weights.format(30_000, WeightUnit.KG))
        assertEquals("0", Weights.format(0, WeightUnit.KG))
        assertEquals("100", Weights.format(45_359, WeightUnit.LB))
        assertEquals("66.1", Weights.format(30_000, WeightUnit.LB))
    }

    @Test fun `reps accept 0 to 200 only`() {
        assertEquals(8, Reps.parse("8"))
        assertEquals(0, Reps.parse("0"))
        assertEquals(200, Reps.parse("200"))
        listOf("", "201", "-1", "8.5", "abc", "1000").forEach {
            assertNull("expected null for '$it'", Reps.parse(it))
        }
    }
}
