package com.workouttracker.data

import androidx.room.withTransaction
import com.workouttracker.data.db.AppDatabase
import com.workouttracker.data.db.RoutineSummary
import com.workouttracker.data.db.SessionEntity
import com.workouttracker.data.db.SessionStatsRow
import com.workouttracker.data.seed.SeedRoutines
import com.workouttracker.data.seed.Seeder
import com.workouttracker.data.settings.SettingsRepository
import com.workouttracker.domain.Rotation
import com.workouttracker.domain.SessionStats
import com.workouttracker.domain.SessionStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate

fun SessionStatsRow.toStats() = SessionStats(date, status, startedAt, finishedAt, workingSets, volumeGramReps)

/** The next routine and its 0-based place in a rotation of [total] routines. */
data class NextUp(val routine: RoutineSummary, val position: Int, val total: Int)

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

    /** The routine next in the rotation right now, with where the rotation stands, or null if there are none. */
    suspend fun nextInRotation(): NextUp? {
        val routines = routineDao.observeSummaries().first()
        val latest = sessionDao.observeLatestRotationEntry().first()
        val nextId = Rotation.next(routines.map { it.id }, latest?.routineId) ?: return null
        val index = routines.indexOfFirst { it.id == nextId }
        return NextUp(routines[index], position = index, total = routines.size)
    }

    suspend fun workingSetsLogged(sessionId: Long): Int = db.loggedSetDao().countWorkingSets(sessionId)

    fun observeTotalSets(): Flow<Int> = db.loggedSetDao().observeTotalSets()

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
        observeSessionRows(from, to).map { rows -> rows.map { it.toStats() } }

    /** Every workout and skip between two dates (inclusive), with per-workout sets and volume. */
    fun observeSessionRows(from: LocalDate, to: LocalDate): Flow<List<SessionStatsRow>> = sessionDao.observeStats(from, to)
}
