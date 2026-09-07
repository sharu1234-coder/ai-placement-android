package com.aiplacementos.app.ui.jobhunt

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aiplacementos.app.ui.common.AppCard
import com.aiplacementos.app.ui.common.DetailTopBar
import com.aiplacementos.app.ui.common.PrimaryButton
import com.aiplacementos.app.ui.common.networkModule
import com.aiplacementos.app.ui.theme.Spacing

private val EXPERIENCE_LEVELS = listOf(
    "fresher" to "Fresher (0-1 years)",
    "junior" to "Junior (1-3 years)",
    "mid" to "Mid-level (3-6 years)",
    "senior" to "Senior (6+ years)",
)

private val WORK_MODES = listOf("remote" to "Remote", "hybrid" to "Hybrid", "onsite" to "On-site")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobHuntSetupScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    onOpenResumeUpload: () -> Unit,
) {
    val network = networkModule()
    val viewModel: JobHuntSetupViewModel = viewModel(
        factory = viewModelFactory {
            initializer { JobHuntSetupViewModel(network.jobHuntRepository, network.resumeRepository) }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.savedSuccessfully) {
        if (state.savedSuccessfully) {
            viewModel.onNavigated()
            onSaved()
        }
    }

    Scaffold(topBar = { DetailTopBar(title = "Job Hunt Setup", onBack = onBack) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            ResumeSection(
                resumeName = state.existingResumeName,
                hasResume = state.existingResumeId != null,
                useResume = state.useResume,
                onUseResumeChange = viewModel::onUseResumeChange,
                onOpenResumeUpload = onOpenResumeUpload,
            )

            if (state.roleSuggestions.isNotEmpty()) {
                // Suggestions, never auto-applied: the student confirms one. Read only from
                // real job titles on their resume, so an empty list is a normal outcome for a
                // fresher rather than a failure.
                Text(
                    text = "From your resume — tap to confirm",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.chipGap)) {
                    state.roleSuggestions.take(3).forEach { family ->
                        val label = family.replace('_', ' ').replaceFirstChar { it.uppercase() }
                        SuggestionChip(
                            onClick = { viewModel.onTargetRoleChange(label) },
                            label = { Text(label) },
                        )
                    }
                }
            }

            HorizontalDivider()

            Text(
                text = "Enter details manually",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.semantics { heading() },
            )

            OutlinedTextField(
                value = state.targetRole,
                onValueChange = viewModel::onTargetRoleChange,
                label = { Text("Target role") },
                placeholder = { Text("e.g. Software Engineer") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            ExperienceLevelField(
                selected = state.experienceLevel,
                onSelect = viewModel::onExperienceLevelChange,
            )

            OutlinedTextField(
                value = state.preferredLocation,
                onValueChange = viewModel::onLocationChange,
                label = { Text("Preferred location") },
                placeholder = { Text("e.g. Bengaluru") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Text(
                text = "Work mode",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.chipGap)) {
                WORK_MODES.forEach { (value, label) ->
                    FilterChip(
                        selected = state.workMode == value,
                        // Tapping the selected chip clears it -- "no preference" has to stay
                        // reachable, or an accidental tap would permanently narrow results.
                        onClick = {
                            viewModel.onWorkModeChange(if (state.workMode == value) null else value)
                        },
                        label = { Text(label) },
                    )
                }
            }

            if (state.errorMessage != null) {
                Text(
                    text = state.errorMessage!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            PrimaryButton(
                text = if (state.isSaving) "Saving…" else "Find Matching Jobs",
                onClick = viewModel::submit,
                enabled = state.canSubmit && !state.isSaving,
                modifier = Modifier.fillMaxWidth(),
            )

            if (state.matchingSkills.isNotEmpty()) {
                Column {
                    Text(
                        text = "Skills we're matching with",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    // Read-only here on purpose. Skills are owned by Profile and by resume
                    // extraction; a second editor in Job Hunt would be a second source of
                    // truth for the same list. Showing them still matters -- a student who
                    // cannot see what we are matching on cannot tell a bad result from a bad
                    // input.
                    SkillChipRow(
                        skills = state.matchingSkills.take(6),
                        modifier = Modifier.padding(top = Spacing.xs),
                    )
                    SignalOrigin(state.signalProvenance["resume_skills"])
                    Text(
                        text = "Edit these in Profile, or upload a different resume above.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Spacing.xs),
                    )
                }
            }

            if (!state.canSubmit) {
                // States the actual precondition instead of leaving a disabled button
                // unexplained.
                Text(
                    text = "Add a target role, or use your resume, to start matching.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            PremiumFiltersNote()
        }
    }
}

/**
 * A one-line note under a field saying where its current value came from.
 *
 * Only rendered when the value did *not* come from the student. An "explicit" field needs no
 * caption -- they typed it -- while a value we read off a resume or inferred must say so, and
 * must stay editable. Turning an inference into an unmarked answer is the specific failure
 * this guards: the student would then see our guess as their own stated preference.
 */
fun signalOriginNote(origin: String?): String? = when (origin) {
    "resume" -> "From your resume — edit if it's wrong"
    "evidence" -> "From your assessments and projects — edit if it's wrong"
    "inferred" -> "Our guess — edit if it's wrong"
    // "explicit", null, or anything a newer backend introduces: say nothing rather than
    // captioning a field with a word the student cannot interpret.
    else -> null
}

@Composable
private fun SignalOrigin(origin: String?) {
    val note = signalOriginNote(origin) ?: return
    Text(
        text = note,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = Spacing.xs),
    )
}

@Composable
private fun ResumeSection(
    resumeName: String?,
    hasResume: Boolean,
    useResume: Boolean,
    onUseResumeChange: (Boolean) -> Unit,
    onOpenResumeUpload: () -> Unit,
) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.Description,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Text(
                text = "Use my resume",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = Spacing.sm).semantics { heading() },
            )
        }

        if (hasResume) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
            ) {
                Checkbox(checked = useResume, onCheckedChange = onUseResumeChange)
                Column(modifier = Modifier.padding(start = Spacing.xs)) {
                    Text(
                        text = resumeName ?: "Your uploaded resume",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = "Adds your extracted skills to matching.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            Text(
                text = "No resume on file. You can still match on a role alone.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xs),
            )
            PrimaryButton(
                text = "Upload Resume",
                onClick = onOpenResumeUpload,
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExperienceLevelField(selected: String?, onSelect: (String?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val label = EXPERIENCE_LEVELS.firstOrNull { it.first == selected }?.second ?: ""

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = label,
            onValueChange = {},
            readOnly = true,
            label = { Text("Experience level") },
            placeholder = { Text("Select your level") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            EXPERIENCE_LEVELS.forEach { (value, text) ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = {
                        onSelect(value)
                        expanded = false
                    },
                )
            }
        }
    }
}

/**
 * States the paywall boundary up front rather than letting a student discover it by hitting a
 * 403 mid-search. Says exactly what is free, because the free tier here is genuinely useful
 * and hiding that would undersell it.
 */
@Composable
private fun PremiumFiltersNote() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Filled.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = "Role, location and work mode are free. Salary, employment type and " +
                "posted-date filters are Premium.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = Spacing.xs),
        )
    }
}

/** Kept for the confirmation row on the results screen. */
@Composable
fun ReadyIndicator(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = Spacing.xs),
        )
    }
}
