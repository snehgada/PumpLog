package com.workouttracker.timer

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Clock
import java.time.Instant

data class RestState(val endsAt: Instant, val exerciseName: String)

/**
 * The one rest countdown in the app. The screen reads [state]; [RestTimerService] keeps it alive and
 * alerts at the end while the phone is locked.
 */
class RestTimer(private val context: Context, private val clock: Clock) {

    private val _state = MutableStateFlow<RestState?>(null)
    val state: StateFlow<RestState?> = _state.asStateFlow()

    fun start(seconds: Int, exerciseName: String) {
        val clamped = seconds.coerceIn(1, MAX_REST_SEC)
        val next = RestState(clock.instant().plusSeconds(clamped.toLong()), exerciseName)
        _state.value = next
        RestTimerService.start(context, next)
    }

    /** Adds (or with a negative value removes) time; never ends up in the past or beyond the cap. */
    fun add(seconds: Int) {
        val current = _state.value ?: return
        val now = clock.instant()
        val ends = current.endsAt.plusSeconds(seconds.toLong())
            .coerceAtLeast(now.plusSeconds(1))
            .coerceAtMost(now.plusSeconds(MAX_REST_SEC.toLong()))
        val next = current.copy(endsAt = ends)
        _state.value = next
        RestTimerService.start(context, next)
    }

    fun skip() {
        if (_state.value == null) return
        _state.value = null
        RestTimerService.stop(context)
    }

    /** Called by the service when the countdown it was running reaches zero. */
    internal fun onFinished(endsAt: Instant) {
        if (_state.value?.endsAt == endsAt) _state.value = null
    }

    companion object {
        /** 15 minutes: enough for any rest, and bounds how long the service may hold a wake lock. */
        const val MAX_REST_SEC = 15 * 60
    }
}
