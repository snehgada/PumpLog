package com.workouttracker.timer

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.workouttracker.WorkoutApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * Keeps the rest countdown running with the screen locked: an ongoing notification shows the time left
 * (rendered by the system), and a "Rest's up" notification fires at the end. Not exported; only this app starts it.
 */
class RestTimerService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var countdown: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> begin(intent)
            ACTION_SKIP -> {
                (application as WorkoutApp).container.restTimer.skip()
                end() // even if the in-app timer was already gone, don't leave the notification behind
            }
            else -> end()
        }
        return START_NOT_STICKY
    }

    private fun begin(intent: Intent) {
        val endsAt = intent.getLongExtra(EXTRA_ENDS_AT, 0L)
        val name = intent.getStringExtra(EXTRA_EXERCISE).orEmpty().take(MAX_NAME)
        val remaining = endsAt - System.currentTimeMillis()
        RestNotifications.ensureChannels(this)
        // startForeground must come first: a service started with startForegroundService() that stops
        // without calling it crashes the app.
        try {
            ServiceCompat.startForeground(
                this,
                RestNotifications.ID_COUNTDOWN,
                RestNotifications.countdown(this, maxOf(endsAt, System.currentTimeMillis()), name),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } catch (e: IllegalStateException) {
            // Not allowed to start in the background (e.g. a stale intent); the in-app timer still works.
            end()
            return
        }
        // Ignore anything malformed: past end times or longer than the timer allows.
        if (remaining <= 0 || remaining > RestTimer.MAX_REST_SEC * 1000L + SLACK_MS) {
            end()
            return
        }
        holdWakeLock(remaining + SLACK_MS)
        countdown?.cancel()
        countdown = scope.launch {
            delay(remaining)
            RestNotifications.restOver(this@RestTimerService, name)
            (application as WorkoutApp).container.restTimer.onFinished(Instant.ofEpochMilli(endsAt))
            end()
        }
    }

    private fun end() {
        countdown?.cancel()
        countdown = null
        releaseWakeLock()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun holdWakeLock(timeoutMs: Long) {
        releaseWakeLock()
        val pm = getSystemService(PowerManager::class.java)
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WorkoutTracker:rest").apply {
            setReferenceCounted(false)
            acquire(timeoutMs) // always bounded, so a bug can never keep the phone awake
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    override fun onDestroy() {
        scope.cancel()
        releaseWakeLock()
        super.onDestroy()
    }

    companion object {
        private const val ACTION_START = "com.workouttracker.rest.START"
        private const val ACTION_STOP = "com.workouttracker.rest.STOP"
        internal const val ACTION_SKIP = "com.workouttracker.rest.SKIP"
        private const val EXTRA_ENDS_AT = "endsAt"
        private const val EXTRA_EXERCISE = "exercise"
        private const val MAX_NAME = 60
        private const val SLACK_MS = 5_000L

        fun start(context: Context, state: RestState) {
            val intent = Intent(context, RestTimerService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_ENDS_AT, state.endsAt.toEpochMilli())
                .putExtra(EXTRA_EXERCISE, state.exerciseName)
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: IllegalStateException) {
                // App not in the foreground: skip the notification, the in-app countdown still runs.
            }
        }

        fun stop(context: Context) {
            try {
                context.startService(Intent(context, RestTimerService::class.java).setAction(ACTION_STOP))
            } catch (e: IllegalStateException) {
                // Service not running and app in background: nothing to stop.
            }
        }
    }
}
