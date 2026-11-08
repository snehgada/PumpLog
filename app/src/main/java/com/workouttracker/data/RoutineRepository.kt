package com.workouttracker.data

import androidx.room.withTransaction
import com.workouttracker.data.db.AppDatabase
import com.workouttracker.data.db.ExerciseEntity
import com.workouttracker.data.db.PlannedSetEntity
import com.workouttracker.data.db.RoutineEntity
import com.workouttracker.data.db.RoutineExerciseEntity
import com.workouttracker.data.db.RoutineExerciseName
import com.workouttracker.domain.ExerciseDraft
import com.workouttracker.domain.ExerciseKind
import com.workouttracker.domain.RoutineDraft
import com.workouttracker.domain.RoutineEditing
import com.workouttracker.domain.SetDraft
import kotlinx.coroutines.flow.Flow

/** Thrown when a save would break a rule the editor should already have enforced. */
class RoutineRejected(message: String) : IllegalArgumentException(message)

sealed interface RenameResult {
    data class Renamed(val name: String) : RenameResult
    /** Another exercise already has this name (ignoring case). */
    data object NameTaken : RenameResult
    data object Invalid : RenameResult
    data object NotFound : RenameResult
}

class RoutineRepository(private val db: AppDatabase) {
    private val dao = db.routineDao()

    fun observeExercises(): Flow<List<ExerciseEntity>> = dao.observeExercises()

    fun observeRoutineExerciseNames(): Flow<List<RoutineExerciseName>> = dao.observeRoutineExerciseNames()

    /** Loads a routine for editing, or null if it no longer exists. [nextKey] hands out list keys. */
    suspend fun loadDraft(routineId: Long, nextKey: () -> Long): RoutineDraft? = db.withTransaction {
        val routine = dao.getRoutine(routineId) ?: return@withTransaction null
        val rows = dao.getRoutineExercises(routineId)
        val setsByRow = dao.getPlannedSets(rows.map { it.id }).groupBy { it.routineExerciseId }
        val exerciseIds = rows.flatMap { listOfNotNull(it.exerciseId, it.alternativeExerciseId) }.distinct()
        val exercises = dao.getExercises(exerciseIds).associateBy { it.id }
        RoutineDraft(
            routineId = routine.id,
            name = routine.name,
            exercises = rows.mapNotNull { row ->
                val exercise = exercises[row.exerciseId] ?: return@mapNotNull null
                ExerciseDraft(
                    key = nextKey(),
                    exerciseId = exercise.id,
                    name = exercise.name,
                    kind = exercise.kind,
                    isRpt = row.isRpt,
                    restSec = row.restSec,
                    sets = setsByRow[row.id].orEmpty().map { SetDraft(it.minReps.toString(), it.maxReps?.toString().orEmpty(), it.note) },
                    alternativeName = row.alternativeExerciseId?.let { exercises[it]?.name }.orEmpty(),
                )
            },
        )
    }

    suspend fun countRoutines(): Int = dao.countRoutines()

    /**
     * Renames an exercise everywhere: every routine that uses it and all logged history (which refers to it by id).
     * Refuses a name another exercise already has, rather than silently merging the two.
     */
    suspend fun renameExercise(exerciseId: Long, newName: String): RenameResult {
        val name = RoutineEditing.cleanExerciseName(newName) ?: return RenameResult.Invalid
        return db.withTransaction {
            dao.getExercises(listOf(exerciseId)).firstOrNull() ?: return@withTransaction RenameResult.NotFound
            val clash = dao.findExerciseByName(name)
            if (clash != null && clash.id != exerciseId) return@withTransaction RenameResult.NameTaken
            dao.renameExercise(exerciseId, name)
            RenameResult.Renamed(name)
        }
    }

    /**
     * Validates and stores [draft], replacing the routine's exercise list. Returns the routine id.
     * Validation runs here too, so nothing invalid reaches the database even if the UI misses something.
     */
    suspend fun save(draft: RoutineDraft): Long {
        val plan = RoutineEditing.toPlan(draft) ?: throw RoutineRejected("routine has validation issues")
        return db.withTransaction {
            val routineId = if (plan.routineId == null) {
                val count = dao.countRoutines()
                if (count >= RoutineEditing.MAX_ROUTINES) throw RoutineRejected("too many routines")
                dao.insertRoutine(RoutineEntity(name = plan.name, rotationOrder = count))
            } else {
                dao.getRoutine(plan.routineId) ?: throw RoutineRejected("routine no longer exists")
                dao.renameRoutine(plan.routineId, plan.name)
                plan.routineId
            }
            dao.deleteRoutineExercises(routineId)
            plan.exercises.forEachIndexed { position, ex ->
                val exerciseId = resolveExercise(ex.exerciseId, ex.name, ex.kind)
                val alternativeId = ex.alternativeName?.let { resolveExercise(null, it, ex.kind) }
                val rowId = dao.insertRoutineExercise(
                    RoutineExerciseEntity(
                        routineId = routineId,
                        exerciseId = exerciseId,
                        position = position,
                        isRpt = ex.isRpt,
                        restSec = ex.restSec,
                        alternativeExerciseId = alternativeId,
                    ),
                )
                dao.insertPlannedSets(ex.sets.mapIndexed { i, s -> PlannedSetEntity(rowId, i, s.minReps, s.maxReps, s.note) })
            }
            routineId
        }
    }

    /** Moves a routine one place earlier (-1) or later (+1) in the rotation. */
    suspend fun move(routineId: Long, delta: Int) = db.withTransaction {
        val routines = dao.getRoutines()
        val from = routines.indexOfFirst { it.id == routineId }
        renumber(RoutineEditing.move(routines, from, delta))
    }

    /** Deletes a routine; its past sessions stay in history. The last remaining routine cannot be deleted. */
    suspend fun delete(routineId: Long): Boolean = db.withTransaction {
        val routines = dao.getRoutines()
        if (routines.size <= 1 || routines.none { it.id == routineId }) return@withTransaction false
        dao.deleteRoutine(routineId)
        renumber(routines.filter { it.id != routineId })
        true
    }

    private suspend fun renumber(ordered: List<RoutineEntity>) {
        ordered.forEachIndexed { index, routine ->
            if (routine.rotationOrder != index) dao.setRotationOrder(routine.id, index)
        }
    }

    private suspend fun resolveExercise(id: Long?, name: String, kind: ExerciseKind): Long {
        if (id != null) {
            val existing = dao.getExercises(listOf(id)).firstOrNull()
            if (existing != null) return existing.id
        }
        return dao.findExerciseByName(name)?.id ?: dao.insertExercise(ExerciseEntity(name = name, kind = kind))
    }
}
