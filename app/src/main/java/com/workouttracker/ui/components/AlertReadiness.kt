package com.workouttracker.ui.components

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.workouttracker.timer.RestAlarm

/** What decides whether a "Rest's up" alert can reach a locked phone. */
data class AlertReadiness(
    val notificationsAllowed: Boolean,
    /** The user exempted the app from battery optimization ("Allow background activity" on OnePlus). */
    val backgroundAllowed: Boolean,
    val exactAlarms: Boolean,
)

/** Re-checked every time the screen resumes, e.g. after coming back from system settings. */
@Composable
fun rememberAlertReadiness(): AlertReadiness {
    val context = LocalContext.current
    var resumes by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resumes++ }
    return remember(resumes) { alertReadiness(context) }
}

fun alertReadiness(context: Context): AlertReadiness {
    val notifications = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    val power = context.getSystemService(PowerManager::class.java)
    return AlertReadiness(
        notificationsAllowed = notifications,
        backgroundAllowed = power.isIgnoringBatteryOptimizations(context.packageName),
        exactAlarms = RestAlarm.canBeExact(context),
    )
}

/** System settings pages, all for this app only. */
object SystemSettings {
    fun notifications(context: Context) = open(
        context,
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
    )

    /** App info page; on OnePlus the Battery entry there has "Allow background activity". */
    fun appDetails(context: Context) = open(
        context,
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
    )

    fun exactAlarms(context: Context) = open(
        context,
        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.fromParts("package", context.packageName, null)),
    )

    private fun open(context: Context, intent: Intent) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: android.content.ActivityNotFoundException) {
            // Some phones lack a specific page: fall back to the general app info page.
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}
