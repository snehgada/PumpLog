@file:OptIn(ExperimentalMaterial3Api::class)

package com.workouttracker.ui.progress

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.workouttracker.AppContainer
import com.workouttracker.R
import com.workouttracker.domain.ExerciseProgress
import com.workouttracker.domain.ExerciseSession
import com.workouttracker.domain.LiveSessionRules
import com.workouttracker.domain.ProgressPoint
import com.workouttracker.domain.SetType
import com.workouttracker.domain.WeightUnit
import com.workouttracker.domain.Weights
import java.time.format.DateTimeFormatter
import java.util.Locale

// ---------------- Exercise list ----------------

@Composable
fun ProgressRoute(container: AppContainer, onOpenExercise: (Long) -> Unit) {
    val viewModel: ProgressListViewModel = viewModel(factory = ProgressListViewModel.factory(container))
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = MaterialTheme.colorScheme
    val dateFormat = remember { DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()) }
    Scaffold(containerColor = colors.background) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 20.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Column(modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)) {
                    Text(stringResource(R.string.progress_title), style = MaterialTheme.typography.headlineMedium)
                    Text(stringResource(R.string.progress_subtitle), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                }
            }
            if (!state.loading && state.exercises.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.progress_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }
            items(state.exercises, key = { it.id }) { row ->
                Surface(
                    onClick = { onOpenExercise(row.id) },
                    shape = RoundedCornerShape(18.dp),
                    color = colors.surfaceContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text(row.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(
                                R.string.progress_row_meta,
                                androidx.compose.ui.platform.LocalContext.current.resources.getQuantityString(R.plurals.workout_count, row.sessions, row.sessions),
                                row.lastDate.format(dateFormat),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

// ---------------- One exercise ----------------

@Composable
fun ExerciseProgressRoute(container: AppContainer, exerciseId: Long, onBack: () -> Unit) {
    val viewModel: ExerciseProgressViewModel = viewModel(
        key = "progress-$exerciseId",
        factory = ExerciseProgressViewModel.factory(container, exerciseId),
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = MaterialTheme.colorScheme
    val unitLabel = stringResource(if (state.unit == WeightUnit.KG) R.string.unit_kg else R.string.unit_lb)
    val points = state.summary.points
    val dateFormat = remember { DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()) }

    Scaffold(
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                title = {
                    Column {
                        Text(state.name, style = MaterialTheme.typography.titleLarge)
                        Text(
                            androidx.compose.ui.platform.LocalContext.current.resources.getQuantityString(R.plurals.workout_count, points.size, points.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background),
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    ProgressMetric.entries.forEachIndexed { index, metric ->
                        SegmentedButton(
                            selected = state.metric == metric,
                            onClick = { viewModel.setMetric(metric) },
                            shape = SegmentedButtonDefaults.itemShape(index, ProgressMetric.entries.size),
                            colors = SegmentedButtonDefaults.colors(
                                activeContainerColor = colors.primary,
                                activeContentColor = colors.onPrimary,
                                activeBorderColor = colors.primary,
                            ),
                            icon = {},
                        ) { Text(stringResource(metricLabel(metric))) }
                    }
                }
            }
            if (points.isNotEmpty()) {
                val values = points.map { metricValue(it, state.metric) }
                val latest = values.last()
                val delta = latest - values.first()
                item {
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(start = 4.dp)) {
                        Text(formatMetric(latest, state.metric, state.unit, unitLabel), style = MaterialTheme.typography.displaySmall)
                        if (points.size > 1) {
                            Text(
                                stringResource(R.string.since_first, (if (delta >= 0) "+" else "−") + formatMetric(kotlin.math.abs(delta), state.metric, state.unit, unitLabel)),
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (delta >= 0) colors.primary else colors.secondary,
                                modifier = Modifier.padding(bottom = 6.dp),
                            )
                        }
                    }
                }
                item { LineChart(values.takeLast(MAX_POINTS), contentDesc = stringResource(R.string.chart_description, metricLabelText(state.metric))) }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Tile(
                            stringResource(R.string.best_set),
                            state.summary.bestSet?.let { "${it.reps} × ${Weights.format(it.weightGrams, state.unit)}" } ?: "—",
                            Modifier.weight(1f),
                        )
                        Tile(
                            stringResource(R.string.best_volume),
                            state.summary.bestVolume?.let { ExerciseProgress.formatVolume(it.volumeGramReps, state.unit) } ?: "—",
                            Modifier.weight(1f),
                        )
                        Tile(
                            stringResource(R.string.pr_set_on),
                            state.summary.bestSet?.date?.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())) ?: "—",
                            Modifier.weight(1f),
                            highlight = true,
                        )
                    }
                }
            }
            item {
                Text(
                    stringResource(R.string.history_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp),
                )
            }
            items(state.sessions, key = { it.sessionId }) { session ->
                SessionCard(session, state.unit, dateFormat, isPr = session.sessionId == state.summary.bestSet?.sessionId)
            }
        }
    }
}

@Composable
private fun Tile(label: String, value: String, modifier: Modifier, highlight: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (highlight) colors.secondaryContainer else colors.surfaceContainer,
        modifier = modifier,
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = if (highlight) colors.secondary else colors.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium, color = if (highlight) colors.onSecondaryContainer else colors.onSurface, maxLines = 1)
        }
    }
}

@Composable
private fun SessionCard(session: ExerciseSession, unit: WeightUnit, dateFormat: DateTimeFormatter, isPr: Boolean) {
    val colors = MaterialTheme.colorScheme
    val working = session.sets.filter { it.type != SetType.WARMUP }
    val volume = working.sumOf { s -> s.segments.sumOf { it.reps.toLong() * it.weightGrams } }
    Surface(shape = RoundedCornerShape(16.dp), color = colors.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row {
                Text(session.date.format(dateFormat), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(ExerciseProgress.formatVolume(volume, unit), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                session.sets.sortedBy { it.setIndex }.forEach { set ->
                    Text(
                        LiveSessionRules.lastTimeText(set, unit),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (set.type == SetType.WARMUP) colors.outline else colors.onSurface,
                        maxLines = 1,
                        modifier = Modifier
                            .weight(1f)
                            .background(colors.surfaceContainerHigh, RoundedCornerShape(8.dp))
                            .padding(vertical = 6.dp, horizontal = 4.dp),
                    )
                }
            }
            if (isPr) Text(stringResource(R.string.pr_badge), style = MaterialTheme.typography.labelSmall, color = colors.secondary)
        }
    }
}

@Composable
private fun LineChart(values: List<Long>, contentDesc: String) {
    val colors = MaterialTheme.colorScheme
    val line = colors.primary
    val grid = colors.outlineVariant
    val dot = colors.background
    Surface(shape = RoundedCornerShape(18.dp), color = colors.surfaceContainer) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .padding(16.dp)
                .semantics { contentDescription = contentDesc },
        ) {
            if (values.isEmpty()) return@Canvas
            val min = values.min().toFloat()
            val max = values.max().toFloat()
            val span = (max - min).takeIf { it > 0f } ?: 1f
            fun x(i: Int) = if (values.size == 1) size.width / 2 else size.width * i / (values.size - 1)
            fun y(v: Long) = size.height - (v - min) / span * size.height * 0.85f - size.height * 0.075f
            repeat(3) { i ->
                val gy = size.height * (i + 1) / 4f
                drawLine(grid, Offset(0f, gy), Offset(size.width, gy), strokeWidth = 1.dp.toPx())
            }
            val path = Path()
            values.forEachIndexed { i, v -> if (i == 0) path.moveTo(x(i), y(v)) else path.lineTo(x(i), y(v)) }
            drawPath(path, line, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            values.forEachIndexed { i, v ->
                val isLast = i == values.lastIndex
                drawCircle(line, radius = if (isLast) 7.dp.toPx() else 5.dp.toPx(), center = Offset(x(i), y(v)))
                if (!isLast) drawCircle(dot, radius = 2.5.dp.toPx(), center = Offset(x(i), y(v)))
            }
        }
    }
}

private const val MAX_POINTS = 20

private fun metricValue(point: ProgressPoint, metric: ProgressMetric): Long = when (metric) {
    ProgressMetric.E1RM -> point.e1rmGrams.toLong()
    ProgressMetric.TOP_SET -> point.topSetGrams.toLong()
    ProgressMetric.VOLUME -> point.volumeGramReps
}

private fun formatMetric(value: Long, metric: ProgressMetric, unit: WeightUnit, unitLabel: String): String = when (metric) {
    ProgressMetric.VOLUME -> ExerciseProgress.formatVolume(value, unit)
    else -> "${Weights.format(value.toInt(), unit)} $unitLabel"
}

private fun metricLabel(metric: ProgressMetric): Int = when (metric) {
    ProgressMetric.E1RM -> R.string.metric_e1rm
    ProgressMetric.TOP_SET -> R.string.metric_top_set
    ProgressMetric.VOLUME -> R.string.metric_volume
}

@Composable
private fun metricLabelText(metric: ProgressMetric): String = stringResource(metricLabel(metric))
