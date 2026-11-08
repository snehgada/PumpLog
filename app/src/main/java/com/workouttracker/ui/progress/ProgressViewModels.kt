package com.workouttracker.ui.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.workouttracker.AppContainer
import com.workouttracker.data.ProgressRepository
import com.workouttracker.data.db.ExerciseHistoryRow
import com.workouttracker.data.settings.SettingsRepository
import com.workouttracker.domain.ExerciseProgress
import com.workouttracker.domain.ExerciseProgressSummary
import com.workouttracker.domain.ExerciseSession
import com.workouttracker.domain.WeightUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProgressListState(val loading: Boolean = true, val exercises: List<ExerciseHistoryRow> = emptyList())

class ProgressListViewModel(repository: ProgressRepository) : ViewModel() {
    val state: StateFlow<ProgressListState> = repository.observeExercises()
        .map { rows -> ProgressListState(loading = false, exercises = rows) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProgressListState())

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { ProgressListViewModel(container.progressRepository) }
        }
    }
}

enum class ProgressMetric { E1RM, TOP_SET, VOLUME }

data class ExerciseProgressState(
    val loading: Boolean = true,
    val name: String = "",
    val unit: WeightUnit = WeightUnit.KG,
    val metric: ProgressMetric = ProgressMetric.E1RM,
    val summary: ExerciseProgressSummary = ExerciseProgressSummary(emptyList(), null, null),
    /** Newest first, for the history list. */
    val sessions: List<ExerciseSession> = emptyList(),
)

class ExerciseProgressViewModel(
    private val exerciseId: Long,
    private val repository: ProgressRepository,
    settings: SettingsRepository,
) : ViewModel() {

    private val name = MutableStateFlow("")
    private val metric = MutableStateFlow(ProgressMetric.E1RM)

    init {
        viewModelScope.launch { name.value = repository.exerciseName(exerciseId).orEmpty() }
    }

    val state: StateFlow<ExerciseProgressState> = combine(
        repository.observeSessions(exerciseId),
        settings.weightUnit,
        metric,
        name,
    ) { sessions, unit, m, n ->
        ExerciseProgressState(
            loading = false,
            name = n,
            unit = unit,
            metric = m,
            summary = ExerciseProgress.summarize(sessions),
            sessions = sessions.sortedWith(compareByDescending<ExerciseSession> { it.date }.thenByDescending { it.sessionId }),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExerciseProgressState())

    fun setMetric(value: ProgressMetric) {
        metric.value = value
    }

    companion object {
        fun factory(container: AppContainer, exerciseId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer { ExerciseProgressViewModel(exerciseId, container.progressRepository, container.settings) }
        }
    }
}
