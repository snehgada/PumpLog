package com.workouttracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutineEditingTest {

    private fun exercise(
        key: Long = 1,
        name: String = "Bench Press",
        sets: List<SetDraft> = List(3) { SetDraft("4", "6") },
        alternative: String = "",
    ) = ExerciseDraft(key, null, name, ExerciseKind.BARBELL, isRpt = false, restSec = 180, sets = sets, alternativeName = alternative)

    private fun routine(name: String = "Upper", exercises: List<ExerciseDraft> = listOf(exercise())) =
        RoutineDraft(routineId = null, name = name, exercises = exercises)

    @Test fun `a normal routine is valid and converts to a plan`() {
        val plan = RoutineEditing.toPlan(routine(name = "  Upper  "))
        assertNotNull(plan)
        assertEquals("Upper", plan!!.name)
        assertEquals(PlannedSet(4, 6, null), plan.exercises.single().sets.first())
    }

    @Test fun `routine name must be present and short`() {
        assertEquals(NameIssue.BLANK, RoutineEditing.validate(routine(name = "   ")).name)
        assertEquals(NameIssue.TOO_LONG, RoutineEditing.validate(routine(name = "x".repeat(41))).name)
        assertNull(RoutineEditing.toPlan(routine(name = "")))
    }

    @Test fun `a routine needs at least one exercise`() {
        assertTrue(RoutineEditing.validate(routine(exercises = emptyList())).noExercises)
    }

    @Test fun `set targets are checked`() {
        val ex = exercise(
            sets = listOf(
                SetDraft("", "6"),     // missing min
                SetDraft("8", "6"),    // max below min
                SetDraft("8", "999"),  // max out of range
                SetDraft("10", ""),    // open-ended: fine
            ),
        )
        val issues = RoutineEditing.validate(routine(exercises = listOf(ex)))
        assertEquals(
            mapOf(0 to SetIssue.MIN_INVALID, 1 to SetIssue.MAX_BELOW_MIN, 2 to SetIssue.MAX_INVALID),
            issues.sets[ex.key],
        )
        assertFalse(issues.isValid)
    }

    @Test fun `open-ended sets store a null max`() {
        val plan = RoutineEditing.toPlan(routine(exercises = listOf(exercise(sets = listOf(SetDraft("10", ""))))))
        assertEquals(PlannedSet(10, null, null), plan!!.exercises.single().sets.single())
    }

    @Test fun `alternative cannot be the exercise itself`() {
        val issues = RoutineEditing.validate(routine(exercises = listOf(exercise(name = "Seated Row", alternative = "seated row"))))
        assertEquals(ExerciseIssue.ALTERNATIVE_SAME_AS_EXERCISE, issues.exercises[1L])
    }

    @Test fun `rep input keeps digits only, three at most`() {
        assertEquals("12", RoutineEditing.filterRepInput("1a2"))
        assertEquals("100", RoutineEditing.filterRepInput("1000"))
        assertNull(RoutineEditing.parseReps("0"))
        assertNull(RoutineEditing.parseReps("101"))
        assertEquals(100, RoutineEditing.parseReps("100"))
    }

    @Test fun `rest moves in 15 second steps between 15 s and 10 min`() {
        assertEquals(195, RoutineEditing.adjustRest(180, 1))
        assertEquals(15, RoutineEditing.adjustRest(15, -1))
        assertEquals(600, RoutineEditing.adjustRest(600, 1))
    }

    @Test fun `move swaps neighbours and ignores moves off the ends`() {
        val list = listOf("a", "b", "c")
        assertEquals(listOf("b", "a", "c"), RoutineEditing.move(list, 1, -1))
        assertEquals(listOf("a", "c", "b"), RoutineEditing.move(list, 1, 1))
        assertEquals(list, RoutineEditing.move(list, 0, -1))
        assertEquals(list, RoutineEditing.move(list, 2, 1))
    }

    @Test fun `adding a set copies the last one, removing keeps at least one`() {
        val ex = exercise(sets = listOf(SetDraft("4", "6", note = "top set"), SetDraft("6", "8")))
        assertEquals(SetDraft("6", "8"), RoutineEditing.addSet(ex).sets.last())
        val single = exercise(sets = listOf(SetDraft("4", "6")))
        assertEquals(single, RoutineEditing.removeSet(single, 0))
        val full = exercise(sets = List(RoutineEditing.MAX_SETS) { SetDraft("5", "5") })
        assertEquals(full, RoutineEditing.addSet(full))
    }

    @Test fun `scheme text`() {
        assertEquals("3 × 8–10", RoutineEditing.scheme(List(3) { SetDraft("8", "10") }))
        assertEquals("2 × 10+", RoutineEditing.scheme(List(2) { SetDraft("10", "") }))
        assertEquals("4–6 / 6–8 / 8–10", RoutineEditing.scheme(listOf(SetDraft("4", "6"), SetDraft("6", "8"), SetDraft("8", "10"))))
    }
}
