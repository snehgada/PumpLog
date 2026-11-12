package com.workouttracker.timer

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Clock
import java.time.Instant

data class RestState(val endsAt: Instant, val exerciseName: String)

/**
 * The one rest countdown in the app. The screen reads [state].
 * - [RestAlarm] fires the "Rest's up" alert through Android's alarm system, so it works even if the phone
 *   freezes the app in the background.
 * - [RestTimerService] shows the countdown notification while resting.
 */
class RestTimer(private val context: Context, private val clock: Clock) {

    private val _state = MutableStateFlow<RestState?>(null)
    val state: StateFlow<RestState?> = _state.asStateFlow()

    fun start(seconds: Int, exerciseName: String) {
        val clamped = seconds.coerceIn(1, MAX_REST_SEC)
        set(RestState(clock.instant().plusSeconds(clamped.toLong()), exerciseName))
    }

    /** Adds (or with a negative value removes) time; never ends up in the past or beyond the cap. */
    fun add(seconds: Int) {
        val current = _state.value ?: return
        val now = clock.instant()
        val ends = current.endsAt.plusSeconds(seconds.toLong())
            .coerceAtLeast(now.plusSeconds(1))
            .coerceAtMost(now.plusSeconds(MAX_REST_SEC.toLong()))
        set(current.copy(endsAt = ends))
    }

    /** If the rest in progress leads into [from], make it lead into [to] instead (after a swap). */
    fun renameNext(from: String, to: String) {
        val current = _state.value ?: return
        if (current.exerciseName == from && from != to) set(current.copy(exerciseName = to))
    }

    fun skip() {
        if (_state.value == null) return
        _state.value = null
        RestAlarm.cancel(context)
        RestTimerService.stop(context)
    }

    /**
     * Fires a "Rest's up" alert in [seconds] through the same alarm path as a real rest, without touching the
     * workout. Lets you check the alert reaches a locked phone.
     */
    fun testAlert(seconds: Int = TEST_SEC, label: String) {
        if (_state.value != null) return // never replace a real rest in progress
        RestAlarm.schedule(context, clock.millis() + seconds * 1000L, label)
    }

    /** Called when the alarm fires or the countdown service ends. */
    internal fun onFinished(endsAt: Instant) {
        if (_state.value?.endsAt == endsAt) _state.value = null
    }

    private fun set(next: RestState) {
        _state.value = next
        RestAlarm.schedule(context, next.endsAt.toEpochMilli(), next.exerciseName)
        RestTimerService.start(context, next)
    }

    companion object {
        /** 15 minutes: enough for any rest, and bounds how long the service may hold a wake lock. */
        const val MAX_REST_SEC = 15 * 60
        const val TEST_SEC = 10
    }
}
