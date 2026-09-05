package com.aiplacementos.app.ui.alarm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aiplacementos.app.database.entity.AlarmType
import com.aiplacementos.app.ui.common.ErrorState
import com.aiplacementos.app.ui.common.LoadingState
import com.aiplacementos.app.ui.common.PrimaryButton
import com.aiplacementos.app.ui.common.SecondaryButton
import com.aiplacementos.app.ui.common.networkModule
import com.aiplacementos.app.ui.theme.Spacing
import java.util.Calendar

// Single-letter labels only, except Tuesday/Thursday -- "T"/"T" alone would make the two
// FilterChips visually indistinguishable, letting a student silently pick the wrong day with
// no way to tell them apart. "Tu"/"Th" are still compact enough for a chip. Sunday/Saturday
// are still both visually "S" (a deliberately compact label, unlike Tu/Th which needed two
// letters to read unambiguously at a glance) -- the third element is the full day name, used
// only as each chip's accessible contentDescription so TalkBack still disambiguates them.
private val DAYS = listOf(
    Triple(Calendar.SUNDAY, "S", "Sunday"), Triple(Calendar.MONDAY, "M", "Monday"), Triple(Calendar.TUESDAY, "Tu", "Tuesday"),
    Triple(Calendar.WEDNESDAY, "W", "Wednesday"), Triple(Calendar.THURSDAY, "Th", "Thursday"), Triple(Calendar.FRIDAY, "F", "Friday"),
    Triple(Calendar.SATURDAY, "S", "Saturday"),
)

/**
 * Minimal by design, per the fixed product requirement: only the alarm's schedule (time +
 * repeat) is ever configured here. There is deliberately no topic, skill, question-count, or
 * difficulty picker anywhere on this screen -- eligible topics come entirely from the
 * student's resume, checked once via EligibilityState below, never chosen manually.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmCreateScreen(
    onSaved: () -> Unit,
    onGoToResume: () -> Unit,
    alarmId: Long = 0L,
) {
    val context = LocalContext.current
    val network = networkModule()
    val viewModel: AlarmCreateViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                AlarmCreateViewModel(network.placementAlarmRepository, network.alarmScheduler, alarmId)
            }
        },
    )
    val existingAlarm by viewModel.existingAlarm.collectAsStateWithLifecycle()
    val loadingExisting by viewModel.loadingExisting.collectAsStateWithLifecycle()
    val eligibility by viewModel.eligibility.collectAsStateWithLifecycle()
    val saved by viewModel.saved.collectAsStateWithLifecycle()

    LaunchedEffect(saved) { if (saved) onSaved() }

    if (loadingExisting) {
        LoadingState(modifier = Modifier.fillMaxSize())
        return
    }

    when (val current = eligibility) {
        is EligibilityState.Checking -> LoadingState(modifier = Modifier.fillMaxSize())
        is EligibilityState.Error -> ErrorState(
            message = current.message,
            modifier = Modifier.fillMaxSize(),
            onRetry = viewModel::checkEligibility,
        )
        is EligibilityState.NotEligible -> Column(
            modifier = Modifier.fillMaxSize().padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(
                text = "Complete your resume to enable Placement Alarm",
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = "Your alarm's questions come from the skills on your resume -- upload " +
                    "one first, then come back here.",
                style = MaterialTheme.typography.bodyMedium,
            )
            PrimaryButton(text = "Go to resume upload", onClick = onGoToResume, modifier = Modifier.fillMaxWidth())
        }
        is EligibilityState.Eligible -> {
            val timePickerState = rememberTimePickerState(
                initialHour = existingAlarm?.hour ?: 7,
                initialMinute = existingAlarm?.minute ?: 0,
                is24Hour = false,
            )
            var selectedDays by remember {
                mutableStateOf(
                    existingAlarm?.repeatDays.orEmpty().split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()
                )
            }
            var title by remember { mutableStateOf(existingAlarm?.title.orEmpty()) }
            var type by remember { mutableStateOf(existingAlarm?.type ?: AlarmType.CUSTOM) }
            var notes by remember { mutableStateOf(existingAlarm?.notes.orEmpty()) }
            val exactAlarmsAllowed = network.alarmScheduler.canScheduleExactAlarms()

            Column(
                modifier = Modifier.fillMaxSize().padding(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Text(
                    text = if (viewModel.isEditing) "Edit Placement Alarm" else "Placement Alarm",
                    style = MaterialTheme.typography.headlineSmall,
                )

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Text(text = "Type", style = MaterialTheme.typography.labelLarge)
                // chipGap (not sm) between these -- see Spacing.chipGap's own doc comment.
                LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.chipGap)) {
                    items(AlarmType.ALL) { candidate ->
                        FilterChip(
                            selected = type == candidate,
                            onClick = { type = candidate },
                            label = { Text(AlarmType.label(candidate)) },
                        )
                    }
                }

                Text(text = "Time", style = MaterialTheme.typography.labelLarge)
                TimeInput(state = timePickerState)

                Text(text = "Repeat", style = MaterialTheme.typography.labelLarge)
                // chipGap (not sm) between these -- see Spacing.chipGap's own doc comment.
                LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.chipGap)) {
                    items(DAYS) { (day, label, fullName) ->
                        FilterChip(
                            selected = day in selectedDays,
                            onClick = {
                                selectedDays = if (day in selectedDays) selectedDays - day else selectedDays + day
                            },
                            label = { Text(label) },
                            modifier = Modifier.semantics { contentDescription = fullName },
                        )
                    }
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (optional)") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                )

                Text(
                    text = "Your placement skills will be used automatically to generate your challenge.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (!exactAlarmsAllowed) {
                    Text(
                        text = "This app needs permission to schedule exact alarms so your " +
                            "Placement Alarm rings on time.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    SecondaryButton(
                        text = "Grant alarm permission",
                        onClick = { context.startActivity(network.alarmScheduler.requestExactAlarmPermissionIntent()) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Row(modifier = Modifier.fillMaxWidth()) {
                    PrimaryButton(
                        text = if (viewModel.isEditing) "Save Changes" else "Save Alarm",
                        onClick = {
                            viewModel.save(timePickerState.hour, timePickerState.minute, selectedDays, title, type, notes)
                        },
                        enabled = exactAlarmsAllowed,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
