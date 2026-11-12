package com.workouttracker

import android.app.Application
import com.workouttracker.widget.WidgetSync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class WorkoutApp : Application() {
    lateinit var container: AppContainer
        private set

    /** Lives as long as the process; used for work that isn't tied to a screen. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        WidgetSync.start(this, container, appScope)
    }
}
