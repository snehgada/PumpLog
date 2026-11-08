@file:OptIn(ExperimentalMaterial3Api::class)

package com.workouttracker.ui.workout

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.workouttracker.AppContainer
import com.workouttracker.R
import com.workouttracker.data.ExerciseRef
import com.workouttracker.domain.LiveSessionRules
import com.workouttracker.domain.SetType
import com.workouttracker.domain.WeightUnit
import com.workouttracker.domain.Weights
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant

@Composable
fun WorkoutRoute(container: AppContainer, sessionId: Long, onClose: () -> Unit) {
    val viewModel: WorkoutViewModel = viewModel(
        key = "workout-$sessionId",
        factory = WorkoutViewModel.factory(container, sessionId),
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val options by viewModel.exerciseOptions.collectAsStateWithLifecycle()

    LaunchedEffect(state.closed) { if (state.closed) onClose() }
    if (!state.loading && !state.editing) AskForNotifications()

    if (state.loading || state.closed) {
        Box(Modifier.background(MaterialTheme.colorScheme.background))
        return
    }
    WorkoutScreen(state, options, viewModel, onBack = onClose)
}

/** The rest timer alerts through notifications; ask once when a workout opens (Android 13+). */
@Composable
private fun AskForNotifications() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    var asked by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!granted && !asked) {
            asked = true
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

@Composable
private fun WorkoutScreen(
    state: WorkoutUiState,
    options: List<ExerciseRef>,
    vm: WorkoutViewModel,
    onBack: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    var confirmFinish by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val dateText = remember(state.date) {
        state.date?.format(java.time.format.DateTimeFormatter.ofPattern("EEE d MMM yyyy", java.util.Locale.getDefault())).orEmpty()
    }
    val resources = LocalContext.current.resources
    val now by rememberTicker()

    LaunchedEffect(state.error) {
        if (state.error == null) return@LaunchedEffect
        snackbar.showSnackbar(resources.getString(R.string.workout_save_failed))
        vm.clearError()
    }

    Scaffold(
        containerColor = colors.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                title = {
                    Column {
                        Text(state.routineName, style = MaterialTheme.typography.titleLarge)
                        Text(
                            if (state.editing) stringResource(R.string.editing_workout_on, dateText) else elapsed(state.startedAt, now),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    OutlinedButton(
                        onClick = {
                            if (state.editing) {
                                onBack()
                            } else {
                                confirmFinish = true
                            }
                        },
                        border = BorderStroke(1.dp, colors.primary),
                        modifier = Modifier.padding(end = 8.dp),
                    ) { Text(stringResource(if (state.editing) R.string.done else R.string.finish), color = colors.primary) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background),
            )
        },
        bottomBar = {
            state.rest?.let { rest -> RestBar(rest.endsAt, now, rest.exerciseName, onAdd = { vm.addRest(15) }, onSkip = vm::skipRest) }
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 12.dp,
                end = 12.dp,
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { ProgressSegments(state) }
            items(state.exercises, key = { it.plannedId }) { exercise ->
                if (state.expandedId == exercise.plannedId) {
                    ExpandedExercise(exercise, state.unit, vm)
                } else {
                    CollapsedExercise(exercise, onClick = { vm.toggleExpanded(exercise.plannedId) })
                }
            }
            item {
                if (state.editing) {
                    TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        Text(stringResource(R.string.delete_workout), color = colors.error)
                    }
                } else {
                    TextButton(onClick = { confirmFinish = true }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        Text(stringResource(R.string.finish_workout), color = colors.onSurface)
                    }
                }
            }
        }
    }

    state.sheet?.let { sheet -> SetSheet(sheet, state.unit, vm) }
    state.swapFor?.let { plannedId ->
        state.exercises.firstOrNull { it.plannedId == plannedId }?.let { exercise ->
            SwapDialog(exercise, options, onPick = { vm.swapTo(plannedId, it) }, onNew = { name -> vm.swapToNew(plannedId, name, exercise.performed.kind) }, onDismiss = vm::closeSwap)
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_workout_title)) },
            text = { Text(stringResource(R.string.delete_workout_body, state.routineName, dateText)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; vm.deleteWorkout() }) { Text(stringResource(R.string.delete), color = colors.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (confirmFinish) {
        FinishDialog(
            logged = state.loggedCount,
            planned = state.plannedCount,
            onFinish = { confirmFinish = false; vm.finish() },
            onDiscard = { confirmFinish = false; vm.discard() },
            onDismiss = { confirmFinish = false },
        )
    }
}

@Composable
private fun ProgressSegments(state: WorkoutUiState) {
    val colors = MaterialTheme.colorScheme
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
        state.exercises.forEach { ex ->
            val color = when {
                ex.complete -> colors.primary
                ex.loggedCount > 0 -> colors.primaryContainer
                else -> colors.outlineVariant
            }
            Box(Modifier.weight(1f).height(4.dp).background(color, RoundedCornerShape(2.dp)))
        }
    }
}

@Composable
private fun CollapsedExercise(exercise: ExerciseUi, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = colors.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(28.dp)
                    .background(if (exercise.complete) colors.primary else Color.Transparent, CircleShape),
            ) {
                if (exercise.complete) Icon(Icons.Filled.Check, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(18.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(exercise.performed.name, style = MaterialTheme.typography.titleMedium)
                if (exercise.swapped) {
                    Text(stringResource(R.string.instead_of, exercise.plannedName), style = MaterialTheme.typography.bodySmall, color = colors.secondary)
                }
            }
            Text(
                stringResource(R.string.sets_progress, exercise.loggedCount, exercise.plannedCount),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ExpandedExercise(exercise: ExerciseUi, unit: WeightUnit, vm: WorkoutViewModel) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = colors.surfaceContainer,
        border = BorderStroke(1.dp, colors.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClickLabel = stringResource(R.string.collapse)) { vm.toggleExpanded(exercise.plannedId) },
                ) {
                    Text(exercise.performed.name, style = MaterialTheme.typography.titleLarge)
                    if (exercise.swapped) {
                        Text(stringResource(R.string.instead_of, exercise.plannedName), style = MaterialTheme.typography.bodySmall, color = colors.secondary)
                    }
                    Text(
                        buildString {
                            append(exercise.scheme)
                            if (exercise.isRpt) append(" · RPT")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { vm.openSwap(exercise.plannedId) }) {
                    Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.swap_exercise))
                }
            }

            exercise.suggestedIncrease?.let { increase ->
                Text(
                    stringResource(R.string.progression_hint, increase),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onPrimaryContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.primaryContainer.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                HeaderCell(stringResource(R.string.col_set), Modifier.width(36.dp))
                HeaderCell(stringResource(R.string.col_last), Modifier.weight(1f))
                HeaderCell(if (unit == WeightUnit.KG) stringResource(R.string.unit_kg) else stringResource(R.string.unit_lb), Modifier.width(76.dp), TextAlign.Center)
                HeaderCell(stringResource(R.string.col_reps), Modifier.width(60.dp), TextAlign.Center)
                Spacer(Modifier.width(44.dp))
            }
            exercise.rows.forEach { row -> SetRow(exercise, row, unit, vm) }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { vm.addSet(exercise.plannedId) },
                    border = BorderStroke(1.dp, colors.outlineVariant),
                    modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                ) { Text(stringResource(R.string.add_set), color = colors.onSurface) }
                OutlinedButton(
                    onClick = { vm.openSheet(exercise.plannedId, null) },
                    border = BorderStroke(1.dp, colors.outlineVariant),
                    modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                ) { Text(stringResource(R.string.drop_set_or_note), color = colors.onSurface) }
            }
        }
    }
}

@Composable
private fun HeaderCell(text: String, modifier: Modifier, align: TextAlign = TextAlign.Start) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.outline,
        textAlign = align,
        modifier = modifier,
    )
}

@Composable
private fun SetRow(exercise: ExerciseUi, row: SetRowUi, unit: WeightUnit, vm: WorkoutViewModel) {
    val colors = MaterialTheme.colorScheme
    val logged = row.logged
    val isDone = logged != null
    Column {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .background(if (isDone) colors.primaryContainer.copy(alpha = 0.35f) else Color.Transparent, RoundedCornerShape(12.dp))
                .padding(vertical = 2.dp),
        ) {
            Text(
                text = when {
                    logged?.type == SetType.WARMUP -> "W"
                    logged?.type == SetType.DROP -> "${row.index + 1}D"
                    else -> "${row.index + 1}"
                },
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = if (isDone) colors.primary else colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .width(36.dp)
                    .clickable(onClickLabel = stringResource(R.string.set_details)) { vm.openSheet(exercise.plannedId, row.index) }
                    .padding(vertical = 10.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(row.lastTime, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1)
                row.target?.let { Text(LiveSessionRules.targetText(it), style = MaterialTheme.typography.labelMedium, color = colors.outline) }
                    ?: Text(stringResource(R.string.extra_set), style = MaterialTheme.typography.labelMedium, color = colors.outline)
            }
            NumberField(
                value = row.weightText,
                onChange = { vm.onWeight(exercise.plannedId, row.index, it) },
                label = stringResource(R.string.weight_for_set, row.index + 1),
                decimal = true,
                isError = row.invalid,
                modifier = Modifier.width(76.dp),
            )
            NumberField(
                value = row.repsText,
                onChange = { vm.onReps(exercise.plannedId, row.index, it) },
                label = stringResource(R.string.reps_for_set, row.index + 1),
                placeholder = row.target?.let(LiveSessionRules::targetText),
                isError = row.invalid,
                modifier = Modifier.width(60.dp),
            )
            IconButton(
                onClick = { vm.toggleSet(exercise.plannedId, row.index) },
                modifier = Modifier
                    .size(44.dp)
                    .background(if (isDone) colors.primary else colors.surfaceContainerHigh, RoundedCornerShape(10.dp)),
            ) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = stringResource(if (isDone) R.string.unlog_set else R.string.log_set, row.index + 1),
                    tint = if (isDone) colors.onPrimary else colors.outline,
                )
            }
        }
        if (row.invalid) {
            Text(stringResource(R.string.invalid_set), style = MaterialTheme.typography.bodySmall, color = colors.error, modifier = Modifier.padding(start = 44.dp))
        }
        logged?.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, modifier = Modifier.padding(start = 44.dp)) }
        if (logged != null && logged.segments.size > 1) {
            Text(
                logged.segments.drop(1).joinToString("  ") { "→ ${it.reps} × ${Weights.format(it.weightGrams, unit)}" },
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(start = 44.dp),
            )
        }
    }
}

@Composable
private fun NumberField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier,
    decimal: Boolean = false,
    isError: Boolean = false,
    placeholder: String? = null,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(10.dp)
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.titleMedium.copy(color = colors.onSurface, textAlign = TextAlign.Center),
        cursorBrush = SolidColor(colors.primary),
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number, imeAction = ImeAction.Done),
        modifier = modifier
            .heightIn(min = 44.dp)
            .background(colors.surfaceContainerHigh, shape)
            .then(if (isError) Modifier.border(1.dp, colors.error, shape) else Modifier)
            .semantics { contentDescription = label },
        decorationBox = { inner ->
            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp)) {
                if (value.isEmpty() && placeholder != null) {
                    Text(placeholder, style = MaterialTheme.typography.titleMedium, color = colors.outline, textAlign = TextAlign.Center)
                }
                inner()
            }
        },
    )
}

@Composable
private fun RestBar(endsAt: Instant, now: Instant, nextName: String, onAdd: () -> Unit, onSkip: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val left = Duration.between(now, endsAt).let { if (it.isNegative) Duration.ZERO else it }
    Surface(color = colors.primary, shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 18.dp, vertical = 12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.rest_label), style = MaterialTheme.typography.labelSmall, color = colors.onPrimary)
                Text(
                    "%d:%02d".format(left.toMinutes(), left.seconds % 60),
                    style = MaterialTheme.typography.headlineMedium,
                    color = colors.onPrimary,
                )
                Text(stringResource(R.string.rest_notification_next, nextName), style = MaterialTheme.typography.bodySmall, color = colors.onPrimary, maxLines = 1)
            }
            TextButton(onClick = onAdd) { Text(stringResource(R.string.rest_plus), color = colors.onPrimary) }
            Button(
                onClick = onSkip,
                colors = ButtonDefaults.buttonColors(containerColor = colors.onPrimary, contentColor = colors.primary),
            ) { Text(stringResource(R.string.skip_rest)) }
        }
    }
}

@Composable
private fun SetSheet(sheet: SetSheetState, unit: WeightUnit, vm: WorkoutViewModel) {
    val colors = MaterialTheme.colorScheme
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = vm::closeSheet, sheetState = sheetState, containerColor = colors.surfaceContainer) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(sheet.exerciseName.uppercase(), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            Text(stringResource(R.string.set_number, sheet.index + 1), style = MaterialTheme.typography.headlineMedium)

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                SetType.entries.forEach { type ->
                    FilterChip(
                        selected = sheet.type == type,
                        onClick = { vm.sheetType(type) },
                        label = { Text(stringResource(typeLabel(type))) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = colors.primary, selectedLabelColor = colors.onPrimary),
                    )
                }
            }

            sheet.blocks.forEachIndexed { i, (weight, reps) ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (i == 0) stringResource(R.string.top_set) else stringResource(R.string.drop_number, i),
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.width(64.dp),
                    )
                    OutlinedTextField(
                        value = weight,
                        onValueChange = { vm.sheetBlock(i, weight = it) },
                        label = { Text(if (unit == WeightUnit.KG) stringResource(R.string.unit_kg) else stringResource(R.string.unit_lb)) },
                        singleLine = true,
                        isError = sheet.invalid,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                        modifier = Modifier.weight(1f),
                    )
                    Text("×", color = colors.onSurfaceVariant)
                    OutlinedTextField(
                        value = reps,
                        onValueChange = { vm.sheetBlock(i, reps = it) },
                        label = { Text(stringResource(R.string.col_reps)) },
                        singleLine = true,
                        isError = sheet.invalid,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { vm.sheetRemoveBlock(i) }, enabled = sheet.blocks.size > 1) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.remove_block, i + 1))
                    }
                }
            }
            if (sheet.invalid) Text(stringResource(R.string.invalid_set), color = colors.error, style = MaterialTheme.typography.bodySmall)
            if (sheet.blocks.size < LiveSessionRules.MAX_SEGMENTS) {
                TextButton(onClick = vm::sheetAddDrop) { Text(stringResource(R.string.add_drop)) }
            }
            OutlinedTextField(
                value = sheet.note,
                onValueChange = vm::sheetNote,
                label = { Text(stringResource(R.string.set_note)) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )
            OutlinedButton(
                onClick = { vm.openSwap(sheet.plannedId) },
                border = BorderStroke(1.dp, colors.outlineVariant),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.swap_for_today), color = colors.onSurface) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (sheet.isLogged) {
                    OutlinedButton(
                        onClick = vm::removeFromSheet,
                        border = BorderStroke(1.dp, colors.outlineVariant),
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                    ) { Text(stringResource(R.string.remove_logged_set), color = colors.error) }
                } else {
                    OutlinedButton(
                        onClick = vm::closeSheet,
                        border = BorderStroke(1.dp, colors.outlineVariant),
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                    ) { Text(stringResource(R.string.cancel), color = colors.onSurface) }
                }
                Button(
                    onClick = vm::saveSheet,
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = colors.onPrimary),
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                ) { Text(stringResource(R.string.save_set)) }
            }
        }
    }
}

@Composable
private fun SwapDialog(
    exercise: ExerciseUi,
    options: List<ExerciseRef>,
    onPick: (ExerciseRef) -> Unit,
    onNew: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val trimmed = query.trim()
    val planned = ExerciseRef(exercise.plannedId, exercise.plannedName, exercise.performed.kind)
    val matches = if (trimmed.length < 2) emptyList() else {
        options.filter { it.name.contains(trimmed, ignoreCase = true) && it.id != exercise.performed.id }.take(5)
    }
    val exact = options.any { it.name.equals(trimmed, ignoreCase = true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.swap_title, exercise.plannedName)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.swap_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (exercise.swapped) SwapOption(stringResource(R.string.swap_back, exercise.plannedName)) { onPick(planned) }
                exercise.alternative?.takeIf { it.id != exercise.performed.id }?.let { alt ->
                    SwapOption(stringResource(R.string.swap_alternative, alt.name)) { onPick(alt) }
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it.take(com.workouttracker.domain.RoutineEditing.MAX_EXERCISE_NAME) },
                    label = { Text(stringResource(R.string.swap_search)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                matches.forEach { option -> SwapOption(option.name) { onPick(option) } }
                if (trimmed.isNotEmpty() && !exact) SwapOption(stringResource(R.string.swap_use_new, trimmed)) { onNew(trimmed) }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun SwapOption(text: String, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp, horizontal = 4.dp),
    )
}

@Composable
private fun FinishDialog(logged: Int, planned: Int, onFinish: () -> Unit, onDiscard: () -> Unit, onDismiss: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    if (logged == 0) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.nothing_logged_title)) },
            text = { Text(stringResource(R.string.nothing_logged_body)) },
            confirmButton = { TextButton(onClick = onDiscard) { Text(stringResource(R.string.discard_workout), color = colors.error) } },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.keep_going)) } },
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.finish_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.finish_body, logged, planned))
                    TextButton(onClick = onDiscard, contentPadding = PaddingValues(0.dp)) {
                        Text(stringResource(R.string.discard_workout), color = colors.error)
                    }
                }
            },
            confirmButton = { TextButton(onClick = onFinish) { Text(stringResource(R.string.finish)) } },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.keep_going)) } },
        )
    }
}

/** Current time, updated every second while on screen (for the elapsed clock and rest countdown). */
@Composable
private fun rememberTicker(): androidx.compose.runtime.State<Instant> {
    val state = remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            state.value = Instant.now()
            delay(1_000L - (System.currentTimeMillis() % 1_000L))
        }
    }
    return state
}

private fun elapsed(startedAt: Instant?, now: Instant): String {
    if (startedAt == null) return ""
    val d = Duration.between(startedAt, now).let { if (it.isNegative) Duration.ZERO else it }
    val h = d.toHours()
    val m = d.toMinutes() % 60
    val s = d.seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

private fun typeLabel(type: SetType): Int = when (type) {
    SetType.NORMAL -> R.string.type_normal
    SetType.DROP -> R.string.type_drop
    SetType.WARMUP -> R.string.type_warmup
    SetType.FAILURE -> R.string.type_failure
}
