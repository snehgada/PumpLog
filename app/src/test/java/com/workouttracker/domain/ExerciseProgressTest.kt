package com.workouttracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class ExerciseProgressTest {

    private var nextId = 1L
    private fun set(reps: Int, kg: Double, type: SetType = SetType.NORMAL, vararg drops: Pair<Int, Double>) =
        PerformedSet(
            nextId++, 0, type, null,
            listOf(Segment(reps, (kg * 1000).toInt())) + drops.map { (r, w) -> Segment(r, (w * 1000).toInt()) },
            performedExerciseId = 1,
        )

    private fun session(id: Long, day: Int, vararg sets: PerformedSet) =
        ExerciseSession(id, LocalDate.of(2026, 10, day), sets.toList())

    // Bench Press, sessions 1–4 from the old sheet.
    private val bench = listOf(
        session(4, 26, set(8, 25.0), set(10, 27.5), set(7, 30.0)),
        session(1, 5, set(10, 20.0), set(8, 25.0), set(7, 27.5)),
        session(2, 12, set(10, 15.0), set(8, 25.0), set(8, 27.5)),
        session(3, 19, set(10, 25.0), set(8, 27.5), set(7, 27.5)),
    )

    @Test fun `points are oldest first with the sheet's numbers`() {
        val points = ExerciseProgress.summarize(bench).points
        assertEquals(listOf(1L, 2L, 3L, 4L), points.map { it.sessionId })
        assertEquals(listOf(33_917, 34_833, 34_833, 37_000), points.map { it.e1rmGrams })
        assertEquals(listOf(27_500, 27_500, 27_500, 30_000), points.map { it.topSetGrams })
        // Volume in gram-reps: 592.5, 570, 662.5 and 685 kg.
        assertEquals(listOf(592_500L, 570_000L, 662_500L, 685_000L), points.map { it.volumeGramReps })
    }

    @Test fun `best set and best volume`() {
        val summary = ExerciseProgress.summarize(bench)
        assertEquals(BestSet(7, 30_000, LocalDate.of(2026, 10, 26), 4), summary.bestSet)
        assertEquals(4L, summary.bestVolume?.sessionId)
    }

    @Test fun `warm-ups are ignored and drop blocks count toward volume only`() {
        val s = session(9, 1, set(12, 10.0, SetType.WARMUP), set(5, 35.0, SetType.DROP, 4 to 30.0))
        val point = ExerciseProgress.summarize(listOf(s)).points.single()
        assertEquals(35_000, point.topSetGrams)
        assertEquals(5L * 35_000 + 4L * 30_000, point.volumeGramReps)
    }

    @Test fun `a session with only warm-ups is left out`() {
        val summary = ExerciseProgress.summarize(listOf(session(9, 1, set(12, 10.0, SetType.WARMUP))))
        assertEquals(emptyList<ProgressPoint>(), summary.points)
        assertNull(summary.bestSet)
        assertNull(summary.bestVolume)
    }

    @Test fun `volume labels`() {
        assertEquals("685 kg", ExerciseProgress.formatVolume(685_000L, WeightUnit.KG, Locale.US))
        assertEquals("22.0 t", ExerciseProgress.formatVolume(21_980_000L, WeightUnit.KG, Locale.US)) // 21,980 kg
        assertEquals("1,510 lb", ExerciseProgress.formatVolume(685_000L, WeightUnit.LB, Locale.US))
    }
}
