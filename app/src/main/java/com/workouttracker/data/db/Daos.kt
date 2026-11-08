package com.workouttracker.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.workouttracker.domain.SessionStatus
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

/** A routine with the counts shown on Today and in the routine list. */
data class RoutineSummary(
    val id: Long,
    val name: String,
    val rotationOrder: Int,
    val exerciseCount: Int,
    val setCount: Int,
)

/** Row backing [com.workouttracker.domain.SessionStats]. */
data class SessionStatsRow(
    val sessionId: Long,
    val date: LocalDate,
    val status: SessionStatus,
    val startedAt: Instant?,
    val finishedAt: Instant?,
    val workingSets: Int,
    val volumeGramReps: Long,
)

@Dao
interface RoutineDao {

    @Query(
        """
        SELECT r.id, r.name, r.rotationOrder,
            (SELECT COUNT(*) FROM routine_exercise re WHERE re.routineId = r.id) AS exerciseCount,
            (SELECT COUNT(*) FROM planned_set ps
                JOIN routine_exercise re ON ps.routineExerciseId = re.id
                WHERE re.routineId = r.id) AS setCount
        FROM routine r
        ORDER BY r.rotationOrder
        """,
    )
    fun observeSummaries(): Flow<List<RoutineSummary>>

    @Query("SELECT * FROM routine WHERE id = :id")
    suspend fun getRoutine(id: Long): RoutineEntity?

    @Query("SELECT * FROM routine ORDER BY rotationOrder, id")
    suspend fun getRoutines(): List<RoutineEntity>

    @Query("SELECT COUNT(*) FROM routine")
    suspend fun countRoutines(): Int

    /** Exercise names per routine, in order. Used for previews such as the First launch screen. */
    @Query(
        """
        SELECT re.routineId AS routineId, e.name AS name
        FROM routine_exercise re JOIN exercise e ON e.id = re.exerciseId
        ORDER BY re.routineId, re.position
        """,
    )
    fun observeRoutineExerciseNames(): Flow<List<RoutineExerciseName>>

    @Query("SELECT * FROM routine_exercise WHERE routineId = :routineId ORDER BY position")
    suspend fun getRoutineExercises(routineId: Long): List<RoutineExerciseEntity>

    @Query("SELECT * FROM planned_set WHERE routineExerciseId IN (:routineExerciseIds) ORDER BY routineExerciseId, setIndex")
    suspend fun getPlannedSets(routineExerciseIds: List<Long>): List<PlannedSetEntity>

    @Query("SELECT * FROM exercise WHERE id IN (:ids)")
    suspend fun getExercises(ids: List<Long>): List<ExerciseEntity>

    @Query("SELECT * FROM exercise ORDER BY name COLLATE NOCASE")
    fun observeExercises(): Flow<List<ExerciseEntity>>

    /** Case-insensitive, so "bench press" reuses "Bench Press" instead of creating a duplicate. */
    @Query("SELECT * FROM exercise WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findExerciseByName(name: String): ExerciseEntity?

    @Query("UPDATE routine SET name = :name WHERE id = :id")
    suspend fun renameRoutine(id: Long, name: String)

    @Query("UPDATE routine SET rotationOrder = :order WHERE id = :id")
    suspend fun setRotationOrder(id: Long, order: Int)

    @Query("DELETE FROM routine WHERE id = :id")
    suspend fun deleteRoutine(id: Long): Int

    /** Planned sets go with them (cascade). Logged history refers to exercises, not these rows, so it is untouched. */
    @Query("DELETE FROM routine_exercise WHERE routineId = :routineId")
    suspend fun deleteRoutineExercises(routineId: Long)

    @Insert suspend fun insertExercise(exercise: ExerciseEntity): Long
    @Insert suspend fun insertRoutine(routine: RoutineEntity): Long
    @Insert suspend fun insertRoutineExercise(routineExercise: RoutineExerciseEntity): Long
    @Insert suspend fun insertPlannedSets(sets: List<PlannedSetEntity>)
}

data class RoutineExerciseName(val routineId: Long, val name: String)

@Dao
interface SessionDao {

    @Insert suspend fun insertSession(session: SessionEntity): Long

    /** The session that decides what comes next in the rotation. */
    @Query(
        """
        SELECT * FROM session
        WHERE status IN ('DONE', 'SKIPPED')
        ORDER BY date DESC, id DESC
        LIMIT 1
        """,
    )
    fun observeLatestRotationEntry(): Flow<SessionEntity?>

    /** For each routine, its most recent done-or-skipped session. */
    @Query(
        """
        SELECT * FROM session s
        WHERE s.routineId IS NOT NULL
          AND s.id = (
            SELECT s2.id FROM session s2
            WHERE s2.routineId = s.routineId AND s2.status IN ('DONE', 'SKIPPED')
            ORDER BY s2.date DESC, s2.id DESC
            LIMIT 1
          )
        """,
    )
    fun observeLatestEntryPerRoutine(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM session WHERE status = 'IN_PROGRESS' LIMIT 1")
    suspend fun getInProgress(): SessionEntity?

    /** Only skips can be undone this way; real workouts are never deleted by it. */
    @Query("DELETE FROM session WHERE id = :sessionId AND status = 'SKIPPED'")
    suspend fun deleteSkip(sessionId: Long): Int

    @Query(
        """
        SELECT s.id AS sessionId, s.date, s.status, s.startedAt, s.finishedAt,
            (SELECT COUNT(*) FROM logged_set ls
                WHERE ls.sessionId = s.id AND ls.type != 'WARMUP') AS workingSets,
            (SELECT COALESCE(SUM(seg.reps * seg.weightGrams), 0) FROM logged_set ls
                JOIN set_segment seg ON seg.loggedSetId = ls.id
                WHERE ls.sessionId = s.id AND ls.type != 'WARMUP') AS volumeGramReps
        FROM session s
        WHERE s.date BETWEEN :from AND :to
        ORDER BY s.date, s.id
        """,
    )
    fun observeStats(from: LocalDate, to: LocalDate): Flow<List<SessionStatsRow>>
}
