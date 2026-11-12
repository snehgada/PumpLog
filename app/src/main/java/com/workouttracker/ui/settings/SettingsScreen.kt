@file:OptIn(ExperimentalMaterial3Api::class)

package com.workouttracker.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import com.workouttracker.data.settings.SettingsRepository
import com.workouttracker.domain.WeightUnit
import com.workouttracker.timer.RestState
import com.workouttracker.timer.RestTimer
import com.workouttracker.ui.components.SystemSettings
import com.workouttracker.ui.components.rememberAlertReadiness
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settings: SettingsRepository,
    private val restTimer: RestTimer,
) : ViewModel() {
    val unit: StateFlow<WeightUnit> = settings.weightUnit.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WeightUnit.KG)
    val rest: StateFlow<RestState?> = restTimer.state

    fun setUnit(unit: WeightUnit) {
        viewModelScope.launch { settings.setWeightUnit(unit) }
    }

    fun testAlert(label: String) = restTimer.testAlert(label = label)

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { SettingsViewModel(container.settings, container.restTimer) }
        }
    }
}

@Composable
fun SettingsRoute(container: AppContainer, onBack: () -> Unit) {
    val vm: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(container))
    val unit by vm.unit.collectAsStateWithLifecycle()
    val rest by vm.rest.collectAsStateWithLifecycle()
    val readiness = rememberAlertReadiness()
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val testLabel = stringResource(R.string.test_alert_label)
    val testStarted = stringResource(R.string.test_alert_started)

    Scaffold(
        containerColor = colors.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                title = { Text(stringResource(R.string.settings)) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background),
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 4.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Section(stringResource(R.string.units)) {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        WeightUnit.entries.forEachIndexed { index, u ->
                            SegmentedButton(
                                selected = unit == u,
                                onClick = { vm.setUnit(u) },
                                shape = SegmentedButtonDefaults.itemShape(index, WeightUnit.entries.size),
                                colors = SegmentedButtonDefaults.colors(
                                    activeContainerColor = colors.primary,
                                    activeContentColor = colors.onPrimary,
                                    activeBorderColor = colors.primary,
                                ),
                                icon = {},
                            ) { Text(stringResource(if (u == WeightUnit.KG) R.string.unit_kg else R.string.unit_lb)) }
                        }
                    }
                    Text(stringResource(R.string.units_note), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
            }
            item {
                Section(stringResource(R.string.rest_alerts)) {
                    StatusRow(
                        label = stringResource(R.string.status_notifications),
                        ok = readiness.notificationsAllowed,
                        okText = stringResource(R.string.status_on),
                        badText = stringResource(R.string.status_off),
                        action = stringResource(R.string.turn_on),
                        onAction = { SystemSettings.notifications(context) },
                    )
                    StatusRow(
                        label = stringResource(R.string.status_background),
                        ok = readiness.backgroundAllowed,
                        okText = stringResource(R.string.status_allowed),
                        badText = stringResource(R.string.status_may_pause),
                        action = stringResource(R.string.open_settings),
                        onAction = { SystemSettings.appDetails(context) },
                    )
                    StatusRow(
                        label = stringResource(R.string.status_exact),
                        ok = readiness.exactAlarms,
                        okText = stringResource(R.string.status_on),
                        badText = stringResource(R.string.status_off),
                        action = stringResource(R.string.turn_on),
                        onAction = { SystemSettings.exactAlarms(context) },
                    )
                    if (!readiness.backgroundAllowed) {
                        Text(stringResource(R.string.background_help), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    }
                    Button(
                        onClick = {
                            vm.testAlert(testLabel)
                            scope.launch { snackbar.showSnackbar(testStarted) }
                        },
                        enabled = rest == null && readiness.notificationsAllowed,
                        colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = colors.onPrimary),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    ) { Text(stringResource(R.string.test_alert)) }
                    Text(
                        stringResource(if (rest != null) R.string.test_alert_busy else R.string.test_alert_help),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            content()
        }
    }
}

@Composable
private fun StatusRow(label: String, ok: Boolean, okText: String, badText: String, action: String, onAction: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 44.dp)) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            Text(
                (if (ok) "✓ " else "! ") + (if (ok) okText else badText),
                style = MaterialTheme.typography.bodySmall,
                color = if (ok) colors.primary else colors.secondary,
            )
        }
        if (!ok) TextButton(onClick = onAction) { Text(action) }
    }
}
