package com.workouttracker.ui.routines

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.workouttracker.AppContainer
import com.workouttracker.data.RoutineRejected
import com.workouttracker.data.RoutineRepository
import com.workouttracker.domain.DraftIssues
import com.workouttracker.domain.ExerciseDraft
import com.workouttracker.domain.ExerciseKind
import com.workouttracker.domain.RoutineDraft
import com.workouttracker.domain.RoutineEditing
import com.workouttracker.domain.SetDraft
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ExerciseOption(val id: Long, val name: String, val kind: ExerciseKind)

enum class EditorError { SAVE_FAILED, DELETE_FAILED }

data class EditorUiState(
    val loading: Boolean = true,
    val isNew: Boolean = false,
    val draft: RoutineDraft = RoutineDraft(null, "", emptyList()),
    val issues: DraftIssues = DraftIssues(),
    /** Issues are only shown after the first save attempt, so a fresh form isn't covered in red. */
    val showIssues: Boolean = false,
    val dirty: Boolean = false,
    val expandedKey: Long? = null,
    val canDelete: Boolean = false,
    val busy: Boolean = false,
    /** The routine was deleted elsewhere. */
    val missing: Boolean = false,
    val error: EditorError? = null,
    /** Saved or deleted: the screen should close. */
    val finished: Boolean = false,
)

/** What the Add exercise dialog hands back. Already validated by [AddExerciseInput.isValid]. */
data class AddExerciseInput(
    val existingId: Long?,
    val name: String,
    val kind: ExerciseKind,
    val setCount: String,
    val minText: String,
    val maxText: String,
) {
    val sets: Int? get() = setCount.trim().toIntOrNull()?.takeIf { it in 1..RoutineEditing.MAX_SETS }

    val isValid: Boolean
        get() {
            val trimmed = name.trim()
            if (trimmed.isEmpty() || trimmed.length > RoutineEditing.MAX_EXERCISE_NAME || sets == null) return false
            val min = RoutineEditing.parseReps(minText) ?: return false
            if (maxText.isBlank()) return true
            val max = RoutineEditing.parseReps(maxText) ?: return false
            return max >= min
        }
}

class RoutineEditorViewModel(
    private val repository: RoutineRepository,
    private val routineId: Long?,
) : ViewModel() {

    private var keySeq = 0L
    private fun nextKey(): Long = ++keySeq
    private var original: RoutineDraft? = null

    private val _state = MutableStateFlow(EditorUiState(isNew = routineId == null))
    val state: StateFlow<EditorUiState> = _state.asStateFlow()

    val exerciseOptions: StateFlow<List<ExerciseOption>> = repository.observeExercises()
        .map { list -> list.map { ExerciseOption(it.id, it.name, it.kind) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            val count = repository.countRoutines()
            val draft = if (routineId == null) {
                RoutineDraft(null, "", emptyList())
            } else {
                repository.loadDraft(routineId, ::nextKey)
            }
            if (draft == null) {
                _state.update { it.copy(loading = false, missing = true) }
                return@launch
            }
            original = draft
            _state.update {
                it.copy(
                    loading = false,
                    draft = draft,
                    issues = RoutineEditing.validate(draft),
                    canDelete = routineId != null && count > 1,
                )
            }
        }
    }

    fun setName(text: String) = edit { it.copy(name = text.take(RoutineEditing.MAX_ROUTINE_NAME)) }

    fun toggleExpanded(key: Long) = _state.update { it.copy(expandedKey = if (it.expandedKey == key) null else key) }

    fun moveExercise(key: Long, delta: Int) = edit { d ->
        d.copy(exercises = RoutineEditing.move(d.exercises, d.exercises.indexOfFirst { it.key == key }, delta))
    }

    fun removeExercise(key: Long) = edit { d -> d.copy(exercises = d.exercises.filterNot { it.key == key }) }

    fun setMin(key: Long, index: Int, text: String) = editSet(key, index) { it.copy(minText = RoutineEditing.filterRepInput(text)) }

    fun setMax(key: Long, index: Int, text: String) = editSet(key, index) { it.copy(maxText = RoutineEditing.filterRepInput(text)) }

    fun addSet(key: Long) = editExercise(key) { RoutineEditing.addSet(it) }

    fun removeSet(key: Long, index: Int) = editExercise(key) { RoutineEditing.removeSet(it, index) }

    fun adjustRest(key: Long, steps: Int) = editExercise(key) { it.copy(restSec = RoutineEditing.adjustRest(it.restSec, steps)) }

    fun setRpt(key: Long, rpt: Boolean) = editExercise(key) { it.copy(isRpt = rpt) }

    fun setAlternative(key: Long, text: String) =
        editExercise(key) { it.copy(alternativeName = text.take(RoutineEditing.MAX_EXERCISE_NAME)) }

    fun addExercise(input: AddExerciseInput) {
        val sets = input.sets
        if (!input.isValid || sets == null) return
        if (_state.value.draft.exercises.size >= RoutineEditing.MAX_EXERCISES) return
        val key = nextKey()
        val top = RoutineEditing.parseReps(input.maxText) ?: Int.MAX_VALUE
        val exercise = ExerciseDraft(
            key = key,
            exerciseId = input.existingId,
            name = input.name.trim(),
            kind = input.kind,
            isRpt = false,
            restSec = when {
                top <= 6 -> 180
                top <= 10 -> 120
                else -> 90
            },
            sets = List(sets) { SetDraft(input.minText.trim(), input.maxText.trim()) },
            alternativeName = "",
        )
        edit { it.copy(exercises = it.exercises + exercise) }
        _state.update { it.copy(expandedKey = key) }
    }

    fun save() {
        val current = _state.value
        if (current.busy) return
        if (!current.issues.isValid) {
            val firstBad = current.draft.exercises.firstOrNull { it.key in current.issues.exercises || it.key in current.issues.sets }
            _state.update { it.copy(showIssues = true, expandedKey = firstBad?.key ?: it.expandedKey) }
            return
        }
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                repository.save(current.draft)
                _state.update { it.copy(busy = false, finished = true) }
            } catch (e: RoutineRejected) {
                _state.update { it.copy(busy = false, error = EditorError.SAVE_FAILED) }
            }
        }
    }

    fun delete() {
        val id = routineId ?: return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val deleted = repository.delete(id)
            _state.update { if (deleted) it.copy(busy = false, finished = true) else it.copy(busy = false, error = EditorError.DELETE_FAILED) }
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }

    private fun edit(change: (RoutineDraft) -> RoutineDraft) = _state.update { s ->
        val draft = change(s.draft)
        s.copy(draft = draft, issues = RoutineEditing.validate(draft), dirty = draft != original)
    }

    private fun editExercise(key: Long, change: (ExerciseDraft) -> ExerciseDraft) = edit { d ->
        d.copy(exercises = d.exercises.map { if (it.key == key) change(it) else it })
    }

    private fun editSet(key: Long, index: Int, change: (SetDraft) -> SetDraft) = editExercise(key) { ex ->
        if (index !in ex.sets.indices) ex else ex.copy(sets = ex.sets.mapIndexed { i, s -> if (i == index) change(s) else s })
    }

    companion object {
        fun factory(container: AppContainer, routineId: Long?): ViewModelProvider.Factory = viewModelFactory {
            initializer { RoutineEditorViewModel(container.routineRepository, routineId) }
        }
    }
}
