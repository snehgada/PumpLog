package com.workouttracker.ui.today

import androidx.annotation.PluralsRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.workouttracker.AppContainer
import com.workouttracker.R
import com.workouttracker.data.db.RoutineSummary
import com.workouttracker.domain.RotationStatus
import com.workouttracker.ui.components.RoutineBadge
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Composable
fun TodayRoute(
    container: AppContainer,
    onEditRoutines: () -> Unit,
    onOpenRoutine: (routineId: Long) -> Unit,
    onOpenWorkout: (sessionId: Long) -> Unit,
) {
    val viewModel: TodayViewModel = viewModel(factory = TodayViewModel.factory(container))
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.openWorkout) {
        val id = state.openWorkout ?: return@LaunchedEffect
        viewModel.workoutOpened()
        onOpenWorkout(id)
    }
    TodayScreen(
        state = state,
        onStart = viewModel::start,
        onResume = viewModel::resume,
        onReopen = viewModel::reopen,
        onSkip = viewModel::skip,
        onUndoSkip = viewModel::undoSkip,
        onSkipNoticeShown = viewModel::skipNoticeShown,
        onEditRoutines = onEditRoutines,
        onOpenRoutine = onOpenRoutine,
    )
}

@Composable
fun TodayScreen(
    state: TodayUiState,
    onStart: (routineId: Long) -> Unit,
    onResume: () -> Unit,
    onReopen: (sessionId: Long) -> Unit,
    onSkip: (routineId: Long) -> Unit,
    onUndoSkip: (sessionId: Long) -> Unit,
    onSkipNoticeShown: () -> Unit,
    onEditRoutines: () -> Unit,
    onOpenRoutine: (routineId: Long) -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalContext.current.resources
    var pickingAnother by rememberSaveable { mutableStateOf(false) }

    // Clear the notice only after the snackbar is done: clearing it first would change the effect key
    // and cancel the snackbar immediately.
    LaunchedEffect(state.skipNotice) {
        val notice = state.skipNotice ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = resources.getString(R.string.routine_skipped, notice.routineName),
            actionLabel = resources.getString(R.string.undo),
            duration = SnackbarDuration.Long,
        )
        if (result == SnackbarResult.ActionPerformed) onUndoSkip(notice.sessionId)
        onSkipNoticeShown()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 20.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { Header(state) }
            if (state.rotation.isNotEmpty()) {
                item { RotationStrip(state.rotation) }
            }
            val inProgress = state.inProgress
            if (inProgress != null) {
                item { InProgressCard(inProgress.routineName, inProgress.startedAt, onResume) }
            } else {
                state.next?.let { next ->
                    item {
                        NextUpCard(
                            next = next,
                            onStart = { onStart(next.id) },
                            onSkip = { onSkip(next.id) },
                            onPickAnother = { pickingAnother = true },
                        )
                    }
                }
                state.finishedToday?.let { done ->
                    item { FinishedTodayRow(done.routineName, done.finishedAt, onReopen = { onReopen(done.id) }) }
                }
            }
            if (state.routines.isNotEmpty()) {
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(start = 4.dp, top = 12.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.your_routines),
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = onEditRoutines) { Text(stringResource(R.string.edit)) }
                    }
                }
                items(state.routines, key = { it.id }) {
                    RoutineRow(it, badge = state.badges[it.id].orEmpty(), onClick = { onOpenRoutine(it.id) })
                }
            }
        }
    }

    if (pickingAnother) {
        PickAnotherDialog(
            routines = state.routines,
            nextId = state.next?.id,
            onPick = { id -> pickingAnother = false; onStart(id) },
            onDismiss = { pickingAnother = false },
        )
    }
}

@Composable
private fun InProgressCard(routineName: String, startedAt: Instant?, onResume: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val started = remember(startedAt) {
        startedAt?.atZone(ZoneId.systemDefault())?.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)).orEmpty()
    }
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = colors.surfaceContainer,
        border = BorderStroke(1.dp, colors.primaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.in_progress), style = MaterialTheme.typography.labelSmall, color = colors.primary)
            Text(routineName, style = MaterialTheme.typography.displaySmall)
            Text(stringResource(R.string.started_at, started), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = onResume,
                colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = colors.onPrimary),
                modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
            ) { Text(stringResource(R.string.resume_workout), style = MaterialTheme.typography.labelLarge) }
        }
    }
}

@Composable
private fun FinishedTodayRow(routineName: String, finishedAt: Instant?, onReopen: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val time = remember(finishedAt) {
        finishedAt?.atZone(ZoneId.systemDefault())?.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)).orEmpty()
    }
    Surface(shape = RoundedCornerShape(18.dp), color = colors.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        ) {
            Text(
                stringResource(R.string.finished_at, routineName, time),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onReopen) { Text(stringResource(R.string.reopen)) }
        }
    }
}

@Composable
private fun PickAnotherDialog(routines: List<RoutineSummary>, nextId: Long?, onPick: (Long) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pick_another_title)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.pick_another_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                routines.filter { it.id != nextId }.forEach { routine ->
                    Text(
                        routine.name,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(routine.id) }
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun Header(state: TodayUiState) {
    val dateText = remember(state.date) {
        state.date?.format(DateTimeFormatter.ofPattern("EEEE, d MMM", Locale.getDefault())).orEmpty()
    }
    Column(modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)) {
        Text(dateText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.today_title), style = MaterialTheme.typography.headlineMedium)
    }
}

@Composable
private fun RotationStrip(items: List<RotationItem>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 8.dp)) {
        Text(
            stringResource(R.string.rotation_label),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items.forEachIndexed { index, item ->
                RotationChip(item, position = index + 1, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun RotationChip(item: RotationItem, position: Int, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val (container, border, label, mark) = when (item.status) {
        RotationStatus.NEXT -> ChipColors(colors.primary, colors.primary, colors.onPrimary, colors.onPrimary)
        RotationStatus.DONE -> ChipColors(colors.primaryContainer.copy(alpha = 0.45f), colors.primaryContainer, colors.onSurfaceVariant, colors.primary)
        RotationStatus.SKIPPED -> ChipColors(colors.surfaceContainer, colors.outlineVariant, colors.onSurfaceVariant, colors.secondary)
        RotationStatus.LATER -> ChipColors(Color.Transparent, colors.outlineVariant, colors.onSurface, colors.outline)
    }
    val markText = when (item.status) {
        RotationStatus.NEXT -> stringResource(R.string.rotation_next)
        RotationStatus.DONE -> stringResource(R.string.rotation_done)
        RotationStatus.SKIPPED -> stringResource(R.string.rotation_skipped)
        RotationStatus.LATER -> position.toString()
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = container,
        border = BorderStroke(1.dp, border),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 2.dp),
        ) {
            Text(markText, style = MaterialTheme.typography.labelSmall, color = mark, maxLines = 1)
            Text(
                item.routine.name,
                style = MaterialTheme.typography.labelMedium,
                color = label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private data class ChipColors(val container: Color, val border: Color, val label: Color, val mark: Color)

@Composable
private fun NextUpCard(next: RoutineSummary, onStart: () -> Unit, onSkip: () -> Unit, onPickAnother: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stringResource(R.string.next_in_rotation),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(next.name, style = MaterialTheme.typography.displaySmall)
            Text(
                routineMeta(next),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = onStart,
                colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = colors.onPrimary),
                modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
            ) { Text(stringResource(R.string.start_routine, next.name), style = MaterialTheme.typography.labelLarge) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onSkip,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    border = BorderStroke(1.dp, colors.outlineVariant),
                ) { Text(stringResource(R.string.skip_routine, next.name), color = colors.onSurface) }
                OutlinedButton(
                    onClick = onPickAnother,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    border = BorderStroke(1.dp, colors.outlineVariant),
                ) { Text(stringResource(R.string.pick_another), color = colors.onSurface) }
            }
        }
    }
}

@Composable
private fun RoutineRow(routine: RoutineSummary, badge: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            RoutineBadge(badge)
            Column {
                Text(routine.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    routineMeta(routine),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun routineMeta(routine: RoutineSummary): String = stringResource(
    R.string.routine_meta,
    quantity(R.plurals.exercise_count, routine.exerciseCount),
    quantity(R.plurals.set_count, routine.setCount),
)

@Composable
private fun quantity(@PluralsRes id: Int, count: Int): String =
    LocalContext.current.resources.getQuantityString(id, count, count)
