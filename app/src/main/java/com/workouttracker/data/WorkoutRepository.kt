package com.workouttracker.data

import androidx.room.withTransaction
import com.workouttracker.data.db.AppDatabase
import com.workouttracker.data.db.RoutineSummary
import com.workouttracker.data.db.SessionEntity
import com.workouttracker.data.seed.SeedRoutines
import com.workouttracker.data.seed.Seeder
import com.workouttracker.data.settings.SettingsRepository
import com.workouttracker.domain.SessionStats
import com.workouttracker.domain.SessionStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate

/** Result of trying to skip a routine. */
sealed interface SkipResult {
    data class Skipped(val sessionId: Long, val routineName: String) : SkipResult
    /** A workout is in progress; finish or discard it before skipping. */
    data object WorkoutInProgress : SkipResult
    data object RoutineNotFound : SkipResult
}

class WorkoutRepository(
    private val db: AppDatabase,
    private val settings: SettingsRepository,
) {
    private val routineDao = db.routineDao()
    private val sessionDao = db.sessionDao()
    private val seedLock = Mutex()

    /** Loads the five routines from the old sheet the first time the app runs. Safe to call repeatedly. */
    suspend fun ensureSeeded() = seedLock.withLock {
        if (settings.isSeeded()) return@withLock
        db.withTransaction {
            if (routineDao.countRoutines() == 0) Seeder(routineDao).insert(SeedRoutines.all)
        }
        settings.markSeeded()
    }

    fun observeRoutineSummaries(): Flow<List<RoutineSummary>> = routineDao.observeSummaries()

    fun observeLatestRotationEntry(): Flow<SessionEntity?> = sessionDao.observeLatestRotationEntry()

    fun observeLatestEntryPerRoutine(): Flow<List<SessionEntity>> = sessionDao.observeLatestEntryPerRoutine()

    /** Records [routineId] as skipped on [date], which moves the rotation on to the next routine. */
    suspend fun skip(routineId: Long, date: LocalDate): SkipResult = db.withTransaction {
        if (sessionDao.getInProgress() != null) return@withTransaction SkipResult.WorkoutInProgress
        val routine = routineDao.getRoutine(routineId) ?: return@withTransaction SkipResult.RoutineNotFound
        val id = sessionDao.insertSession(
            SessionEntity(
                routineId = routine.id,
                routineName = routine.name,
                status = SessionStatus.SKIPPED,
                date = date,
                startedAt = null,
                finishedAt = null,
            ),
        )
        SkipResult.Skipped(id, routine.name)
    }

    /** Removes a skip. Returns false if [sessionId] is not a skip (real workouts are never deleted here). */
    suspend fun undoSkip(sessionId: Long): Boolean = sessionDao.deleteSkip(sessionId) == 1

    fun observeSessionStats(from: LocalDate, to: LocalDate): Flow<List<SessionStats>> =
        sessionDao.observeStats(from, to).map { rows ->
            rows.map { SessionStats(it.date, it.status, it.startedAt, it.finishedAt, it.workingSets, it.volumeGramReps) }
        }
}
