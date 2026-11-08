package com.workouttracker.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.workouttracker.AppContainer
import com.workouttracker.data.SessionRejected
import com.workouttracker.data.SessionRepository
import com.workouttracker.data.WorkoutRepository
import com.workouttracker.data.db.RoutineSummary
import com.workouttracker.data.db.SessionStatsRow
import com.workouttracker.data.settings.SettingsRepository
import com.workouttracker.data.toStats
import com.workouttracker.domain.Badges
import com.workouttracker.domain.DayStatus
import com.workouttracker.domain.WeekSummary
import com.workouttracker.domain.Weeks
import com.workouttracker.domain.WeightUnit
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate

enum class ChartMetric { SETS, VOLUME, DAYS }

data class DayCell(
    val date: LocalDate,
    val status: DayStatus,
    /** Routine badge for a workout day ("U", "Pl"…), empty otherwise. */
    val label: String,
    val isToday: Boolean,
    val isFuture: Boolean,
)

data class HistoryUiState(
    val loading: Boolean = true,
    val today: LocalDate = LocalDate.MIN,
    val weekStart: LocalDate = LocalDate.MIN,
    val isCurrentWeek: Boolean = true,
    val days: List<DayCell> = emptyList(),
    val week: WeekSummary? = null,
    val lastWeek: WeekSummary? = null,
    /** Eight weeks, oldest first, ending with the shown week. */
    val bars: List<WeekSummary> = emptyList(),
    val metric: ChartMetric = ChartMetric.SETS,
    /** The shown week's workouts and skips, newest first. */
    val sessions: List<SessionStatsRow> = emptyList(),
    val unit: WeightUnit = WeightUnit.KG,
    val routines: List<RoutineSummary> = emptyList(),
    /** One-shot: open this workout (just logged as a past workout). */
    val openWorkout: Long? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(
    private val workouts: WorkoutRepository,
    private val sessions: SessionRepository,
    settings: SettingsRepository,
    private val clock: Clock,
) : ViewModel() {

    private val today: LocalDate get() = LocalDate.now(clock)
    private val weekStart = MutableStateFlow(Weeks.startOf(today))
    private val metric = MutableStateFlow(ChartMetric.SETS)
    private val openWorkout = MutableStateFlow<Long?>(null)

    private val rows = weekStart.flatMapLatest { ws ->
        workouts.observeSessionRows(ws.minusWeeks(BAR_WEEKS - 1L), ws.plusDays(6))
    }

    val state: StateFlow<HistoryUiState> = combine(
        weekStart,
        rows,
        combine(settings.weightUnit, metric, ::Pair),
        workouts.observeRoutineSummaries(),
        openWorkout,
    ) { ws, allRows, (unit, chartMetric), routines, open ->
        val now = today
        val stats = allRows.map { it.toStats() }
        val week = Weeks.summarize(ws, stats)
        val weekEnd = ws.plusDays(6)
        val badges = routines.map { it.name }.zip(Badges.labels(routines.map { it.name })).toMap()
        val inWeek = allRows.filter { !it.date.isBefore(ws) && !it.date.isAfter(weekEnd) }
        HistoryUiState(
            loading = false,
            today = now,
            weekStart = ws,
            isCurrentWeek = ws == Weeks.startOf(now),
            days = (0L..6L).map { offset ->
                val date = ws.plusDays(offset)
                val doneHere = inWeek.lastOrNull { it.date == date && it.status == com.workouttracker.domain.SessionStatus.DONE }
                DayCell(
                    date = date,
                    status = week.days[offset.toInt()],
                    label = doneHere?.let { badges[it.routineName] ?: it.routineName.take(2) }.orEmpty(),
                    isToday = date == now,
                    isFuture = date.isAfter(now),
                )
            },
            week = week,
            lastWeek = Weeks.summarize(ws.minusWeeks(1), stats),
            bars = Weeks.recent(ws, BAR_WEEKS, stats),
            metric = chartMetric,
            sessions = inWeek.sortedWith(compareByDescending<SessionStatsRow> { it.date }.thenByDescending { it.sessionId }),
            unit = unit,
            routines = routines,
            openWorkout = open,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    fun previousWeek() {
        weekStart.value = weekStart.value.minusWeeks(1)
    }

    fun nextWeek() {
        val next = weekStart.value.plusWeeks(1)
        if (!next.isAfter(Weeks.startOf(today))) weekStart.value = next
    }

    fun setMetric(value: ChartMetric) {
        metric.value = value
    }

    fun undoSkip(sessionId: Long) {
        viewModelScope.launch { workouts.undoSkip(sessionId) }
    }

    /** Records a forgotten workout on [date] and opens it so its sets can be filled in. */
    fun logPast(routineId: Long, date: LocalDate) {
        viewModelScope.launch {
            try {
                openWorkout.value = sessions.logPast(routineId, date, today)
            } catch (e: SessionRejected) {
                // Date out of range or routine gone: the picker prevents both; nothing to open.
            }
        }
    }

    fun workoutOpened() {
        openWorkout.value = null
    }

    companion object {
        const val BAR_WEEKS = 8

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { HistoryViewModel(container.workoutRepository, container.sessionRepository, container.settings, container.clock) }
        }
    }
}
