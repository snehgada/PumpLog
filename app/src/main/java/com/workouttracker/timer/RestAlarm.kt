package com.workouttracker.timer

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.workouttracker.MainActivity
import com.workouttracker.WorkoutApp
import java.time.Instant

/**
 * Ends a rest through Android's alarm system instead of a timer inside the app.
 *
 * Some phones (OnePlus/ColorOS among them) freeze an app seconds after it leaves the screen, which stops any
 * in-app countdown. An alarm-clock alarm is delivered by the system and wakes the app for it, so the
 * "Rest's up" alert still fires.
 */
internal object RestAlarm {
    private const val REQUEST_REST = 10
    private const val REQUEST_SHOW = 11
    private const val ACTION_REST_OVER = "com.workouttracker.rest.OVER"
    private const val EXTRA_ENDS_AT = "endsAt"
    private const val EXTRA_EXERCISE = "exercise"
    private const val MAX_NAME = 60

    /** Schedules the end-of-rest alert, replacing any earlier one. Returns true if it is an exact alarm. */
    fun schedule(context: Context, endsAtMillis: Long, exerciseName: String): Boolean {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val fire = restOverIntent(context, endsAtMillis, exerciseName)
        return if (alarms.canScheduleExactAlarms()) {
            // Shown to the system as an alarm clock: exact, allowed in deep sleep, and not deferred by battery savers.
            alarms.setAlarmClock(AlarmManager.AlarmClockInfo(endsAtMillis, openApp(context)), fire)
            true
        } else {
            // Without the exact-alarm permission: still delivered in deep sleep, possibly a little late.
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endsAtMillis, fire)
            false
        }
    }

    fun cancel(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val existing = PendingIntent.getBroadcast(
            context,
            REQUEST_REST,
            Intent(context, RestAlarmReceiver::class.java).setAction(ACTION_REST_OVER),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE,
        ) ?: return
        alarms.cancel(existing)
        existing.cancel()
    }

    fun canBeExact(context: Context): Boolean = context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    private fun restOverIntent(context: Context, endsAtMillis: Long, exerciseName: String): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST_REST,
            Intent(context, RestAlarmReceiver::class.java)
                .setAction(ACTION_REST_OVER)
                .putExtra(EXTRA_ENDS_AT, endsAtMillis)
                .putExtra(EXTRA_EXERCISE, exerciseName.take(MAX_NAME)),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        REQUEST_SHOW,
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(MainActivity.EXTRA_OPEN_WORKOUT, true),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Not exported: only this app's own alarm (an explicit, immutable PendingIntent) can reach it. */
    class RestAlarmReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != ACTION_REST_OVER) return
            val endsAt = intent.getLongExtra(EXTRA_ENDS_AT, 0L)
            val name = intent.getStringExtra(EXTRA_EXERCISE).orEmpty().take(MAX_NAME)
            RestNotifications.ensureChannels(context)
            RestNotifications.restOver(context, name)
            val app = context.applicationContext as? WorkoutApp ?: return
            app.container.restTimer.onFinished(Instant.ofEpochMilli(endsAt))
            RestTimerService.stop(context)
        }
    }
}
