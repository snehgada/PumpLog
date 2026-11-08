package com.workouttracker.ui.welcome

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.workouttracker.data.settings.SettingsRepository
import com.workouttracker.domain.WeightUnit
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class WelcomeRoutine(val id: Long, val name: String, val previewNames: List<String>, val moreCount: Int)

data class WelcomeUiState(
    val routines: List<WelcomeRoutine> = emptyList(),
    val unit: WeightUnit = WeightUnit.KG,
)

class WelcomeViewModel(
    workoutRepository: WorkoutRepository,
    routineRepository: RoutineRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    val state: StateFlow<WelcomeUiState> = combine(
        workoutRepository.observeRoutineSummaries(),
        routineRepository.observeRoutineExerciseNames(),
        settings.weightUnit,
    ) { routines, names, unit ->
        val byRoutine = names.groupBy({ it.routineId }, { it.name })
        WelcomeUiState(
            routines = routines.map { r ->
                val all = byRoutine[r.id].orEmpty()
                WelcomeRoutine(r.id, r.name, all.take(PREVIEW_COUNT), (all.size - PREVIEW_COUNT).coerceAtLeast(0))
            },
            unit = unit,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WelcomeUiState())

    fun setUnit(unit: WeightUnit) {
        viewModelScope.launch { settings.setWeightUnit(unit) }
    }

    suspend fun finish() = settings.markWelcomeDone()

    companion object {
        private const val PREVIEW_COUNT = 2

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { WelcomeViewModel(container.workoutRepository, container.routineRepository, container.settings) }
        }
    }
}

@Composable
fun WelcomeRoute(container: AppContainer, onContinue: () -> Unit, onReviewRoutines: () -> Unit) {
    val viewModel: WelcomeViewModel = viewModel(factory = WelcomeViewModel.factory(container))
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    WelcomeScreen(
        state = state,
        onUnit = viewModel::setUnit,
        onContinue = { scope.launch { viewModel.finish(); onContinue() } },
        onReviewRoutines = { scope.launch { viewModel.finish(); onReviewRoutines() } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WelcomeScreen(
    state: WelcomeUiState,
    onUnit: (WeightUnit) -> Unit,
    onContinue: () -> Unit,
    onReviewRoutines: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .systemBarsPadding(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
        ) {
            Text(stringResource(R.string.welcome_kicker), style = MaterialTheme.typography.labelSmall, color = colors.primary)
            Text(stringResource(R.string.welcome_title), style = MaterialTheme.typography.headlineMedium)
            Text(
                stringResource(R.string.welcome_body),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.size(8.dp))

            state.routines.forEachIndexed { index, routine ->
                RotationRow(position = index + 1, routine = routine, isFirst = index == 0)
            }

            Spacer(Modifier.size(8.dp))
            Surface(shape = RoundedCornerShape(16.dp), color = colors.surfaceContainer) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                ) {
                    Text(stringResource(R.string.units), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    SingleChoiceSegmentedButtonRow {
                        WeightUnit.entries.forEachIndexed { index, unit ->
                            SegmentedButton(
                                selected = state.unit == unit,
                                onClick = { onUnit(unit) },
                                shape = SegmentedButtonDefaults.itemShape(index, WeightUnit.entries.size),
                                colors = SegmentedButtonDefaults.colors(
                                    activeContainerColor = colors.primary,
                                    activeContentColor = colors.onPrimary,
                                    activeBorderColor = colors.primary,
                                ),
                                icon = {},
                            ) {
                                Text(stringResource(if (unit == WeightUnit.KG) R.string.unit_kg else R.string.unit_lb))
                            }
                        }
                    }
                }
            }
        }

        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Button(
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = colors.onPrimary),
            ) {
                Text(stringResource(R.string.welcome_continue), style = MaterialTheme.typography.labelLarge)
            }
            TextButton(onClick = onReviewRoutines, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(R.string.welcome_review), color = colors.onSurface)
            }
        }
    }
}

@Composable
private fun RotationRow(position: Int, routine: WelcomeRoutine, isFirst: Boolean) {
    val colors = MaterialTheme.colorScheme
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(32.dp)
                .then(
                    if (isFirst) Modifier.background(colors.primary, CircleShape)
                    else Modifier.border(1.dp, colors.outlineVariant, CircleShape),
                ),
        ) {
            Text(
                position.toString(),
                style = MaterialTheme.typography.labelLarge,
                color = if (isFirst) colors.onPrimary else colors.onSurfaceVariant,
            )
        }
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (isFirst) colors.primaryContainer.copy(alpha = 0.35f) else colors.surfaceContainer,
            border = if (isFirst) BorderStroke(1.dp, colors.primaryContainer) else null,
            modifier = Modifier.weight(1f),
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(routine.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    if (isFirst) {
                        Text(
                            stringResource(R.string.welcome_up_first),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onPrimary,
                            modifier = Modifier
                                .background(colors.primary, RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
                val preview = buildString {
                    append(routine.previewNames.joinToString(", "))
                    if (routine.moreCount > 0) append(" +").append(routine.moreCount)
                }
                Text(preview, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
        }
    }
}
