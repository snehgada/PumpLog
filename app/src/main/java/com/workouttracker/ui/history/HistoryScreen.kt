@file:OptIn(ExperimentalMaterial3Api::class)

package com.workouttracker.ui.history

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.workouttracker.AppContainer
import com.workouttracker.R
import com.workouttracker.data.SessionRepository
import com.workouttracker.data.db.SessionStatsRow
import com.workouttracker.domain.DayStatus
import com.workouttracker.domain.ExerciseProgress
import com.workouttracker.domain.SessionStatus
import com.workouttracker.domain.WeekSummary
import com.workouttracker.domain.Weeks
import com.workouttracker.domain.WeightUnit
import java.time.Duration
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun HistoryRoute(container: AppContainer, onOpenWorkout: (Long) -> Unit) {
    val viewModel: HistoryViewModel = viewModel(factory = HistoryViewModel.factory(container))
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.openWorkout) {
        val id = state.openWorkout ?: return@LaunchedEffect
        viewModel.workoutOpened()
        onOpenWorkout(id)
    }
    if (state.loading) return
    HistoryScreen(state, viewModel, onOpenWorkout)
}

@Composable
private fun HistoryScreen(state: HistoryUiState, vm: HistoryViewModel, onOpenWorkout: (Long) -> Unit) {
    val colors = MaterialTheme.colorScheme
    var loggingPast by rememberSaveable { mutableStateOf(false) }
    val locale = Locale.getDefault()
    val range = remember(state.weekStart) {
        val f = DateTimeFormatter.ofPattern("EEE d MMM", locale)
        "${state.weekStart.format(f)} – ${state.weekStart.plusDays(6).format(f)}"
    }

    Scaffold(containerColor = colors.background) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 20.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(start = 4.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(range, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                        Text(
                            stringResource(if (state.isCurrentWeek) R.string.this_week else R.string.week_title),
                            style = MaterialTheme.typography.headlineMedium,
                        )
                    }
                    IconButton(onClick = vm::previousWeek) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.previous_week))
                    }
                    IconButton(onClick = vm::nextWeek, enabled = !state.isCurrentWeek) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.next_week))
                    }
                    FilledIconButton(
                        onClick = { loggingPast = true },
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = colors.primary, contentColor = colors.onPrimary),
                    ) { Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.log_past_workout)) }
                }
            }
            item { DayStrip(state.days, locale) }
            state.week?.let { week -> item { StatTiles(week, state.lastWeek, state.unit) } }
            item { WeeklyChart(state, onMetric = vm::setMetric) }
            item {
                Text(
                    stringResource(R.string.workouts_this_week),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp),
                )
            }
            if (state.sessions.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.no_workouts_this_week),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }
            items(state.sessions, key = { it.sessionId }) { row ->
                SessionRow(row, state.unit, locale, onOpen = { onOpenWorkout(row.sessionId) }, onUndoSkip = { vm.undoSkip(row.sessionId) })
            }
        }
    }

    if (loggingPast) {
        LogPastDialog(
            state = state,
            onDismiss = { loggingPast = false },
            onConfirm = { routineId, date -> loggingPast = false; vm.logPast(routineId, date) },
        )
    }
}

@Composable
private fun DayStrip(days: List<DayCell>, locale: Locale) {
    val colors = MaterialTheme.colorScheme
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
        days.forEach { day ->
            val (bg, fg, border) = when {
                day.status == DayStatus.DONE -> Triple(colors.primary, colors.onPrimary, colors.primary)
                day.status == DayStatus.SKIPPED -> Triple(colors.secondaryContainer, colors.secondary, colors.secondaryContainer)
                day.isToday -> Triple(Color.Transparent, colors.primary, colors.primary)
                else -> Triple(Color.Transparent, colors.outline, colors.outlineVariant)
            }
            val mark = when (day.status) {
                DayStatus.DONE -> day.label
                DayStatus.SKIPPED -> "–"
                DayStatus.REST -> "·"
            }
            val dayName = day.date.dayOfWeek.getDisplayName(TextStyle.NARROW, locale)
            val description = stringResource(
                when (day.status) {
                    DayStatus.DONE -> R.string.day_done
                    DayStatus.SKIPPED -> R.string.day_skipped
                    DayStatus.REST -> R.string.day_rest
                },
                day.date.dayOfWeek.getDisplayName(TextStyle.FULL, locale),
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.weight(1f).semantics(mergeDescendants = true) { contentDescription = description },
            ) {
                Text(dayName, style = MaterialTheme.typography.labelMedium, color = if (day.isToday) colors.primary else colors.onSurfaceVariant)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(40.dp)
                        .background(bg, CircleShape)
                        .border(if (day.isToday && day.status == DayStatus.REST) 2.dp else 1.dp, border, CircleShape),
                ) {
                    Text(mark, style = MaterialTheme.typography.labelLarge, color = if (day.isFuture) colors.outlineVariant else fg)
                }
                Text(day.date.dayOfMonth.toString(), style = MaterialTheme.typography.bodySmall, color = colors.outline)
            }
        }
    }
}

@Composable
private fun StatTiles(week: WeekSummary, last: WeekSummary?, unit: WeightUnit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        StatTile(
            value = week.workouts.toString(),
            label = stringResource(R.string.stat_workouts),
            sub = if (week.skipped > 0) stringResource(R.string.stat_skipped, week.skipped) else last?.let { stringResource(R.string.last_week_value, it.workouts.toString()) },
            modifier = Modifier.weight(1f),
        )
        StatTile(
            value = week.sets.toString(),
            label = stringResource(R.string.stat_sets),
            sub = last?.let { stringResource(R.string.last_week_value, it.sets.toString()) },
            modifier = Modifier.weight(1f),
        )
        StatTile(
            value = ExerciseProgress.formatVolume(week.volumeGramReps, unit),
            label = stringResource(R.string.stat_volume),
            sub = last?.let { stringResource(R.string.last_week_value, ExerciseProgress.formatVolume(it.volumeGramReps, unit)) },
            modifier = Modifier.weight(1.3f),
        )
        StatTile(
            value = Weeks.formatDuration(week.time),
            label = stringResource(R.string.stat_time),
            sub = last?.let { stringResource(R.string.last_week_value, Weeks.formatDuration(it.time)) },
            modifier = Modifier.weight(1.2f),
        )
    }
}

@Composable
private fun StatTile(value: String, label: String, sub: String?, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(14.dp), color = colors.surfaceContainer, modifier = modifier) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(value, style = MaterialTheme.typography.titleMedium, maxLines = 1)
            Text(label, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant, maxLines = 1)
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = colors.outline, maxLines = 1)
        }
    }
}

@Composable
private fun WeeklyChart(state: HistoryUiState, onMetric: (ChartMetric) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val values = state.bars.map { week ->
        when (state.metric) {
            ChartMetric.SETS -> week.sets.toLong()
            ChartMetric.VOLUME -> week.volumeGramReps
            ChartMetric.DAYS -> week.days.count { it == DayStatus.DONE }.toLong()
        }
    }
    val max = (values.maxOrNull() ?: 0L).coerceAtLeast(1L)
    val labelFormat = remember { DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()) }
    Surface(shape = RoundedCornerShape(18.dp), color = colors.surfaceContainer) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                ChartMetric.entries.forEachIndexed { index, metric ->
                    SegmentedButton(
                        selected = state.metric == metric,
                        onClick = { onMetric(metric) },
                        shape = SegmentedButtonDefaults.itemShape(index, ChartMetric.entries.size),
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = colors.primary,
                            activeContentColor = colors.onPrimary,
                            activeBorderColor = colors.primary,
                        ),
                        icon = {},
                    ) {
                        Text(
                            stringResource(
                                when (metric) {
                                    ChartMetric.SETS -> R.string.metric_sets
                                    ChartMetric.VOLUME -> R.string.metric_volume
                                    ChartMetric.DAYS -> R.string.metric_days
                                },
                            ),
                        )
                    }
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.fillMaxWidth().height(140.dp),
            ) {
                state.bars.forEachIndexed { i, week ->
                    val value = values[i]
                    val isShown = i == state.bars.lastIndex
                    val text = when (state.metric) {
                        ChartMetric.VOLUME -> ExerciseProgress.formatVolume(value, state.unit).substringBefore(' ')
                        else -> value.toString()
                    }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                        modifier = Modifier.weight(1f).semantics(mergeDescendants = true) {
                            contentDescription = "${week.weekStart.format(labelFormat)}: $text"
                        },
                    ) {
                        Text(text, style = MaterialTheme.typography.labelSmall, color = if (isShown) colors.primary else colors.outline, maxLines = 1)
                        Spacer(Modifier.height(4.dp))
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height((110f * value / max).coerceAtLeast(2f).dp)
                                .background(if (isShown) colors.primary else colors.outlineVariant, RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)),
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                state.bars.forEachIndexed { i, week ->
                    Text(
                        if (i == 0 || week.weekStart.dayOfMonth <= 7) week.weekStart.format(labelFormat) else week.weekStart.dayOfMonth.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.outline,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionRow(row: SessionStatsRow, unit: WeightUnit, locale: Locale, onOpen: () -> Unit, onUndoSkip: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val skipped = row.status == SessionStatus.SKIPPED
    Surface(
        onClick = onOpen,
        enabled = !skipped,
        shape = RoundedCornerShape(16.dp),
        color = colors.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp).heightIn(min = 44.dp),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(40.dp)) {
                Text(
                    row.date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale).uppercase(locale),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                )
                Text(row.date.dayOfMonth.toString(), style = MaterialTheme.typography.titleLarge)
            }
            Column(Modifier.weight(1f)) {
                Text(row.routineName, style = MaterialTheme.typography.titleMedium, color = if (skipped) colors.onSurfaceVariant else colors.onSurface)
                Text(
                    when (row.status) {
                        SessionStatus.SKIPPED -> stringResource(R.string.skipped_row)
                        SessionStatus.IN_PROGRESS -> stringResource(R.string.in_progress_row)
                        SessionStatus.DONE -> sessionMeta(row, unit)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (skipped) colors.secondary else colors.onSurfaceVariant,
                )
            }
            if (skipped) {
                OutlinedButton(onClick = onUndoSkip, border = BorderStroke(1.dp, colors.outlineVariant)) {
                    Text(stringResource(R.string.undo), color = colors.onSurface)
                }
            }
        }
    }
}

@Composable
private fun sessionMeta(row: SessionStatsRow, unit: WeightUnit): String {
    val parts = mutableListOf<String>()
    val start = row.startedAt
    val end = row.finishedAt
    if (start != null && end != null && end.isAfter(start)) parts += Weeks.formatDuration(Duration.between(start, end))
    parts += androidx.compose.ui.platform.LocalContext.current.resources.getQuantityString(R.plurals.set_count, row.workingSets, row.workingSets)
    if (row.volumeGramReps > 0) parts += ExerciseProgress.formatVolume(row.volumeGramReps, unit)
    return parts.joinToString(" · ")
}

/** Two steps: pick the day (past year only), then the routine. */
@Composable
private fun LogPastDialog(state: HistoryUiState, onDismiss: () -> Unit, onConfirm: (Long, LocalDate) -> Unit) {
    var date by rememberSaveable { mutableStateOf<LocalDate?>(null) }
    val today = state.today
    val earliest = today.minusDays(SessionRepository.MAX_PAST_DAYS)
    val picked = date
    if (picked == null) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = today.toEpochDay() * DAY_MS,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    val d = LocalDate.ofEpochDay(Math.floorDiv(utcTimeMillis, DAY_MS))
                    return !d.isAfter(today) && !d.isBefore(earliest)
                }

                override fun isSelectableYear(year: Int): Boolean = year in earliest.year..today.year
            },
        )
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(
                    onClick = { pickerState.selectedDateMillis?.let { date = LocalDate.ofEpochDay(Math.floorDiv(it, DAY_MS)) } },
                    enabled = pickerState.selectedDateMillis != null,
                ) { Text(stringResource(R.string.next)) }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        ) {
            DatePicker(state = pickerState, title = { Text(stringResource(R.string.log_past_when), modifier = Modifier.padding(start = 24.dp, top = 16.dp)) })
        }
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.log_past_which)) },
            text = {
                Column {
                    Text(
                        picked.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault())),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    state.routines.forEach { routine ->
                        Text(
                            routine.name,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onConfirm(routine.id, picked) }
                                .padding(vertical = 12.dp, horizontal = 4.dp),
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { date = null }) { Text(stringResource(R.string.back)) } },
        )
    }
}

private const val DAY_MS = 86_400_000L
