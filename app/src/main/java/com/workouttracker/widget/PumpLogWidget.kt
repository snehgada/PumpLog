package com.workouttracker.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.workouttracker.MainActivity
import com.workouttracker.R
import com.workouttracker.WorkoutApp
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapLatest
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** What the widget shows; read fresh from the database each time it is drawn. */
private sealed interface WidgetState {
    data class Next(val name: String, val exercises: Int, val sets: Int, val position: Int, val total: Int) : WidgetState
    data class InProgress(val routineName: String, val startedAt: Instant?, val logged: Int, val planned: Int, val restEndsAt: Instant?) : WidgetState
    data object Empty : WidgetState
}

/**
 * Home-screen widget: the next routine with Start between workouts, or the open workout with Resume.
 * Two sizes: compact (2×2) and wide (4×2 and larger). Refreshed by [WidgetSync] whenever the data changes.
 */
class PumpLogWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(setOf(COMPACT, WIDE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Glance keeps this composition alive between updates and only recomposes it, so the data has to be
        // observed in here; loading it once outside would leave the widget showing a stale snapshot.
        val initial = load(context)
        val updates = observe(context)
        provideContent {
            val state by updates.collectAsState(initial)
            Content(context, state)
        }
    }

    /** Re-reads the widget's data whenever anything it depends on changes. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observe(context: Context): Flow<WidgetState> {
        val container = (context.applicationContext as WorkoutApp).container
        return combine(
            container.sessionRepository.observeInProgress(),
            container.workoutRepository.observeLatestRotationEntry(),
            container.workoutRepository.observeRoutineSummaries(),
            container.workoutRepository.observeTotalSets(),
            container.restTimer.state,
        ) { _, _, _, _, _ -> Unit }.mapLatest { load(context) }
    }

    private suspend fun load(context: Context): WidgetState {
        val container = (context.applicationContext as WorkoutApp).container
        container.workoutRepository.ensureSeeded()
        val open = container.sessionRepository.inProgress()
        if (open != null) {
            val planned = container.workoutRepository.observeRoutineSummaries().first()
                .firstOrNull { it.id == open.routineId }?.setCount ?: 0
            return WidgetState.InProgress(
                routineName = open.routineName,
                startedAt = open.startedAt,
                logged = container.workoutRepository.workingSetsLogged(open.id),
                planned = planned,
                restEndsAt = container.restTimer.state.value?.endsAt?.takeIf { it.isAfter(Instant.now()) },
            )
        }
        val next = container.workoutRepository.nextInRotation() ?: return WidgetState.Empty
        return WidgetState.Next(next.routine.name, next.routine.exerciseCount, next.routine.setCount, next.position, next.total)
    }

    @Composable
    private fun Content(context: Context, state: WidgetState) {
        val wide = LocalSize.current.width >= WIDE.width
        when (state) {
            is WidgetState.Next -> NextCard(context, state, wide)
            is WidgetState.InProgress -> InProgressCard(context, state, wide)
            WidgetState.Empty -> Card(background = SURFACE, onClick = MainActivity.openIntent(context)) {
                Text(context.getString(R.string.widget_open), style = text(TEXT, 16, bold = true))
            }
        }
    }

    @Composable
    private fun NextCard(context: Context, s: WidgetState.Next, wide: Boolean) {
        Card(background = SURFACE, onClick = MainActivity.openIntent(context)) {
            Text(context.getString(if (wide) R.string.widget_next_in_rotation else R.string.widget_next), style = text(LIME, 11, bold = true))
            Text(s.name, style = text(TEXT, if (wide) 30 else 26, bold = true), maxLines = 1)
            if (wide) {
                Text(
                    context.getString(
                        R.string.routine_meta,
                        context.resources.getQuantityString(R.plurals.exercise_count, s.exercises, s.exercises),
                        context.resources.getQuantityString(R.plurals.set_count, s.sets, s.sets),
                    ),
                    style = text(MUTED, 13),
                    maxLines = 1,
                )
            }
            Spacer(GlanceModifier.defaultWeight())
            Row(verticalAlignment = Alignment.CenterVertically, modifier = GlanceModifier.fillMaxWidth()) {
                if (wide) {
                    // Rotation progress: routines before the next one in dim lime, the next in bright lime.
                    Row(modifier = GlanceModifier.defaultWeight()) {
                        repeat(s.total) { i ->
                            Box(
                                GlanceModifier
                                    .width(18.dp)
                                    .height(6.dp)
                                    .cornerRadius(3.dp)
                                    .background(
                                        when {
                                            i < s.position -> LIME_DIM
                                            i == s.position -> LIME
                                            else -> LINE
                                        },
                                    ),
                            ) {}
                            if (i < s.total - 1) Spacer(GlanceModifier.width(5.dp))
                        }
                    }
                } else {
                    Spacer(GlanceModifier.defaultWeight())
                }
                Pill(context.getString(R.string.widget_start), background = LIME, color = ON_LIME, onClick = MainActivity.startNextIntent(context))
            }
        }
    }

    @Composable
    private fun InProgressCard(context: Context, s: WidgetState.InProgress, wide: Boolean) {
        val open = MainActivity.openWorkoutIntent(context)
        Card(background = LIME, onClick = open) {
            Text(context.getString(R.string.in_progress), style = text(ON_LIME, 11, bold = true))
            Text(s.routineName, style = text(ON_LIME, if (wide) 28 else 24, bold = true), maxLines = 1)
            Text(
                context.getString(R.string.widget_sets_logged, s.logged, s.planned),
                style = text(OLIVE, 13, bold = true),
                maxLines = 1,
            )
            if (wide) {
                val line = s.restEndsAt?.let { context.getString(R.string.widget_rest_until, time(it)) }
                    ?: s.startedAt?.let { context.getString(R.string.started_at, time(it)) }
                if (line != null) Text(line, style = text(OLIVE, 12), maxLines = 1)
            }
            Spacer(GlanceModifier.defaultWeight())
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                Spacer(GlanceModifier.defaultWeight())
                Pill(context.getString(R.string.widget_resume), background = ON_LIME, color = LIME, onClick = open)
            }
        }
    }

    @Composable
    private fun Card(background: Color, onClick: Intent, content: @Composable ColumnScope.() -> Unit) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .cornerRadius(28.dp)
                .background(background)
                .padding(horizontal = 18.dp, vertical = 16.dp)
                .clickable(actionStartActivity(onClick)),
        ) { content() }
    }

    @Composable
    private fun Pill(label: String, background: Color, color: Color, onClick: Intent) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = GlanceModifier
                .height(44.dp)
                .cornerRadius(22.dp)
                .background(background)
                .padding(horizontal = 18.dp)
                .clickable(actionStartActivity(onClick)),
        ) {
            Text(label, style = text(color, 15, bold = true))
        }
    }

    private fun text(color: Color, size: Int, bold: Boolean = false) =
        TextStyle(color = ColorProvider(color), fontSize = size.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)

    private fun time(instant: Instant): String =
        instant.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))

    private companion object {
        val COMPACT = DpSize(110.dp, 110.dp)
        val WIDE = DpSize(250.dp, 110.dp)

        // Same palette as the app (see ui/theme/Color.kt).
        val SURFACE = Color(0xFF1B1D20)
        val LINE = Color(0xFF2E3135)
        val TEXT = Color(0xFFF2F3F1)
        val MUTED = Color(0xFFA3A8AE)
        val LIME = Color(0xFFCBF35C)
        val LIME_DIM = Color(0xFF3A4320)
        val ON_LIME = Color(0xFF16190D)
        val OLIVE = Color(0xFF3A4320)
    }
}

class PumpLogWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PumpLogWidget()
}
