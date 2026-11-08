package com.workouttracker.data

import com.workouttracker.data.db.AppDatabase
import com.workouttracker.data.db.ExerciseHistoryRow
import com.workouttracker.domain.ExerciseSession
import com.workouttracker.domain.PerformedSet
import com.workouttracker.domain.Segment
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class ProgressRepository(private val db: AppDatabase) {
    private val sets = db.loggedSetDao()
    private val routines = db.routineDao()

    /** Exercises with finished workouts, most recently trained first. */
    fun observeExercises(): Flow<List<ExerciseHistoryRow>> = sets.observeExercisesWithHistory()

    /** Every finished workout's sets for one exercise (history is filed under the planned exercise). */
    fun observeSessions(exerciseId: Long): Flow<List<ExerciseSession>> =
        combine(sets.observeExerciseSets(exerciseId), sets.observeExerciseSegments(exerciseId)) { rows, segments ->
            val bySet = segments.groupBy({ it.loggedSetId }) { Segment(it.reps, it.weightGrams) }
            rows.groupBy { it.sessionId }.map { (sessionId, setRows) ->
                ExerciseSession(
                    sessionId = sessionId,
                    date = setRows.first().date,
                    sets = setRows.map { r ->
                        PerformedSet(r.loggedSetId, r.setIndex, r.type, r.note, bySet[r.loggedSetId].orEmpty(), r.performedExerciseId)
                    },
                )
            }
        }

    suspend fun exerciseName(exerciseId: Long): String? = routines.getExercises(listOf(exerciseId)).firstOrNull()?.name
}
