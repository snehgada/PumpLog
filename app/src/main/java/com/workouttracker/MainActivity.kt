package com.workouttracker

import android.content.Context
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

    /** Bumped each time we're asked to show the workout in progress (rest notification, widget Resume). */
    private val openWorkoutRequests = mutableIntStateOf(0)

    /** Bumped each time the widget's Start is tapped. */
    private val startNextRequests = mutableIntStateOf(0)

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
                AppNavigation(
                    container,
                    openWorkoutRequests = openWorkoutRequests.intValue,
                    startNextRequests = startNextRequests.intValue,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    // Requests carry no data: the app looks up the workout in progress or the next routine itself, so nothing
    // from the intent is trusted as an id. (This activity is the launcher entry, so other apps can send it these;
    // the worst they can do is open the app or start the next routine, which can be discarded.)
    private fun handle(intent: Intent?) {
        when {
            intent?.action == ACTION_START_NEXT -> startNextRequests.intValue++
            intent?.getBooleanExtra(EXTRA_OPEN_WORKOUT, false) == true -> openWorkoutRequests.intValue++
        }
    }

    companion object {
        const val EXTRA_OPEN_WORKOUT = "open_workout"
        private const val ACTION_START_NEXT = "com.workouttracker.action.START_NEXT"
        private const val ACTION_OPEN = "com.workouttracker.action.OPEN"
        private const val ACTION_OPEN_WORKOUT = "com.workouttracker.action.OPEN_WORKOUT"

        // Each widget action gets its own Intent action so their PendingIntents never overwrite each other.
        fun openIntent(context: Context) = base(context, ACTION_OPEN)

        fun openWorkoutIntent(context: Context) = base(context, ACTION_OPEN_WORKOUT).putExtra(EXTRA_OPEN_WORKOUT, true)

        fun startNextIntent(context: Context) = base(context, ACTION_START_NEXT)

        private fun base(context: Context, action: String) = Intent(context, MainActivity::class.java)
            .setAction(action)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
}
