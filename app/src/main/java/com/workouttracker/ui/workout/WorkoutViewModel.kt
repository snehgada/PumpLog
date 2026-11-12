package com.workouttracker.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.workouttracker.AppContainer
import com.workouttracker.data.ExerciseRef
import com.workouttracker.data.PlanExercise
import com.workouttracker.data.SessionPlan
import com.workouttracker.data.SessionRejected
import com.workouttracker.data.SessionRepository
import com.workouttracker.data.RoutineRepository
import com.workouttracker.data.settings.SettingsRepository
import com.workouttracker.domain.ExerciseKind
import com.workouttracker.domain.Increments
import com.workouttracker.domain.LiveSessionRules
import com.workouttracker.domain.PerformedSet
import com.workouttracker.domain.Segment
import com.workouttracker.domain.SessionStatus
import com.workouttracker.domain.SetTarget
import com.workouttracker.domain.SetType
import com.workouttracker.domain.WeightUnit
import com.workouttracker.domain.Weights
import com.workouttracker.timer.RestState
import com.workouttracker.timer.RestTimer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant

private typealias RowKey = Pair<Long, Int>

data class SetRowUi(
    val index: Int,
    /** Null for an extra set beyond the plan. */
    val target: SetTarget?,
    val lastTime: String,
    val weightText: String,
    val repsText: String,
    val logged: PerformedSet?,
    val invalid: Boolean,
)

data class ExerciseUi(
    val plannedId: Long,
    val plannedName: String,
    /** What is actually being done today (differs from [plannedName] after a swap). */
    val performed: ExerciseRef,
    val alternative: ExerciseRef?,
    val isRpt: Boolean,
    val restSec: Int,
    val scheme: String,
    /** e.g. "2.5 kg" when last time hit the top of every range; null otherwise. */
    val suggestedIncrease: String?,
    val rows: List<SetRowUi>,
) {
    val swapped: Boolean get() = performed.id != plannedId
    val plannedCount: Int get() = rows.count { it.target != null }
    val loggedCount: Int get() = rows.count { it.logged != null }
    val complete: Boolean get() = rows.filter { it.target != null }.all { it.logged != null }
}

/** The set details sheet: type, drop-set blocks and note. Text fields stay text until saved. */
data class SetSheetState(
    val plannedId: Long,
    val index: Int,
    val exerciseName: String,
    val type: SetType,
    val blocks: List<Pair<String, String>>,
    val note: String,
    val isLogged: Boolean,
    val invalid: Boolean = false,
)

enum class WorkoutError { SAVE_FAILED }

data class WorkoutUiState(
    val loading: Boolean = true,
    /** The workout no longer exists or is already finished: leave the screen. */
    val closed: Boolean = false,
    val routineName: String = "",
    val startedAt: Instant? = null,
    /** Editing a finished workout from History: no rest timer, no suggestion, "Done" instead of Finish. */
    val editing: Boolean = false,
    val date: java.time.LocalDate? = null,
    val unit: WeightUnit = WeightUnit.KG,
    val exercises: List<ExerciseUi> = emptyList(),
    val expandedId: Long? = null,
    val sheet: SetSheetState? = null,
    val swapFor: Long? = null,
    val rest: RestState? = null,
    val error: WorkoutError? = null,
    val batteryTipDismissed: Boolean = true,
) {
    val loggedCount: Int get() = exercises.sumOf { it.loggedCount }
    val plannedCount: Int get() = exercises.sumOf { it.plannedCount }
}

private data class Edits(
    val inputs: Map<RowKey, Pair<String, String>> = emptyMap(),
    val invalid: Set<RowKey> = emptySet(),
    val extraSets: Map<Long, Int> = emptyMap(),
    val swaps: Map<Long, ExerciseRef> = emptyMap(),
    val expandedId: Long? = null,
    val expandedTouched: Boolean = false,
    val sheet: SetSheetState? = null,
    val swapFor: Long? = null,
    val error: WorkoutError? = null,
)

class WorkoutViewModel(
    private val sessionId: Long,
    private val sessions: SessionRepository,
    routines: RoutineRepository,
    private val settings: SettingsRepository,
    private val restTimer: RestTimer,
    private val clock: Clock,
) : ViewModel() {

    private val plan = MutableStateFlow<SessionPlan?>(null)
    private val missing = MutableStateFlow(false)
    private val edits = MutableStateFlow(Edits())

    private val options = routines.observeExercises().map { list -> list.map { ExerciseRef(it.id, it.name, it.kind) } }

    val exerciseOptions: StateFlow<List<ExerciseRef>> =
        options.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            val loaded = sessions.loadPlan(sessionId)
            if (loaded == null) missing.value = true else plan.value = loaded
        }
    }

    private data class Env(
        val unit: WeightUnit,
        val increments: Increments,
        val rest: RestState?,
        val options: List<ExerciseRef>,
        val batteryTipDismissed: Boolean,
    )

    private val env = combine(settings.weightUnit, settings.increments, restTimer.state, options, settings.batteryTipDismissed, ::Env)

    // Emits an empty map until the plan is loaded, so the combined state can still report a missing workout.
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val logged = plan.flatMapLatest { p -> if (p == null) flowOf(emptyMap()) else sessions.observeLogged(sessionId) }

    val state: StateFlow<WorkoutUiState> = combine(
        plan,
        logged,
        sessions.observeSession(sessionId),
        edits,
        env,
    ) { p, loggedSets, session, e, env ->
        if (p == null) return@combine WorkoutUiState()
        // A live workout closes the screen once it is finished or discarded; a finished one being edited
        // closes only if it is deleted.
        val editing = p.session.status == SessionStatus.DONE
        val gone = session == null ||
            session.status == SessionStatus.SKIPPED ||
            (!editing && session.status != SessionStatus.IN_PROGRESS)
        if (gone) return@combine WorkoutUiState(loading = false, closed = true)
        val unit = env.unit
        val rest = if (editing) null else env.rest
        val exercises = p.exercises.map {
            buildExercise(it, loggedSets[it.exercise.id].orEmpty(), e, unit, env.increments, env.options, editing)
        }
        val expanded = if (e.expandedTouched) e.expandedId else exercises.firstOrNull { !it.complete }?.plannedId
        WorkoutUiState(
            loading = false,
            routineName = session.routineName,
            startedAt = session.startedAt,
            editing = editing,
            date = session.date,
            unit = unit,
            exercises = exercises,
            expandedId = expanded,
            sheet = e.sheet,
            swapFor = e.swapFor,
            rest = rest,
            error = e.error,
            batteryTipDismissed = env.batteryTipDismissed,
        )
    }.combine(missing) { s, gone -> if (gone) WorkoutUiState(loading = false, closed = true) else s }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WorkoutUiState())

    private fun buildExercise(
        planned: PlanExercise,
        loggedSets: List<PerformedSet>,
        e: Edits,
        unit: WeightUnit,
        increments: Increments,
        options: List<ExerciseRef>,
        editing: Boolean,
    ): ExerciseUi {
        val pid = planned.exercise.id
        val increment = LiveSessionRules.incrementFor(planned.exercise.kind, increments)
        val suggestion = LiveSessionRules.suggestion(planned.lastTime, planned.targets, increment)
        val byIndex = loggedSets.associateBy { it.setIndex }
        val rowCount = maxOf(
            planned.targets.size + (e.extraSets[pid] ?: 0),
            (loggedSets.maxOfOrNull { it.setIndex } ?: -1) + 1,
        )
        var previousWeight = ""
        val rows = (0 until rowCount).map { i ->
            val logged = byIndex[i]
            val typed = e.inputs[pid to i]
            val weight = typed?.first
                ?: logged?.segments?.firstOrNull()?.let { Weights.format(it.weightGrams, unit) }
                ?: LiveSessionRules.prefillGrams(i, suggestion, planned.lastTime)?.let { Weights.format(it, unit) }
                ?: previousWeight
            val reps = typed?.second ?: logged?.segments?.firstOrNull()?.reps?.toString().orEmpty()
            previousWeight = weight
            SetRowUi(
                index = i,
                target = planned.targets.getOrNull(i),
                lastTime = LiveSessionRules.lastTimeText(planned.lastTime.firstOrNull { it.setIndex == i }, unit)
                    .let { if (it == "—") it else "$it ${unit.label}" },
                weightText = weight,
                repsText = reps,
                logged = logged,
                invalid = (pid to i) in e.invalid,
            )
        }
        val swappedTo = e.swaps[pid]
            ?: loggedSets.firstOrNull()?.performedExerciseId?.takeIf { it != pid }?.let { id ->
                options.firstOrNull { it.id == id } ?: planned.alternative?.takeIf { it.id == id }
            }
        return ExerciseUi(
            plannedId = pid,
            plannedName = planned.exercise.name,
            performed = swappedTo ?: planned.exercise,
            alternative = planned.alternative,
            isRpt = planned.isRpt,
            restSec = planned.restSec,
            scheme = LiveSessionRules.scheme(planned.targets),
            suggestedIncrease = suggestion?.takeUnless { editing }?.let { "${Weights.format(increment, unit)} ${unit.label}" },
            rows = rows,
        )
    }

    // ---- Set rows ----

    fun onWeight(plannedId: Long, index: Int, text: String) = editRow(plannedId, index) { it.copy(first = LiveSessionRules.filterWeightInput(text)) }

    fun onReps(plannedId: Long, index: Int, text: String) =
        editRow(plannedId, index) { it.copy(second = text.filter { c -> c in '0'..'9' }.take(3)) }

    private fun editRow(plannedId: Long, index: Int, change: (Pair<String, String>) -> Pair<String, String>) {
        val row = rowOf(plannedId, index) ?: return
        edits.update { e ->
            val current = e.inputs[plannedId to index] ?: (row.weightText to row.repsText)
            e.copy(inputs = e.inputs + ((plannedId to index) to change(current)), invalid = e.invalid - (plannedId to index))
        }
    }

    /** The check button: logs the row, or un-logs it if it is already logged. */
    fun toggleSet(plannedId: Long, index: Int) {
        val exercise = exerciseOf(plannedId) ?: return
        val row = exercise.rows.getOrNull(index) ?: return
        val logged = row.logged
        if (logged != null) {
            launchWrite { sessions.unlogSet(sessionId, logged.id) }
            return
        }
        val segment = LiveSessionRules.parseSegment(row.weightText, row.repsText, state.value.unit)
        if (segment == null) {
            edits.update { it.copy(invalid = it.invalid + (plannedId to index)) }
            return
        }
        log(exercise, index, SetType.NORMAL, null, listOf(segment))
    }

    fun addSet(plannedId: Long) = edits.update { e ->
        val extra = (e.extraSets[plannedId] ?: 0) + 1
        if (extra > LiveSessionRules.MAX_EXTRA_SETS) e else e.copy(extraSets = e.extraSets + (plannedId to extra))
    }

    fun toggleExpanded(plannedId: Long) = edits.update { e ->
        val current = state.value.expandedId
        e.copy(expandedId = if (current == plannedId) null else plannedId, expandedTouched = true)
    }

    // ---- Set details sheet ----

    fun openSheet(plannedId: Long, index: Int?) {
        val exercise = exerciseOf(plannedId) ?: return
        val i = index ?: exercise.rows.firstOrNull { it.logged == null }?.index ?: exercise.rows.lastIndex
        val row = exercise.rows.getOrNull(i) ?: return
        val unit = state.value.unit
        val logged = row.logged
        edits.update {
            it.copy(
                sheet = SetSheetState(
                    plannedId = plannedId,
                    index = i,
                    exerciseName = exercise.performed.name,
                    type = logged?.type ?: SetType.NORMAL,
                    blocks = logged?.segments?.map { s -> Weights.format(s.weightGrams, unit) to s.reps.toString() }
                        ?: listOf(row.weightText to row.repsText),
                    note = logged?.note.orEmpty(),
                    isLogged = logged != null,
                ),
            )
        }
    }

    fun closeSheet() = edits.update { it.copy(sheet = null) }

    fun sheetType(type: SetType) = editSheet { s ->
        val blocks = if (type == SetType.DROP && s.blocks.size == 1) s.blocks + (s.blocks.first().first to "") else s.blocks
        s.copy(type = type, blocks = if (type == SetType.DROP) blocks else blocks.take(1))
    }

    fun sheetBlock(position: Int, weight: String? = null, reps: String? = null) = editSheet { s ->
        s.copy(
            blocks = s.blocks.mapIndexed { i, b ->
                if (i != position) b else (weight?.let(LiveSessionRules::filterWeightInput) ?: b.first) to (reps?.filter { it in '0'..'9' }?.take(3) ?: b.second)
            },
        )
    }

    fun sheetAddDrop() = editSheet { s ->
        if (s.blocks.size >= LiveSessionRules.MAX_SEGMENTS) s else s.copy(type = SetType.DROP, blocks = s.blocks + (s.blocks.last().first to ""))
    }

    fun sheetRemoveBlock(position: Int) = editSheet { s ->
        if (s.blocks.size <= 1) s else s.copy(blocks = s.blocks.filterIndexed { i, _ -> i != position })
    }

    fun sheetNote(text: String) = editSheet { it.copy(note = text.take(LiveSessionRules.MAX_NOTE)) }

    fun saveSheet() {
        val sheet = state.value.sheet ?: return
        val exercise = exerciseOf(sheet.plannedId) ?: return
        val unit = state.value.unit
        val segments = sheet.blocks.map { (w, r) -> LiveSessionRules.parseSegment(w, r, unit) }
        if (segments.any { it == null } || !LiveSessionRules.isValid(segments.filterNotNull())) {
            editSheet { it.copy(invalid = true) }
            return
        }
        edits.update { it.copy(sheet = null) }
        log(exercise, sheet.index, sheet.type, sheet.note, segments.filterNotNull(), startRest = !sheet.isLogged)
    }

    fun removeFromSheet() {
        val sheet = state.value.sheet ?: return
        val logged = exerciseOf(sheet.plannedId)?.rows?.getOrNull(sheet.index)?.logged
        edits.update { it.copy(sheet = null) }
        if (logged != null) launchWrite { sessions.unlogSet(sessionId, logged.id) }
    }

    private fun editSheet(change: (SetSheetState) -> SetSheetState) = edits.update { e ->
        e.copy(sheet = e.sheet?.let { change(it).copy(invalid = false) })
    }

    // ---- Swaps ----

    fun openSwap(plannedId: Long) = edits.update { it.copy(swapFor = plannedId, sheet = null) }

    fun closeSwap() = edits.update { it.copy(swapFor = null) }

    fun swapTo(plannedId: Long, target: ExerciseRef) {
        // A rest already counting down for this exercise now leads into the swapped one.
        val before = exerciseOf(plannedId)?.performed?.name
        if (before != null) restTimer.renameNext(from = before, to = target.name)
        edits.update { it.copy(swaps = it.swaps + (plannedId to target), swapFor = null) }
        launchWrite { sessions.swap(sessionId, plannedId, target.id) }
    }

    fun swapToNew(plannedId: Long, name: String, kind: ExerciseKind) {
        launchWrite {
            val ref = sessions.findOrCreateExercise(name, kind)
            swapTo(plannedId, ref)
        }
    }

    // ---- Rest, finish ----

    fun addRest(seconds: Int) = restTimer.add(seconds)

    fun skipRest() = restTimer.skip()

    fun finish() = launchWrite {
        if (sessions.finish(sessionId, clock.instant())) restTimer.skip()
    }

    fun discard() = launchWrite {
        if (sessions.discard(sessionId)) restTimer.skip()
    }

    /** Deletes a finished workout being edited, with its sets. */
    fun deleteWorkout() = launchWrite { sessions.deleteWorkout(sessionId) }

    fun clearError() = edits.update { it.copy(error = null) }

    fun dismissBatteryTip() {
        viewModelScope.launch { settings.dismissBatteryTip() }
    }

    // ---- Helpers ----

    private fun log(exercise: ExerciseUi, index: Int, type: SetType, note: String?, segments: List<Segment>, startRest: Boolean = true) {
        val key = exercise.plannedId to index
        edits.update { it.copy(inputs = it.inputs - key, invalid = it.invalid - key) }
        launchWrite {
            sessions.logSet(sessionId, exercise.plannedId, exercise.performed.id, index, type, note, segments, clock.instant())
            if (startRest && !state.value.editing) restTimer.start(exercise.restSec, nextUpName(exercise, index))
            // Logging the last planned set of this exercise moves the focus to the next unfinished one.
            val remaining = exercise.rows.count { it.target != null && it.logged == null && it.index != index }
            if (remaining == 0) edits.update { it.copy(expandedTouched = false) }
        }
    }

    private fun nextUpName(exercise: ExerciseUi, justLogged: Int): String {
        val more = exercise.rows.any { it.target != null && it.logged == null && it.index != justLogged }
        if (more) return exercise.performed.name
        val list = state.value.exercises
        val after = list.dropWhile { it.plannedId != exercise.plannedId }.drop(1)
        return (after + list).firstOrNull { it.plannedId != exercise.plannedId && !it.complete }?.performed?.name ?: exercise.performed.name
    }

    private fun exerciseOf(plannedId: Long): ExerciseUi? = state.value.exercises.firstOrNull { it.plannedId == plannedId }

    private fun rowOf(plannedId: Long, index: Int): SetRowUi? = exerciseOf(plannedId)?.rows?.getOrNull(index)

    private fun launchWrite(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: SessionRejected) {
                edits.update { it.copy(error = WorkoutError.SAVE_FAILED) }
            }
        }
    }

    companion object {
        fun factory(container: AppContainer, sessionId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                WorkoutViewModel(
                    sessionId,
                    container.sessionRepository,
                    container.routineRepository,
                    container.settings,
                    container.restTimer,
                    container.clock,
                )
            }
        }
    }
}

private val WeightUnit.label: String get() = if (this == WeightUnit.KG) "kg" else "lb"
