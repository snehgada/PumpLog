package com.workouttracker.data.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        ExerciseEntity::class,
        RoutineEntity::class,
        RoutineExerciseEntity::class,
        PlannedSetEntity::class,
        SessionEntity::class,
        LoggedSetEntity::class,
        SetSegmentEntity::class,
    ],
    version = 2,
    exportSchema = true,
    autoMigrations = [
        // v2: unique (session, exercise, set) index on logged_set.
        AutoMigration(from = 1, to = 2),
    ],
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun routineDao(): RoutineDao
    abstract fun sessionDao(): SessionDao
    abstract fun loggedSetDao(): LoggedSetDao

    companion object {
        private const val NAME = "workout.db"

        // No destructive fallback: schema changes must ship a migration so workout history is never wiped.
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, NAME).build()
    }
}
