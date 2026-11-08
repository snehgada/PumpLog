package com.workouttracker.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class BadgesTest {

    @Test fun `split routines get distinct badges`() {
        assertEquals(
            listOf("U", "Lo", "Pu", "Pl", "Le"),
            Badges.labels(listOf("Upper", "Lower", "Push", "Pull", "Legs")),
        )
    }

    @Test fun `unique initials stay single letters`() {
        assertEquals(listOf("A", "B"), Badges.labels(listOf("Arms", "Back")))
    }

    @Test fun `blank names get an empty badge`() {
        assertEquals(listOf("", "C"), Badges.labels(listOf("  ", "Core")))
    }
}
