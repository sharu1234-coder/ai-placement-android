package com.aiplacementos.app.ui.alarm

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aiplacementos.app.alarm.AlarmScheduler
import com.aiplacementos.app.database.entity.AlarmConfigEntity
import com.aiplacementos.app.database.entity.AlarmType
import com.aiplacementos.app.ui.common.AppCard
import com.aiplacementos.app.ui.common.AppFab
import com.aiplacementos.app.ui.common.EmptyState
import com.aiplacementos.app.ui.common.networkModule
import com.aiplacementos.app.ui.theme.Spacing
import java.util.Calendar
import java.util.Locale

private enum class AlarmGroup(val label: String) { TODAY("Today"), TOMORROW("Tomorrow"), THIS_WEEK("This Week") }

private fun groupFor(alarm: AlarmConfigEntity, now: Calendar = Calendar.getInstance()): AlarmGroup {
    val trigger = AlarmScheduler.nextTriggerMillis(alarm.hour, alarm.minute, alarm.repeatDays, now)
    val today = (now.clone() as Calendar).apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    val daysAhead = ((trigger - today.timeInMillis) / (24 * 60 * 60 * 1000L)).toInt()
    return when {
        daysAhead <= 0 -> AlarmGroup.TODAY
        daysAhead == 1 -> AlarmGroup.TOMORROW
        else -> AlarmGroup.THIS_WEEK
    }
}

private val DAY_LABELS = mapOf(
    Calendar.SUNDAY to "Sun", Calendar.MONDAY to "Mon", Calendar.TUESDAY to "Tue",
    Calendar.WEDNESDAY to "Wed", Calendar.THURSDAY to "Thu", Calendar.FRIDAY to "Fri",
    Calendar.SATURDAY to "Sat",
)

private fun formatRepeat(repeatDays: String): String {
    val days = repeatDays.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()
    return when {
        days.isEmpty() -> "Once"
        days.size == 7 -> "Every day"
        else -> days.sorted().mapNotNull { DAY_LABELS[it] }.joinToString(", ")
    }
}

private fun formatTime(hour: Int, minute: Int): String {
    val period = if (hour < 12) "AM" else "PM"
    val displayHour = when (hour % 12) { 0 -> 12; else -> hour % 12 }
    return String.format(Locale.getDefault(), "%d:%02d %s", displayHour, minute, period)
}

@Composable
fun AlarmListScreen(
    onCreateAlarm: () -> Unit,
    onEditAlarm: (alarmId: Long) -> Unit,
    onOpenActiveChallenge: (clientAlarmId: String, alarmConfigId: Long) -> Unit,
) {
    val network = networkModule()
    val viewModel: AlarmListViewModel = viewModel(
        factory = viewModelFactory {
            initializer { AlarmListViewModel(network.placementAlarmRepository, network.alarmScheduler) }
        },
    )
    val alarms by viewModel.alarms.collectAsStateWithLifecycle()
    val activeChallenges by viewModel.activeChallenges.collectAsStateWithLifecycle()

    Scaffold(
        floatingActionButton = { AppFab(onClick = onCreateAlarm, contentDescription = "Add placement alarm") },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Column(modifier = Modifier.fillMaxSize().padding(Spacing.md)) {
                Text(text = "Placement Alarms", style = MaterialTheme.typography.headlineSmall)

                if (alarms.isEmpty()) {
                    EmptyState(
                        message = "No alarms yet. Create your first placement reminder.",
                        actionLabel = "Add Alarm",
                        onAction = onCreateAlarm,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    val grouped = remember(alarms) { alarms.groupBy(::groupFor) }
                    LazyColumn(
                        modifier = Modifier.weight(1f).padding(top = Spacing.md),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        AlarmGroup.entries.forEach { group ->
                            val groupAlarms = grouped[group].orEmpty()
                            if (groupAlarms.isNotEmpty()) {
                                item(key = "header-${group.name}") {
                                    Text(
                                        text = group.label,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = Spacing.sm, bottom = Spacing.xs),
                                    )
                                }
                                items(groupAlarms, key = { it.id }) { alarm ->
                                    AlarmRow(
                                        alarm = alarm,
                                        activeClientAlarmId = activeChallenges[alarm.id]?.clientAlarmId,
                                        onClick = { onEditAlarm(alarm.id) },
                                        onToggle = { enabled -> viewModel.setEnabled(alarm, enabled) },
                                        onDelete = { viewModel.deleteAlarm(alarm) },
                                        onContinueChallenge = { clientAlarmId ->
                                            onOpenActiveChallenge(clientAlarmId, alarm.id)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AlarmRow(
    alarm: AlarmConfigEntity,
    activeClientAlarmId: String?,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onContinueChallenge: (String) -> Unit,
) {
    AppCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = alarm.title?.takeIf { it.isNotBlank() } ?: AlarmType.label(alarm.type),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(text = formatTime(alarm.hour, alarm.minute), style = MaterialTheme.typography.bodyMedium)
                Text(text = formatRepeat(alarm.repeatDays), style = MaterialTheme.typography.bodySmall)
                Text(
                    text = "6 questions · Based on your resume skills",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = alarm.enabled,
                onCheckedChange = onToggle,
                modifier = Modifier.semantics {
                    contentDescription = "${alarm.title?.takeIf { it.isNotBlank() } ?: AlarmType.label(alarm.type)} alarm"
                },
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Spacing.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(onClick = onDelete) { Text("Delete") }
            if (activeClientAlarmId != null) {
                TextButton(onClick = { onContinueChallenge(activeClientAlarmId) }) { Text("Continue challenge") }
            }
        }
    }
}
