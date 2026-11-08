package com.workouttracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RotationTest {

    // Upper, Lower, Push, Pull, Legs
    private val order = listOf(1L, 2L, 3L, 4L, 5L)

    @Test fun `no routines means nothing to do`() {
        assertNull(Rotation.next(emptyList(), null))
    }

    @Test fun `fresh install starts with the first routine`() {
        assertEquals(1L, Rotation.next(order, null))
    }

    @Test fun `moves to the routine after the last one done or skipped`() {
        assertEquals(2L, Rotation.next(order, 1L))
        assertEquals(4L, Rotation.next(order, 3L))
    }

    @Test fun `wraps from the last routine back to the first`() {
        assertEquals(1L, Rotation.next(order, 5L))
    }

    @Test fun `restarts if the last routine was deleted`() {
        assertEquals(1L, Rotation.next(order, 99L))
    }

    @Test fun `cycle shows done and skipped before next, later after`() {
        val statuses = mapOf(
            1L to SessionStatus.DONE,
            2L to SessionStatus.SKIPPED,
            4L to SessionStatus.DONE, // from the previous pass; still "later" in this one
        )
        assertEquals(
            listOf(RotationStatus.DONE, RotationStatus.SKIPPED, RotationStatus.NEXT, RotationStatus.LATER, RotationStatus.LATER),
            Rotation.cycle(order, 3L, statuses),
        )
    }

    @Test fun `cycle on fresh install`() {
        assertEquals(
            listOf(RotationStatus.NEXT, RotationStatus.LATER, RotationStatus.LATER, RotationStatus.LATER, RotationStatus.LATER),
            Rotation.cycle(order, 1L, emptyMap()),
        )
    }
}
