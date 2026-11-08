package com.workouttracker.data.seed

import com.workouttracker.data.db.ExerciseEntity
import com.workouttracker.data.db.PlannedSetEntity
import com.workouttracker.data.db.RoutineDao
import com.workouttracker.data.db.RoutineEntity
import com.workouttracker.data.db.RoutineExerciseEntity
import com.workouttracker.domain.ExerciseKind

/** Writes [SeedRoutines] into an empty database. Call inside a transaction. */
internal class Seeder(private val dao: RoutineDao) {

    suspend fun insert(routines: List<RoutineSeed>) {
        routines.forEachIndexed { order, routine ->
            val routineId = dao.insertRoutine(RoutineEntity(name = routine.name, rotationOrder = order))
            routine.exercises.forEachIndexed { position, exercise ->
                val exerciseId = exerciseId(exercise.name, exercise.kind)
                val alternativeId = exercise.alternative?.let { (name, kind) -> exerciseId(name, kind) }
                val routineExerciseId = dao.insertRoutineExercise(
                    RoutineExerciseEntity(
                        routineId = routineId,
                        exerciseId = exerciseId,
                        position = position,
                        isRpt = exercise.isRpt,
                        restSec = exercise.restSec,
                        alternativeExerciseId = alternativeId,
                    ),
                )
                dao.insertPlannedSets(
                    exercise.sets.mapIndexed { index, set ->
                        PlannedSetEntity(routineExerciseId, index, set.minReps, set.maxReps, set.note)
                    },
                )
            }
        }
    }

    private suspend fun exerciseId(name: String, kind: ExerciseKind): Long =
        dao.findExerciseByName(name)?.id ?: dao.insertExercise(ExerciseEntity(name = name, kind = kind))
}
