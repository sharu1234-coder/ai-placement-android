package com.aiplacementos.app.ui.jobhunt

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aiplacementos.app.data.remote.JobAlertDto
import com.aiplacementos.app.ui.common.AppCard
import com.aiplacementos.app.ui.common.DetailTopBar
import com.aiplacementos.app.ui.common.EmptyState
import com.aiplacementos.app.ui.common.PrimaryButton
import com.aiplacementos.app.ui.common.SkeletonList
import com.aiplacementos.app.ui.common.networkModule
import com.aiplacementos.app.ui.theme.Spacing

/**
 * Job alerts — "never miss an opportunity", without becoming the thing people mute.
 *
 * Three deliberate choices are visible on this screen. Each alert has its own switch and no
 * global one exists, because consenting to one standing search is not consenting to all of
 * them. A never-run alert says nothing at all rather than "0 new jobs", which would read as a
 * verdict on the job market. And a failed run says "we couldn't check" rather than staying
 * silent — an alert that has gone quiet because a provider is down looks identical to an empty
 * market unless you say so.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobAlertsScreen(
    onBack: () -> Unit,
    onOpenSubscriptions: () -> Unit,
    onOpenJob: (Int) -> Unit,
) {
    val network = networkModule()
    val viewModel: JobAlertsViewModel = viewModel(
        factory = viewModelFactory { initializer { JobAlertsViewModel(network.jobHuntRepository) } },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showCreate by rememberSaveable { mutableStateOf(false) }

    val success = state as? JobAlertsUiState.Success
    LaunchedEffect(success?.actionError) {
        val message = success?.actionError
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.clearMessages()
        }
    }

    Scaffold(
        topBar = { DetailTopBar(title = "Job Alerts", onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (showCreate) {
            CreateAlertDialog(
                onDismiss = { showCreate = false },
                onCreate = { name, frequency, dayOfWeek, hour, quietStart, quietEnd ->
                    viewModel.create(
                        name = name,
                        frequency = frequency,
                        dayOfWeek = dayOfWeek,
                        hourLocal = hour,
                        quietHoursStart = quietStart,
                        quietHoursEnd = quietEnd,
                        // On at creation, because the student explicitly filled in a form
                        // asking to be told about something. The switch on the row is how
                        // they change their mind.
                        isEnabled = true,
                    )
                    showCreate = false
                },
            )
        }

        when (val current = state) {
            is JobAlertsUiState.Loading ->
                SkeletonList(
                    count = 3,
                    modifier = Modifier.fillMaxSize().padding(padding).padding(Spacing.md),
                )

            is JobAlertsUiState.Failed -> FailureState(
                failure = current.failure,
                onRetry = viewModel::retry,
                onOpenSetup = viewModel::retry,
                onOpenSubscriptions = onOpenSubscriptions,
                onBack = onBack,
            )

            is JobAlertsUiState.Success -> AlertsContent(
                state = current,
                onToggle = viewModel::setEnabled,
                onDelete = viewModel::delete,
                onCreate = { showCreate = true },
                onOpenSubscriptions = onOpenSubscriptions,
                onOpenJob = onOpenJob,
                modifier = Modifier.fillMaxSize().padding(padding),
            )
        }
    }
}

@Composable
private fun AlertsContent(
    state: JobAlertsUiState.Success,
    onToggle: (Int, Boolean) -> Unit,
    onDelete: (Int) -> Unit,
    onCreate: () -> Unit,
    onOpenSubscriptions: () -> Unit,
    onOpenJob: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        if (state.isEmpty) {
            item {
                EmptyState(
                    title = "No job alerts yet",
                    message = "Set one up and we'll tell you when new jobs match it. " +
                        "You choose how often, and when not to be disturbed.",
                    icon = Icons.Filled.NotificationsNone,
                    actionLabel = "Create alert",
                    onAction = onCreate,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
            item {
                Text(
                    text = "Active alerts",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.semantics { heading() },
                )
            }
            items(state.alerts, key = { it.id }) { alert ->
                AlertRow(
                    alert = alert,
                    isBusy = alert.id in state.busyIds,
                    onToggle = { enabled -> onToggle(alert.id, enabled) },
                    onDelete = { onDelete(alert.id) },
                )
            }
        }

        item {
            if (state.canCreate) {
                PrimaryButton(
                    text = "Create New Alert",
                    onClick = onCreate,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                // The plan's limit, stated. A disabled button with no explanation is the
                // version of this that makes a student think the app is broken.
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "You're using all ${state.quota.limit} of your alerts",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = state.upgradeMessage
                            ?: "Premium raises the limit, checks more often, and adds advanced filters.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Spacing.xs),
                    )
                    PrimaryButton(
                        text = "See Premium",
                        onClick = onOpenSubscriptions,
                        modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
                    )
                }
            }
        }

        if (state.history.isNotEmpty()) {
            item {
                Text(
                    text = "Alert history",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = Spacing.sm).semantics { heading() },
                )
            }
            items(state.history, key = { it.id }) { entry ->
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = entry.listing.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    val subtitle = listOfNotNull(entry.listing.company, entry.alertName)
                        .joinToString(" · ")
                    if (subtitle.isNotBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { onOpenJob(entry.listing.id) }) { Text("View job") }
                }
            }
        }
    }
}

@Composable
private fun AlertRow(
    alert: JobAlertDto,
    isBusy: Boolean,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = alert.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = alertScheduleLabel(alert),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                quietHoursLabel(alert)?.let { quiet ->
                    Text(
                        text = quiet,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // Only when it has actually run. Never "0 new jobs" for an alert that has
                // never looked.
                alertLastRunLabel(alert)?.let { last ->
                    Text(
                        text = last,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (alert.lastRunStatus == "provider_error") {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(top = Spacing.xs),
                    )
                }
            }
            Switch(
                checked = alert.isEnabled,
                onCheckedChange = onToggle,
                enabled = !isBusy,
                modifier = Modifier.semantics {
                    contentDescription = "Notifications for ${alert.name}"
                },
            )
            IconButton(onClick = onDelete, enabled = !isBusy) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete alert ${alert.name}")
            }
        }
    }
}

/**
 * The create form.
 *
 * Quiet hours are optional and must be set as a pair — half a window is not a window, and
 * guessing the other half would invent a quiet period the student never asked for. The server
 * enforces the same rule; this form simply does not offer a way to break it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateAlertDialog(
    onDismiss: () -> Unit,
    onCreate: (String, String, Int?, Int, Int?, Int?) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var frequency by rememberSaveable { mutableStateOf("daily") }
    var dayOfWeek by rememberSaveable { mutableIntStateOf(0) }
    var hourText by rememberSaveable { mutableStateOf("9") }
    var quietEnabled by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New job alert") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(120) },
                    label = { Text("Name") },
                    placeholder = { Text("e.g. Remote data roles") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("How often", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.chipGap)) {
                    listOf("daily" to "Daily", "weekly" to "Weekly").forEach { (value, label) ->
                        FilterChip(
                            selected = frequency == value,
                            onClick = { frequency = value },
                            label = { Text(label) },
                        )
                    }
                }
                if (frequency == "weekly") {
                    Text("Which day", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.chipGap)) {
                        listOf(0 to "Mon", 2 to "Wed", 4 to "Fri").forEach { (value, label) ->
                            FilterChip(
                                selected = dayOfWeek == value,
                                onClick = { dayOfWeek = value },
                                label = { Text(label) },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = hourText,
                    onValueChange = { input -> hourText = input.filter { it.isDigit() }.take(2) },
                    label = { Text("Hour of day (0–23)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = quietEnabled, onCheckedChange = { quietEnabled = it })
                    Text(
                        text = "Don't disturb me 10 PM – 7 AM",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = Spacing.sm),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val hour = hourText.toIntOrNull()?.coerceIn(0, 23) ?: 9
                    onCreate(
                        name,
                        frequency,
                        if (frequency == "weekly") dayOfWeek else null,
                        hour,
                        if (quietEnabled) 22 else null,
                        if (quietEnabled) 7 else null,
                    )
                },
                enabled = name.isNotBlank(),
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
