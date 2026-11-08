package com.workouttracker.timer

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.workouttracker.MainActivity
import com.workouttracker.R

internal object RestNotifications {
    const val ID_COUNTDOWN = 1001
    private const val ID_DONE = 1002
    private const val CHANNEL_COUNTDOWN = "rest_countdown"
    private const val CHANNEL_DONE = "rest_done"
    private val VIBRATION = longArrayOf(0, 400, 200, 400)

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_COUNTDOWN, context.getString(R.string.channel_rest_countdown), NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_DONE, context.getString(R.string.channel_rest_done), NotificationManager.IMPORTANCE_HIGH).apply {
                enableVibration(true)
                vibrationPattern = VIBRATION
                setShowBadge(false)
            },
        )
    }

    fun countdown(context: Context, endsAtMillis: Long, exerciseName: String): Notification =
        NotificationCompat.Builder(context, CHANNEL_COUNTDOWN)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle(context.getString(R.string.rest_notification_title))
            .setContentText(context.getString(R.string.rest_notification_next, exerciseName))
            .setWhen(endsAtMillis)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setContentIntent(openSession(context))
            .addAction(0, context.getString(R.string.skip_rest), skip(context))
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()

    fun restOver(context: Context, exerciseName: String) {
        if (!canNotify(context)) return
        val notification = NotificationCompat.Builder(context, CHANNEL_DONE)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle(context.getString(R.string.rest_over_title))
            .setContentText(context.getString(R.string.rest_notification_next, exerciseName))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVibrate(VIBRATION)
            .setAutoCancel(true)
            .setTimeoutAfter(60_000)
            .setContentIntent(openSession(context))
            .build()
        try {
            NotificationManagerCompat.from(context).notify(ID_DONE, notification)
        } catch (e: SecurityException) {
            // Permission revoked between the check and the call: nothing to show.
        }
    }

    private fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Opens the app on the workout in progress. Carries only a flag; the app looks the workout up itself. */
    private fun openSession(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(MainActivity.EXTRA_OPEN_WORKOUT, true),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun skip(context: Context): PendingIntent = PendingIntent.getService(
        context,
        1,
        Intent(context, RestTimerService::class.java).setAction(RestTimerService.ACTION_SKIP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
