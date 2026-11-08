package com.workouttracker.data

import androidx.room.withTransaction
import com.workouttracker.data.db.AppDatabase
import com.workouttracker.data.db.ExerciseEntity
import com.workouttracker.data.db.LoggedSetEntity
import com.workouttracker.data.db.SessionEntity
import com.workouttracker.data.db.SetSegmentEntity
import com.workouttracker.domain.ExerciseKind
import com.workouttracker.domain.LiveSessionRules
import com.workouttracker.domain.PerformedSet
import com.workouttracker.domain.RoutineEditing
import com.workouttracker.domain.Segment
import com.workouttracker.domain.SessionStatus
import com.workouttracker.domain.SetTarget
import com.workouttracker.domain.SetType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Instant
import java.time.LocalDate

data class ExerciseRef(val id: Long, val name: String, val kind: ExerciseKind)

/** One exercise of a workout as planned by its routine, with what was done last time. */
data class PlanExercise(
    val exercise: ExerciseRef,
    val isRpt: Boolean,
    val restSec: Int,
    val targets: List<SetTarget>,
    val alternative: ExerciseRef?,
    val lastTime: List<PerformedSet>,
)

data class SessionPlan(val session: SessionEntity, val exercises: List<PlanExercise>)

sealed interface StartResult {
    data class Started(val sessionId: Long) : StartResult
    /** Only one workout can be open at a time. */
    data class AlreadyInProgress(val sessionId: Long) : StartResult
    data object RoutineNotFound : StartResult
}

/** Thrown when a write would break a rule the screen should already enforce. */
class SessionRejected(message: String) : IllegalArgumentException(message)

class SessionRepository(private val db: AppDatabase) {
    private val sessions = db.sessionDao()
    private val sets = db.loggedSetDao()
    private val routines = db.routineDao()

    fun observeInProgress(): Flow<SessionEntity?> = sessions.observeInProgress()

    suspend fun inProgress(): SessionEntity? = sessions.getInProgress()

    fun observeSession(id: Long): Flow<SessionEntity?> = sessions.observeSession(id)

    suspend fun start(routineId: Long, date: LocalDate, now: Instant): StartResult = db.withTransaction {
        sessions.getInProgress()?.let { return@withTransaction StartResult.AlreadyInProgress(it.id) }
        val routine = routines.getRoutine(routineId) ?: return@withTransaction StartResult.RoutineNotFound
        val id = sessions.insertSession(
            SessionEntity(
                routineId = routine.id,
                routineName = routine.name,
                status = SessionStatus.IN_PROGRESS,
                date = date,
                startedAt = now,
                finishedAt = null,
            ),
        )
        StartResult.Started(id)
    }

    /**
     * The workout's plan from its routine as it is now, plus last time's sets per exercise.
     * Returns null if the session doesn't exist.
     */
    suspend fun loadPlan(sessionId: Long): SessionPlan? = db.withTransaction {
        val session = sessions.getSession(sessionId) ?: return@withTransaction null
        val routineId = session.routineId ?: return@withTransaction SessionPlan(session, emptyList())
        val rows = routines.getRoutineExercises(routineId)
        val targetsByRow = routines.getPlannedSets(rows.map { it.id }).groupBy { it.routineExerciseId }
        val exerciseIds = rows.flatMap { listOfNotNull(it.exerciseId, it.alternativeExerciseId) }.distinct()
        val exercises = routines.getExercises(exerciseIds).associateBy { it.id }
        SessionPlan(
            session = session,
            exercises = rows.mapNotNull { row ->
                val exercise = exercises[row.exerciseId] ?: return@mapNotNull null
                PlanExercise(
                    exercise = exercise.toRef(),
                    isRpt = row.isRpt,
                    restSec = row.restSec,
                    targets = targetsByRow[row.id].orEmpty().map { SetTarget(it.minReps, it.maxReps, it.note) },
                    alternative = row.alternativeExerciseId?.let { exercises[it]?.toRef() },
                    lastTime = lastTime(exercise.id, sessionId),
                )
            },
        )
    }

    /** Sets logged in this workout, live. */
    fun observeLogged(sessionId: Long): Flow<Map<Long, List<PerformedSet>>> =
        combine(sets.observeSets(sessionId), sets.observeSegments(sessionId)) { rows, segments ->
            val bySet = segments.groupBy { it.loggedSetId }
            rows.groupBy({ it.plannedExerciseId }) { it.toPerformed(bySet[it.id].orEmpty()) }
        }

    /** Logs (or re-logs) one set. Validation runs here too, so nothing malformed is stored. */
    suspend fun logSet(
        sessionId: Long,
        plannedExerciseId: Long,
        performedExerciseId: Long,
        setIndex: Int,
        type: SetType,
        note: String?,
        segments: List<Segment>,
        now: Instant,
    ) {
        if (!LiveSessionRules.isValid(segments)) throw SessionRejected("invalid set")
        if (setIndex !in 0 until RoutineEditing.MAX_SETS + LiveSessionRules.MAX_EXTRA_SETS) throw SessionRejected("set index out of range")
        db.withTransaction {
            requireInProgress(sessionId)
            val cleanNote = LiveSessionRules.cleanNote(note)
            val existing = sets.findSet(sessionId, plannedExerciseId, setIndex)
            val id = if (existing == null) {
                sets.insertSet(
                    LoggedSetEntity(
                        sessionId = sessionId,
                        plannedExerciseId = plannedExerciseId,
                        performedExerciseId = performedExerciseId,
                        setIndex = setIndex,
                        type = type,
                        note = cleanNote,
                        completedAt = now,
                    ),
                )
            } else {
                sets.updateSet(existing.id, performedExerciseId, type, cleanNote, now)
                sets.deleteSegments(existing.id)
                existing.id
            }
            sets.insertSegments(segments.mapIndexed { i, s -> SetSegmentEntity(id, i, s.reps, s.weightGrams) })
        }
    }

    suspend fun unlogSet(sessionId: Long, loggedSetId: Long) = db.withTransaction {
        requireInProgress(sessionId)
        sets.deleteSet(loggedSetId, sessionId)
    }

    /** Swaps an exercise for the rest of this workout; sets already logged move with it. */
    suspend fun swap(sessionId: Long, plannedExerciseId: Long, performedExerciseId: Long) = db.withTransaction {
        requireInProgress(sessionId)
        sets.setPerformedExercise(sessionId, plannedExerciseId, performedExerciseId)
    }

    /** Finds an exercise by name (case-insensitive) or creates it. Used when swapping to something new. */
    suspend fun findOrCreateExercise(name: String, kind: ExerciseKind): ExerciseRef {
        val clean = name.trim()
        if (clean.isEmpty() || clean.length > RoutineEditing.MAX_EXERCISE_NAME) throw SessionRejected("invalid exercise name")
        return db.withTransaction {
            val found = routines.findExerciseByName(clean)
            found?.toRef() ?: ExerciseRef(routines.insertExercise(ExerciseEntity(name = clean, kind = kind)), clean, kind)
        }
    }

    suspend fun finish(sessionId: Long, now: Instant): Boolean = sessions.finish(sessionId, now) == 1

    suspend fun discard(sessionId: Long): Boolean = sessions.discardInProgress(sessionId) == 1

    private suspend fun lastTime(exerciseId: Long, currentSessionId: Long): List<PerformedSet> {
        val lastSession = sets.lastSessionWith(exerciseId, currentSessionId) ?: return emptyList()
        val rows = sets.setsFor(lastSession, exerciseId)
        val segments = sets.segmentsFor(rows.map { it.id }).groupBy { it.loggedSetId }
        return rows.map { it.toPerformed(segments[it.id].orEmpty()) }
    }

    private suspend fun requireInProgress(sessionId: Long) {
        val session = sessions.getSession(sessionId)
        if (session?.status != SessionStatus.IN_PROGRESS) throw SessionRejected("workout is not in progress")
    }

    private fun ExerciseEntity.toRef() = ExerciseRef(id, name, kind)

    private fun LoggedSetEntity.toPerformed(segments: List<SetSegmentEntity>) = PerformedSet(
        id = id,
        setIndex = setIndex,
        type = type,
        note = note,
        segments = segments.sortedBy { it.position }.map { Segment(it.reps, it.weightGrams) },
        performedExerciseId = performedExerciseId,
    )
}
