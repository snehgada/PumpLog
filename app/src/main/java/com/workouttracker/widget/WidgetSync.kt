package com.workouttracker.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import com.workouttracker.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Redraws the home-screen widget whenever something it shows changes: a workout starts, finishes or is
 * skipped, a set is logged, routines change, or a rest starts or ends. Runs while the app process is alive,
 * which is whenever any of those can happen.
 */
object WidgetSync {

    @OptIn(FlowPreview::class)
    fun start(context: Context, container: AppContainer, scope: CoroutineScope) {
        val appContext = context.applicationContext
        scope.launch {
            combine(
                container.sessionRepository.observeInProgress(),
                container.workoutRepository.observeLatestRotationEntry(),
                container.workoutRepository.observeRoutineSummaries(),
                container.workoutRepository.observeTotalSets(),
                container.restTimer.state,
            ) { open, latest, routines, sets, rest -> listOf(open, latest, routines, sets, rest) }
                .distinctUntilChanged()
                .debounce(DEBOUNCE_MS)
                .collect { PumpLogWidget().updateAll(appContext) }
        }
    }

    private const val DEBOUNCE_MS = 500L
}
