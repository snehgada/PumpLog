@file:OptIn(ExperimentalMaterial3Api::class)

package com.workouttracker.ui.routines

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.workouttracker.AppContainer
import com.workouttracker.R
import com.workouttracker.domain.ExerciseDraft
import com.workouttracker.domain.ExerciseIssue
import com.workouttracker.domain.ExerciseKind
import com.workouttracker.domain.NameIssue
import com.workouttracker.domain.RoutineEditing
import com.workouttracker.domain.SetIssue

@Composable
fun RoutineEditorRoute(container: AppContainer, routineId: Long?, onDone: () -> Unit) {
    val viewModel: RoutineEditorViewModel = viewModel(
        key = "routine-editor-${routineId ?: "new"}",
        factory = RoutineEditorViewModel.factory(container, routineId),
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val options by viewModel.exerciseOptions.collectAsStateWithLifecycle()

    LaunchedEffect(state.finished, state.missing) {
        if (state.finished || state.missing) onDone()
    }
    RoutineEditorScreen(state, options, viewModel, onBack = onDone)
}

@Composable
private fun RoutineEditorScreen(
    state: EditorUiState,
    options: List<ExerciseOption>,
    vm: RoutineEditorViewModel,
    onBack: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var addingExercise by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalContext.current.resources

    val leave: () -> Unit = {
        if (state.dirty) {
            confirmDiscard = true
        } else {
            onBack()
        }
    }
    BackHandler(enabled = state.dirty) { confirmDiscard = true }

    LaunchedEffect(state.error) {
        val error = state.error ?: return@LaunchedEffect
        snackbar.showSnackbar(
            resources.getString(if (error == EditorError.SAVE_FAILED) R.string.save_failed else R.string.delete_failed),
        )
        vm.clearError()
    }

    Scaffold(
        containerColor = colors.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (state.isNew) R.string.new_routine else R.string.edit_routine)) },
                navigationIcon = {
                    IconButton(onClick = leave) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    Button(
                        onClick = vm::save,
                        enabled = !state.loading && !state.busy && (state.dirty || state.isNew),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = colors.onPrimary),
                        modifier = Modifier.padding(end = 8.dp),
                    ) { Text(stringResource(R.string.save)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background),
            )
        },
    ) { padding ->
        if (state.loading) return@Scaffold
        val draft = state.draft
        val issues = if (state.showIssues) state.issues else null

        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 4.dp,
                bottom = padding.calculateBottomPadding() + 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = vm::setName,
                    label = { Text(stringResource(R.string.routine_name)) },
                    singleLine = true,
                    isError = issues?.name != null,
                    supportingText = if (issues?.name != null) {
                        { Text(stringResource(if (issues.name == NameIssue.BLANK) R.string.issue_name_blank else R.string.issue_name_long)) }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                val sets = draft.exercises.sumOf { it.sets.size }
                Text(
                    stringResource(
                        R.string.routine_meta,
                        resources.getQuantityString(R.plurals.exercise_count, draft.exercises.size, draft.exercises.size),
                        resources.getQuantityString(R.plurals.set_count, sets, sets),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                )
            }
            if (issues?.noExercises == true) {
                item { Text(stringResource(R.string.issue_no_exercises), color = colors.error, style = MaterialTheme.typography.bodyMedium) }
            }
            itemsIndexed(draft.exercises, key = { _, ex -> ex.key }) { index, exercise ->
                ExerciseCard(
                    exercise = exercise,
                    expanded = state.expandedKey == exercise.key,
                    canMoveUp = index > 0,
                    canMoveDown = index < draft.exercises.lastIndex,
                    exerciseIssue = issues?.exercises?.get(exercise.key),
                    setIssues = issues?.sets?.get(exercise.key).orEmpty(),
                    vm = vm,
                )
            }
            item {
                OutlinedButton(
                    onClick = { addingExercise = true },
                    enabled = draft.exercises.size < RoutineEditing.MAX_EXERCISES,
                    border = BorderStroke(1.dp, colors.outlineVariant),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text(stringResource(R.string.add_exercise), modifier = Modifier.padding(start = 8.dp), color = colors.onSurface)
                }
            }
            if (state.canDelete) {
                item {
                    TextButton(
                        onClick = { confirmDelete = true },
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    ) { Text(stringResource(R.string.delete_routine), color = colors.error) }
                }
            }
        }
    }

    if (addingExercise) {
        AddExerciseDialog(
            options = options,
            onDismiss = { addingExercise = false },
            onAdd = { input -> vm.addExercise(input); addingExercise = false },
        )
    }
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(stringResource(R.string.discard_title)) },
            confirmButton = {
                TextButton(onClick = { confirmDiscard = false; onBack() }) { Text(stringResource(R.string.discard), color = colors.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(R.string.keep_editing)) } },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_title, state.draft.name.ifBlank { "routine" })) },
            text = { Text(stringResource(R.string.delete_body)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; vm.delete() }) { Text(stringResource(R.string.delete), color = colors.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun ExerciseCard(
    exercise: ExerciseDraft,
    expanded: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    exerciseIssue: ExerciseIssue?,
    setIssues: Map<Int, SetIssue>,
    vm: RoutineEditorViewModel,
) {
    val colors = MaterialTheme.colorScheme
    val hasIssue = exerciseIssue != null || setIssues.isNotEmpty()
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = colors.surfaceContainer,
        border = when {
            hasIssue -> BorderStroke(1.dp, colors.error)
            expanded -> BorderStroke(1.dp, colors.primaryContainer)
            else -> null
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClickLabel = stringResource(if (expanded) R.string.collapse else R.string.expand)) {
                            vm.toggleExpanded(exercise.key)
                        }
                        .padding(vertical = 8.dp),
                ) {
                    Text(exercise.name, style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(RoutineEditing.scheme(exercise.sets), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                        if (exercise.isRpt) {
                            Text(
                                stringResource(R.string.rpt),
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.secondary,
                            )
                        }
                    }
                    if (exercise.alternativeName.isNotBlank() && !expanded) {
                        Text(
                            stringResource(R.string.alternative_short, exercise.alternativeName),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
                IconButton(onClick = { vm.moveExercise(exercise.key, -1) }, enabled = canMoveUp) {
                    Icon(Icons.Filled.KeyboardArrowUp, contentDescription = stringResource(R.string.move_earlier, exercise.name))
                }
                IconButton(onClick = { vm.moveExercise(exercise.key, 1) }, enabled = canMoveDown) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = stringResource(R.string.move_later, exercise.name))
                }
            }

            if (expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(end = 10.dp, bottom = 8.dp)) {
                    exercise.sets.forEachIndexed { index, set ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                stringResource(R.string.set_number, index + 1),
                                style = MaterialTheme.typography.labelLarge,
                                color = colors.onSurfaceVariant,
                                modifier = Modifier.width(48.dp),
                            )
                            RepField(
                                value = set.minText,
                                label = R.string.min_reps,
                                isError = setIssues[index] == SetIssue.MIN_INVALID,
                                onChange = { vm.setMin(exercise.key, index, it) },
                                modifier = Modifier.weight(1f),
                            )
                            Text("–", color = colors.onSurfaceVariant)
                            RepField(
                                value = set.maxText,
                                label = R.string.max_reps,
                                placeholder = "+",
                                isError = setIssues[index] == SetIssue.MAX_INVALID || setIssues[index] == SetIssue.MAX_BELOW_MIN,
                                onChange = { vm.setMax(exercise.key, index, it) },
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = { vm.removeSet(exercise.key, index) }, enabled = exercise.sets.size > 1) {
                                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.remove_set, index + 1))
                            }
                        }
                        setIssues[index]?.let { issue ->
                            Text(stringResource(setIssueText(issue)), color = colors.error, style = MaterialTheme.typography.bodySmall)
                        }
                        set.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant) }
                    }
                    TextButton(
                        onClick = { vm.addSet(exercise.key) },
                        enabled = exercise.sets.size < RoutineEditing.MAX_SETS,
                    ) { Text(stringResource(R.string.add_set)) }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.rest), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        TextButton(onClick = { vm.adjustRest(exercise.key, -1) }) { Text(stringResource(R.string.rest_minus)) }
                        Text(formatRest(exercise.restSec), style = MaterialTheme.typography.titleMedium)
                        TextButton(onClick = { vm.adjustRest(exercise.key, 1) }) { Text(stringResource(R.string.rest_plus)) }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.rpt_title), style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.rpt_body), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                        }
                        Switch(
                            checked = exercise.isRpt,
                            onCheckedChange = { vm.setRpt(exercise.key, it) },
                            colors = SwitchDefaults.colors(checkedTrackColor = colors.primary, checkedThumbColor = colors.onPrimary),
                        )
                    }
                    OutlinedTextField(
                        value = exercise.alternativeName,
                        onValueChange = { vm.setAlternative(exercise.key, it) },
                        label = { Text(stringResource(R.string.alternative_label)) },
                        singleLine = true,
                        isError = exerciseIssue == ExerciseIssue.ALTERNATIVE_SAME_AS_EXERCISE,
                        supportingText = if (exerciseIssue == ExerciseIssue.ALTERNATIVE_SAME_AS_EXERCISE) {
                            { Text(stringResource(R.string.issue_alternative_same)) }
                        } else {
                            null
                        },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextButton(onClick = { vm.removeExercise(exercise.key) }) {
                        Text(stringResource(R.string.remove_exercise), color = colors.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun RepField(
    value: String,
    @StringRes label: Int,
    isError: Boolean,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(label)) },
        placeholder = if (placeholder != null) {
            { Text(placeholder) }
        } else {
            null
        },
        singleLine = true,
        isError = isError,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
        modifier = modifier,
    )
}

@Composable
private fun AddExerciseDialog(
    options: List<ExerciseOption>,
    onDismiss: () -> Unit,
    onAdd: (AddExerciseInput) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var existingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var kind by rememberSaveable { mutableStateOf(ExerciseKind.DUMBBELL) }
    var sets by rememberSaveable { mutableStateOf("3") }
    var min by rememberSaveable { mutableStateOf("8") }
    var max by rememberSaveable { mutableStateOf("12") }
    val input = AddExerciseInput(existingId, name, kind, sets, min, max)
    val query = name.trim()
    val suggestions = if (query.length < 2 || existingId != null) emptyList() else {
        options.filter { it.name.contains(query, ignoreCase = true) && !it.name.equals(query, ignoreCase = true) }.take(4)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_exercise)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it.take(RoutineEditing.MAX_EXERCISE_NAME)
                        // Typing again detaches from a picked exercise unless the name still matches it exactly.
                        val match = options.firstOrNull { o -> o.name.equals(name.trim(), ignoreCase = true) }
                        existingId = match?.id
                        if (match != null) kind = match.kind
                    },
                    label = { Text(stringResource(R.string.exercise_name)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth(),
                )
                suggestions.forEach { option ->
                    Text(
                        option.name,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                name = option.name
                                existingId = option.id
                                kind = option.kind
                            }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                ) {
                    ExerciseKind.entries.forEach { k ->
                        FilterChip(
                            selected = kind == k,
                            onClick = { kind = k },
                            enabled = existingId == null,
                            label = { Text(stringResource(kindLabel(k))) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RepField(sets, R.string.sets, isError = false, onChange = { sets = it.filter(Char::isDigit).take(2) }, modifier = Modifier.weight(1f))
                    RepField(min, R.string.min_reps, isError = false, onChange = { min = RoutineEditing.filterRepInput(it) }, modifier = Modifier.weight(1f))
                    RepField(max, R.string.max_reps, isError = false, placeholder = "+", onChange = { max = RoutineEditing.filterRepInput(it) }, modifier = Modifier.weight(1f))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onAdd(input) }, enabled = input.isValid) { Text(stringResource(R.string.add)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

private fun formatRest(sec: Int): String = "%d:%02d".format(sec / 60, sec % 60)

@StringRes
private fun setIssueText(issue: SetIssue): Int = when (issue) {
    SetIssue.MIN_INVALID -> R.string.issue_min
    SetIssue.MAX_INVALID -> R.string.issue_max
    SetIssue.MAX_BELOW_MIN -> R.string.issue_max_below_min
}

@StringRes
private fun kindLabel(kind: ExerciseKind): Int = when (kind) {
    ExerciseKind.BARBELL -> R.string.kind_barbell
    ExerciseKind.DUMBBELL -> R.string.kind_dumbbell
    ExerciseKind.CABLE -> R.string.kind_cable
    ExerciseKind.MACHINE -> R.string.kind_machine
    ExerciseKind.BODYWEIGHT -> R.string.kind_bodyweight
}
