package com.workouttracker.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.workouttracker.domain.ExerciseKind
import com.workouttracker.domain.SessionStatus
import com.workouttracker.domain.SetType
import java.time.Instant
import java.time.LocalDate

@Entity(
    tableName = "exercise",
    indices = [Index(value = ["name"], unique = true)],
)
data class ExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val kind: ExerciseKind,
)

@Entity(tableName = "routine")
data class RoutineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Position in the fixed rotation, starting at 0. */
    val rotationOrder: Int,
)

@Entity(
    tableName = "routine_exercise",
    foreignKeys = [
        ForeignKey(entity = RoutineEntity::class, parentColumns = ["id"], childColumns = ["routineId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = ExerciseEntity::class, parentColumns = ["id"], childColumns = ["exerciseId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = ExerciseEntity::class, parentColumns = ["id"], childColumns = ["alternativeExerciseId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("routineId"), Index("exerciseId"), Index("alternativeExerciseId")],
)
data class RoutineExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val routineId: Long,
    val exerciseId: Long,
    val position: Int,
    /** Reverse pyramid training: each set has its own rep range and usually a lighter weight. */
    val isRpt: Boolean,
    val restSec: Int,
    val alternativeExerciseId: Long?,
)

@Entity(
    tableName = "planned_set",
    primaryKeys = ["routineExerciseId", "setIndex"],
    foreignKeys = [
        ForeignKey(entity = RoutineExerciseEntity::class, parentColumns = ["id"], childColumns = ["routineExerciseId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class PlannedSetEntity(
    val routineExerciseId: Long,
    val setIndex: Int,
    val minReps: Int,
    /** Null for open-ended targets such as "10+". */
    val maxReps: Int?,
    val note: String?,
)

@Entity(
    tableName = "session",
    foreignKeys = [
        // Deleting a routine keeps its history; the name is kept on the session itself.
        ForeignKey(entity = RoutineEntity::class, parentColumns = ["id"], childColumns = ["routineId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("routineId"), Index("date"), Index("status")],
)
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val routineId: Long?,
    val routineName: String,
    val status: SessionStatus,
    /** The day the workout counts for (local date). */
    val date: LocalDate,
    /** Null for skips and for past workouts logged without times. */
    val startedAt: Instant?,
    val finishedAt: Instant?,
)

@Entity(
    tableName = "logged_set",
    foreignKeys = [
        ForeignKey(entity = SessionEntity::class, parentColumns = ["id"], childColumns = ["sessionId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = ExerciseEntity::class, parentColumns = ["id"], childColumns = ["plannedExerciseId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = ExerciseEntity::class, parentColumns = ["id"], childColumns = ["performedExerciseId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [
        Index("sessionId"),
        Index("plannedExerciseId"),
        Index("performedExerciseId"),
        // One row per set of each exercise in a workout (added in schema v2).
        Index(value = ["sessionId", "plannedExerciseId", "setIndex"], unique = true),
    ],
)
data class LoggedSetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    /** The exercise in the routine. History is filed under this one. */
    val plannedExerciseId: Long,
    /** Differs from [plannedExerciseId] when the exercise was swapped for the day. */
    val performedExerciseId: Long,
    val setIndex: Int,
    val type: SetType,
    val note: String?,
    val completedAt: Instant,
)

/** One weight × reps block of a set. A normal set has one; a drop set has two or more. */
@Entity(
    tableName = "set_segment",
    primaryKeys = ["loggedSetId", "position"],
    foreignKeys = [
        ForeignKey(entity = LoggedSetEntity::class, parentColumns = ["id"], childColumns = ["loggedSetId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class SetSegmentEntity(
    val loggedSetId: Long,
    val position: Int,
    val reps: Int,
    val weightGrams: Int,
)
