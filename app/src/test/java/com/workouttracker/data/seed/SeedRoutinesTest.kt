package com.workouttracker.data.seed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SeedRoutinesTest {

    private val routines = SeedRoutines.all

    @Test fun `five routines in rotation order`() {
        assertEquals(listOf("Upper", "Lower", "Push", "Pull", "Legs"), routines.map { it.name })
    }

    @Test fun `exercise and set counts match the sheet`() {
        assertEquals(listOf(6, 5, 6, 7, 5), routines.map { it.exercises.size })
        assertEquals(listOf(19, 17, 18, 21, 18), routines.map { r -> r.exercises.sumOf { it.sets.size } })
    }

    @Test fun `upper has dumbbell pullover in place of the second lat pulldown`() {
        val upper = routines.first { it.name == "Upper" }.exercises.map { it.name }
        assertEquals(1, upper.count { it == "Lat Pulldown" })
        assertTrue("Dumbbell Pullover" in upper)
    }

    @Test fun `rep ranges are valid`() {
        routines.flatMap { it.exercises }.flatMap { it.sets }.forEach { set ->
            assertTrue(set.minReps > 0)
            set.maxReps?.let { assertTrue("min ${set.minReps} > max $it", set.minReps <= it) }
        }
    }

    @Test fun `exercise names are unique within each routine`() {
        routines.forEach { r ->
            val names = r.exercises.map { it.name }
            assertEquals(r.name, names.size, names.toSet().size)
        }
    }

    @Test fun `rest defaults follow the rep range`() {
        val all = routines.flatMap { it.exercises }.associateBy { it.name }
        assertEquals(180, all.getValue("Bench Press").restSec)       // 4–6
        assertEquals(180, all.getValue("Weighted Pull-Ups").restSec) // RPT, heaviest set 4–6
        assertEquals(120, all.getValue("Hammer Curls").restSec)      // 8–10
        assertEquals(90, all.getValue("Kneeling Face Pulls").restSec) // 10–15
        assertEquals(90, all.getValue("Banded Push-Ups").restSec)    // 10+
    }
}
