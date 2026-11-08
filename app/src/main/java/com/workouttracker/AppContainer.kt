package com.workouttracker

import android.content.Context
import com.workouttracker.data.RoutineRepository
import com.workouttracker.data.SessionRepository
import com.workouttracker.timer.RestTimer
import com.workouttracker.data.WorkoutRepository
import com.workouttracker.data.db.AppDatabase
import com.workouttracker.data.settings.SettingsRepository
import com.workouttracker.data.settings.settingsDataStore
import java.time.Clock

/** Hand-wired dependencies; one instance per process, owned by [WorkoutApp]. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val clock: Clock = Clock.systemDefaultZone()
    val database: AppDatabase by lazy { AppDatabase.build(appContext) }
    val settings: SettingsRepository by lazy { SettingsRepository(appContext.settingsDataStore) }
    val workoutRepository: WorkoutRepository by lazy { WorkoutRepository(database, settings) }
    val routineRepository: RoutineRepository by lazy { RoutineRepository(database) }
    val sessionRepository: SessionRepository by lazy { SessionRepository(database) }
    val restTimer: RestTimer by lazy { RestTimer(appContext, clock) }
}
