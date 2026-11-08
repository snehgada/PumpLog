package com.workouttracker.ui.routines

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.workouttracker.AppContainer
import com.workouttracker.R
import com.workouttracker.data.RoutineRepository
import com.workouttracker.data.WorkoutRepository
import com.workouttracker.data.db.RoutineSummary
import com.workouttracker.domain.Badges
import com.workouttracker.domain.RoutineEditing
import com.workouttracker.ui.components.RoutineBadge
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RoutinesUiState(
    val routines: List<RoutineSummary> = emptyList(),
    val badges: Map<Long, String> = emptyMap(),
) {
    val canAdd: Boolean get() = routines.size < RoutineEditing.MAX_ROUTINES
}

class RoutinesViewModel(
    workoutRepository: WorkoutRepository,
    private val routineRepository: RoutineRepository,
) : ViewModel() {

    val state: StateFlow<RoutinesUiState> = workoutRepository.observeRoutineSummaries()
        .map { routines ->
            RoutinesUiState(routines, routines.map { it.id }.zip(Badges.labels(routines.map { it.name })).toMap())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RoutinesUiState())

    fun move(routineId: Long, delta: Int) {
        viewModelScope.launch { routineRepository.move(routineId, delta) }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { RoutinesViewModel(container.workoutRepository, container.routineRepository) }
        }
    }
}

@Composable
fun RoutinesRoute(container: AppContainer, onOpenRoutine: (Long) -> Unit, onNewRoutine: () -> Unit) {
    val viewModel: RoutinesViewModel = viewModel(factory = RoutinesViewModel.factory(container))
    val state by viewModel.state.collectAsStateWithLifecycle()
    RoutinesScreen(state, onOpenRoutine, onNewRoutine, onMove = viewModel::move)
}

@Composable
fun RoutinesScreen(
    state: RoutinesUiState,
    onOpenRoutine: (Long) -> Unit,
    onNewRoutine: () -> Unit,
    onMove: (routineId: Long, delta: Int) -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            if (state.canAdd) {
                ExtendedFloatingActionButton(
                    onClick = onNewRoutine,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.new_routine)) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                )
            }
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 20.dp,
                bottom = padding.calculateBottomPadding() + 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Column(modifier = Modifier.padding(start = 4.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.routines_title), style = MaterialTheme.typography.headlineMedium)
                    Text(
                        stringResource(R.string.routines_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            itemsIndexed(state.routines, key = { _, r -> r.id }) { index, routine ->
                RoutineListRow(
                    position = index + 1,
                    routine = routine,
                    badge = state.badges[routine.id].orEmpty(),
                    canMoveUp = index > 0,
                    canMoveDown = index < state.routines.lastIndex,
                    onClick = { onOpenRoutine(routine.id) },
                    onMove = { delta -> onMove(routine.id, delta) },
                )
            }
        }
    }
}

@Composable
private fun RoutineListRow(
    position: Int,
    routine: RoutineSummary,
    badge: String,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onClick: () -> Unit,
    onMove: (Int) -> Unit,
) {
    val resources = LocalContext.current.resources
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        ) {
            RoutineBadge(badge)
            Column(modifier = Modifier.weight(1f)) {
                Text(routine.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(
                        R.string.routine_list_meta,
                        position,
                        resources.getQuantityString(R.plurals.exercise_count, routine.exerciseCount, routine.exerciseCount),
                        resources.getQuantityString(R.plurals.set_count, routine.setCount, routine.setCount),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { onMove(-1) }, enabled = canMoveUp) {
                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = stringResource(R.string.move_earlier, routine.name))
            }
            IconButton(onClick = { onMove(1) }, enabled = canMoveDown) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = stringResource(R.string.move_later, routine.name))
            }
        }
    }
}
