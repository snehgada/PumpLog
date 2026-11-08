package com.workouttracker

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableIntStateOf
import com.workouttracker.ui.AppNavigation
import com.workouttracker.ui.theme.WorkoutTheme

class MainActivity : ComponentActivity() {

    /** Bumped each time we're asked to show the workout in progress (e.g. from the rest notification). */
    private val openWorkoutRequests = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Dark-only app: always light status/navigation bar icons.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        if (savedInstanceState == null) handle(intent)
        val container = (application as WorkoutApp).container
        setContent {
            WorkoutTheme {
                AppNavigation(container, openWorkoutRequests = openWorkoutRequests.intValue)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    // The extra is only a flag; the app finds the in-progress workout itself, so nothing from the
    // intent is trusted as an id.
    private fun handle(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_WORKOUT, false) == true) openWorkoutRequests.intValue++
    }

    companion object {
        const val EXTRA_OPEN_WORKOUT = "open_workout"
    }
}
