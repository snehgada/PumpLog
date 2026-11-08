package com.workouttracker.domain

/** Where each routine stands in the current pass through the split. */
enum class RotationStatus { DONE, SKIPPED, NEXT, LATER }

object Rotation {

    /**
     * The routine to do next: the one after [lastRoutineId] in [routineIdsInOrder], wrapping around.
     * [lastRoutineId] is the routine of the most recent DONE or SKIPPED session, or null if there is none.
     * If that routine no longer exists, the rotation restarts from the first routine.
     */
    fun next(routineIdsInOrder: List<Long>, lastRoutineId: Long?): Long? {
        if (routineIdsInOrder.isEmpty()) return null
        val index = routineIdsInOrder.indexOf(lastRoutineId)
        if (index < 0) return routineIdsInOrder.first()
        return routineIdsInOrder[(index + 1) % routineIdsInOrder.size]
    }

    /**
     * Status of every routine for the rotation strip on Today.
     * Routines before the next one show how they were last finished (done or skipped); the rest are still to come.
     */
    fun cycle(
        routineIdsInOrder: List<Long>,
        nextRoutineId: Long?,
        lastStatusByRoutine: Map<Long, SessionStatus>,
    ): List<RotationStatus> {
        val nextIndex = routineIdsInOrder.indexOf(nextRoutineId)
        return routineIdsInOrder.mapIndexed { index, id ->
            when {
                index == nextIndex -> RotationStatus.NEXT
                index < nextIndex -> when (lastStatusByRoutine[id]) {
                    SessionStatus.DONE -> RotationStatus.DONE
                    SessionStatus.SKIPPED -> RotationStatus.SKIPPED
                    else -> RotationStatus.LATER
                }
                else -> RotationStatus.LATER
            }
        }
    }
}
