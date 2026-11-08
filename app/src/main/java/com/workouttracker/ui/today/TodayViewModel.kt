package com.workouttracker.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.workouttracker.AppContainer
import com.workouttracker.data.SkipResult
import com.workouttracker.data.WorkoutRepository
import com.workouttracker.data.db.RoutineSummary
import com.workouttracker.domain.Badges
import com.workouttracker.domain.Rotation
import com.workouttracker.domain.RotationStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate

data class RotationItem(val routine: RoutineSummary, val status: RotationStatus)

/** Shown once after a skip so it can be undone. */
data class SkipNotice(val sessionId: Long, val routineName: String)

data class TodayUiState(
    val loading: Boolean = true,
    val date: LocalDate? = null,
    val rotation: List<RotationItem> = emptyList(),
    val next: RoutineSummary? = null,
    val routines: List<RoutineSummary> = emptyList(),
    /** Badge text per routine id, e.g. "Pu" for Push and "Pl" for Pull. */
    val badges: Map<Long, String> = emptyMap(),
    val skipNotice: SkipNotice? = null,
)

class TodayViewModel(
    private val repository: WorkoutRepository,
    private val clock: Clock,
) : ViewModel() {

    private val skipNotice = MutableStateFlow<SkipNotice?>(null)

    val state: StateFlow<TodayUiState> = combine(
        repository.observeRoutineSummaries(),
        repository.observeLatestRotationEntry(),
        repository.observeLatestEntryPerRoutine(),
        skipNotice,
    ) { routines, latest, perRoutine, notice ->
        val order = routines.map { it.id }
        val nextId = Rotation.next(order, latest?.routineId)
        val lastStatus = perRoutine.mapNotNull { s -> s.routineId?.let { it to s.status } }.toMap()
        val statuses = Rotation.cycle(order, nextId, lastStatus)
        TodayUiState(
            loading = false,
            date = LocalDate.now(clock),
            rotation = routines.zip(statuses) { routine, status -> RotationItem(routine, status) },
            next = routines.firstOrNull { it.id == nextId },
            routines = routines,
            badges = order.zip(Badges.labels(routines.map { it.name })).toMap(),
            skipNotice = notice,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState())

    fun skip(routineId: Long) {
        viewModelScope.launch {
            when (val result = repository.skip(routineId, LocalDate.now(clock))) {
                is SkipResult.Skipped -> skipNotice.value = SkipNotice(result.sessionId, result.routineName)
                // Not reachable from Today until live logging lands; nothing to show yet.
                SkipResult.WorkoutInProgress, SkipResult.RoutineNotFound -> Unit
            }
        }
    }

    fun undoSkip(sessionId: Long) {
        viewModelScope.launch { repository.undoSkip(sessionId) }
    }

    fun skipNoticeShown() {
        skipNotice.value = null
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { TodayViewModel(container.workoutRepository, container.clock) }
        }
    }
}
