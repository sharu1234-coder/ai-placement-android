package com.aiplacementos.app.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aiplacementos.app.data.remote.PresenceAuditDto
import com.aiplacementos.app.data.remote.ProfileDto
import com.aiplacementos.app.data.remote.ResumeSummaryDto
import com.aiplacementos.app.data.remote.RoleDto
import com.aiplacementos.app.data.remote.SkillDto
import com.aiplacementos.app.ui.common.AppCard
import com.aiplacementos.app.ui.common.ConfirmationDialog
import com.aiplacementos.app.ui.common.ErrorState
import com.aiplacementos.app.ui.common.LoadingState
import com.aiplacementos.app.ui.common.PrimaryButton
import com.aiplacementos.app.ui.common.SecondaryButton
import com.aiplacementos.app.ui.common.SkeletonCard
import com.aiplacementos.app.ui.common.SkeletonText
import com.aiplacementos.app.ui.common.SkillPicker
import com.aiplacementos.app.ui.common.UiState
import com.aiplacementos.app.ui.common.networkModule
import com.aiplacementos.app.ui.onboarding.GOALS
import com.aiplacementos.app.ui.onboarding.LEVELS
import com.aiplacementos.app.ui.presence.ProfessionalPresenceViewModel
import com.aiplacementos.app.ui.theme.Spacing

@Composable
fun ProfileScreen(
    onSelectRole: () -> Unit,
    onSelectSecondaryRole: () -> Unit,
    onReplaceResume: () -> Unit,
    onOpenProfessionalPresence: () -> Unit = {},
    onConnectGitHub: () -> Unit = {},
) {
    val network = networkModule()
    val viewModel: ProfileViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                ProfileViewModel(
                    network.profileRepository,
                    network.resumeRepository,
                    network.professionalPresenceRepository,
                )
            }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val skills by viewModel.skills.collectAsStateWithLifecycle()
    val resume by viewModel.resume.collectAsStateWithLifecycle()
    val roles by viewModel.roles.collectAsStateWithLifecycle()
    val savingSection by viewModel.savingSection.collectAsStateWithLifecycle()
    val saveError by viewModel.saveError.collectAsStateWithLifecycle()
    val presence by viewModel.presence.collectAsStateWithLifecycle()

    // Resume may have just been replaced on the upload screen -- re-check every time this
    // screen resumes rather than only on first composition.
    LaunchedEffect(Unit) {
        viewModel.reloadResume()
        viewModel.reloadPresence()
    }

    when (val current = state) {
        is UiState.Loading -> ProfileLoadingSkeleton()
        is UiState.Empty -> LoadingState(modifier = Modifier.fillMaxSize())
        is UiState.Error -> ErrorState(message = current.message, onRetry = viewModel::load, modifier = Modifier.fillMaxSize())
        is UiState.Success<ProfileDto> -> ProfileContent(
            profile = current.data,
            skills = skills,
            resume = resume,
            roles = roles,
            savingSection = savingSection,
            saveError = saveError,
            onSavePersonal = viewModel::savePersonalAndEducation,
            onToggleSkill = viewModel::toggleSkill,
            onSaveGoals = viewModel::savePreparationGoals,
            onSaveExperienceLevel = viewModel::saveExperienceLevel,
            onSelectRole = onSelectRole,
            onSelectSecondaryRole = onSelectSecondaryRole,
            onReplaceResume = onReplaceResume,
            onRemoveResume = viewModel::removeResume,
            presence = presence,
            onOpenProfessionalPresence = onOpenProfessionalPresence,
            onConnectGitHub = onConnectGitHub,
        )
    }
}

/** Mirrors the real screen's shape -- a header row plus stacked section-sized blocks -- so
 * the loading state doesn't look unrelated to the form that's about to render. */
@Composable
private fun ProfileLoadingSkeleton() {
    Column(
        modifier = Modifier.fillMaxSize().padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        SkeletonText(widthFractions = listOf(0.3f))
        SkeletonCard(textLines = 3)
        SkeletonCard(textLines = 2)
        SkeletonCard(textLines = 2)
    }
}

@Composable
private fun ProfileContent(
    profile: ProfileDto,
    skills: List<SkillDto>,
    resume: ResumeSummaryDto?,
    roles: List<RoleDto>,
    savingSection: ProfileSection?,
    saveError: String?,
    onSavePersonal: (String?, String?, String?, String?, String?, Int?) -> Unit,
    onToggleSkill: (String) -> Unit,
    onSaveGoals: (List<String>) -> Unit,
    onSaveExperienceLevel: (String) -> Unit,
    onSelectRole: () -> Unit,
    onSelectSecondaryRole: () -> Unit,
    onReplaceResume: () -> Unit,
    onRemoveResume: (Int) -> Unit,
    presence: PresenceAuditDto?,
    onOpenProfessionalPresence: () -> Unit,
    onConnectGitHub: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item { Text(text = "Profile", style = MaterialTheme.typography.headlineSmall) }

        if (saveError != null) {
            item {
                // Announced automatically by screen readers as soon as it appears -- doesn't
                // solve which of the 5 independently-saveable sections below failed (that
                // needs saveError to become per-section, a bigger ViewModel change), but at
                // least ensures the failure itself is never silently missed.
                Text(
                    text = saveError,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }

        item {
            PersonalAndEducationSection(
                profile = profile,
                saving = savingSection == ProfileSection.PERSONAL,
                onSave = onSavePersonal,
            )
        }

        item {
            TargetRolesSection(
                profile = profile,
                roles = roles,
                onSelectRole = onSelectRole,
                onSelectSecondaryRole = onSelectSecondaryRole,
            )
        }

        item {
            SectionCard(title = "Skills") {
                SkillPicker(skills = skills, busy = savingSection == ProfileSection.SKILLS, onToggle = onToggleSkill)
            }
        }

        item {
            PreparationGoalsSection(
                initialGoals = profile.preparationGoals.orEmpty(),
                saving = savingSection == ProfileSection.GOALS,
                onSave = onSaveGoals,
            )
        }

        item {
            ExperienceLevelSection(
                initialLevel = profile.experienceLevel,
                saving = savingSection == ProfileSection.EXPERIENCE_LEVEL,
                onSave = onSaveExperienceLevel,
            )
        }

        item {
            ResumeSection(resume = resume, onReplaceResume = onReplaceResume, onRemoveResume = onRemoveResume)
        }

        item {
            ProfessionalPresenceSection(
                presence = presence,
                onOpen = onOpenProfessionalPresence,
                onConnect = onConnectGitHub,
            )
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Column {
        Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        AppCard(modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm)) { content() }
    }
}

@Composable
private fun PersonalAndEducationSection(
    profile: ProfileDto,
    saving: Boolean,
    onSave: (String?, String?, String?, String?, String?, Int?) -> Unit,
) {
    // Keyed on profile.id (stable across saves), not the whole profile object -- so a save in
    // a DIFFERENT section (which replaces uiState with a fresh ProfileDto) never resets what
    // the student is mid-typing here. rememberSaveable (not plain remember) so an in-progress
    // edit also survives a rotation or process death instead of silently reverting to the
    // last-saved server value.
    var fullName by rememberSaveable(profile.id) { mutableStateOf(profile.fullName.orEmpty()) }
    var mobileNumber by rememberSaveable(profile.id) { mutableStateOf(profile.mobileNumber.orEmpty()) }
    var college by rememberSaveable(profile.id) { mutableStateOf(profile.college.orEmpty()) }
    var degree by rememberSaveable(profile.id) { mutableStateOf(profile.degree.orEmpty()) }
    var branch by rememberSaveable(profile.id) { mutableStateOf(profile.branch.orEmpty()) }
    var graduationYear by rememberSaveable(profile.id) { mutableStateOf(profile.graduationYear?.toString().orEmpty()) }

    SectionCard(title = "Personal Information") {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            OutlinedTextField(
                value = profile.email,
                onValueChange = {},
                label = { Text("Email") },
                singleLine = true,
                enabled = false,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = fullName,
                onValueChange = { fullName = it },
                label = { Text("Full name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = mobileNumber,
                onValueChange = { if (it.length <= 20) mobileNumber = it },
                label = { Text("Mobile number") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth(),
            )
            Text(text = "Education", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = Spacing.xs))
            OutlinedTextField(
                value = college,
                onValueChange = { college = it },
                label = { Text("College / University") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedTextField(
                    value = degree,
                    onValueChange = { degree = it },
                    label = { Text("Degree") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = branch,
                    onValueChange = { branch = it },
                    label = { Text("Branch") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            OutlinedTextField(
                value = graduationYear,
                onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) graduationYear = it },
                label = { Text("Graduation year") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            PrimaryButton(
                text = if (saving) "Saving..." else "Save",
                enabled = !saving,
                onClick = {
                    onSave(
                        fullName.ifBlank { null },
                        mobileNumber.ifBlank { null },
                        college.ifBlank { null },
                        degree.ifBlank { null },
                        branch.ifBlank { null },
                        graduationYear.toIntOrNull(),
                    )
                },
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs),
            )
        }
    }
}

@Composable
private fun TargetRolesSection(
    profile: ProfileDto,
    roles: List<RoleDto>,
    onSelectRole: () -> Unit,
    onSelectSecondaryRole: () -> Unit,
) {
    val primaryLabel = profile.customTargetRole
        ?: roles.find { it.id == profile.targetRoleId }?.name
        ?: "Not selected"
    val secondaryLabel = roles.find { it.id == profile.secondaryTargetRoleId }?.name

    SectionCard(title = "Target Roles") {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(text = "Primary role", style = MaterialTheme.typography.labelMedium)
            Text(text = primaryLabel, style = MaterialTheme.typography.titleMedium)
            SecondaryButton(text = "Change target role", onClick = onSelectRole, modifier = Modifier.fillMaxWidth())

            Text(text = "Secondary role (optional)", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = Spacing.xs))
            Text(text = secondaryLabel ?: "Not selected", style = MaterialTheme.typography.titleMedium)
            SecondaryButton(
                text = if (secondaryLabel == null) "Add secondary role" else "Change secondary role",
                onClick = onSelectSecondaryRole,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PreparationGoalsSection(initialGoals: List<String>, saving: Boolean, onSave: (List<String>) -> Unit) {
    var selected by remember(initialGoals) { mutableStateOf(initialGoals.toSet()) }

    SectionCard(title = "Preparation Goals") {
        Column {
            // chipGap (not xs) between these -- see Spacing.chipGap's own doc comment.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.chipGap)) {
                GOALS.forEach { goal ->
                    FilterChip(
                        selected = goal in selected,
                        onClick = { selected = if (goal in selected) selected - goal else selected + goal },
                        enabled = !saving,
                        label = { Text(goal) },
                    )
                }
            }
            PrimaryButton(
                text = if (saving) "Saving..." else "Save",
                enabled = !saving,
                onClick = { onSave(selected.toList()) },
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
            )
        }
    }
}

@Composable
private fun ExperienceLevelSection(initialLevel: String?, saving: Boolean, onSave: (String) -> Unit) {
    var selectedLevel by remember(initialLevel) { mutableStateOf(initialLevel) }

    SectionCard(title = "Experience Level") {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            LEVELS.forEach { (value, description) ->
                val isSelected = selectedLevel == value
                AppCard(
                    containerColor = if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    modifier = Modifier.fillMaxWidth().selectable(
                        selected = isSelected,
                        enabled = !saving,
                        role = Role.RadioButton,
                        onClick = { selectedLevel = value },
                    ),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Selection must not be color-only -- the filled/empty radio glyph is
                        // a real shape difference, not just the card's tint.
                        RadioButton(selected = isSelected, onClick = null, enabled = !saving)
                        Column {
                            Text(text = value.replaceFirstChar(Char::uppercase), style = MaterialTheme.typography.titleSmall)
                            Text(text = description, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            PrimaryButton(
                text = if (saving) "Saving..." else "Save",
                enabled = !saving && selectedLevel != null,
                onClick = { selectedLevel?.let(onSave) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ResumeSection(resume: ResumeSummaryDto?, onReplaceResume: () -> Unit, onRemoveResume: (Int) -> Unit) {
    var confirmingRemove by remember { mutableStateOf(false) }

    if (confirmingRemove && resume != null) {
        ConfirmationDialog(
            title = "Remove resume?",
            message = "This deletes your uploaded resume and its analysis. This can't be undone.",
            confirmText = "Remove",
            isDestructive = true,
            onConfirm = { confirmingRemove = false; onRemoveResume(resume.id) },
            onDismiss = { confirmingRemove = false },
        )
    }

    SectionCard(title = "Resume") {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            if (resume == null) {
                Text(text = "No resume uploaded yet.", style = MaterialTheme.typography.bodyMedium)
            } else {
                Text(text = resume.filename ?: "Resume.pdf", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "Status: ${resume.parseStatus.replaceFirstChar(Char::uppercase)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SecondaryButton(
                text = if (resume == null) "Upload Resume" else "Replace Resume",
                onClick = onReplaceResume,
                modifier = Modifier.fillMaxWidth(),
            )
            if (resume != null) {
                SecondaryButton(text = "Remove Resume", onClick = { confirmingRemove = true }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

/**
 * The way back into Professional Presence for a student who skipped it during onboarding, and
 * the only place in Profile that says anything about GitHub.
 *
 * It renders server state and nothing else -- no local "connected" flag is kept anywhere, so
 * this card cannot drift out of step with the connection it describes. A null [presence] means
 * the read failed, and is rendered as the not-connected state deliberately: it still offers the
 * way in, and claiming "not connected" is less wrong than claiming a connection we cannot see.
 *
 * Every action lives on the Professional Presence screen. This card opens it -- [onConnect] opens
 * it already connecting, because the student has just asked to -- and it never syncs or
 * disconnects, so there is exactly one place where those can go wrong.
 */
@Composable
private fun ProfessionalPresenceSection(
    presence: PresenceAuditDto?,
    onOpen: () -> Unit,
    onConnect: () -> Unit,
) {
    val connected = presence?.connected == true
    // Still the student's connection, with everything it already proved, but GitHub no longer
    // accepts it. A green tick here would say the opposite of the screen it opens.
    val needsReconnect = connected && presence?.status == ProfessionalPresenceViewModel.STATUS_NEEDS_REAUTH
    SectionCard(title = "Professional Presence") {
        // No padding of its own: AppCard already insets its content by Spacing.md, and a second
        // inset here left this card's edges and button narrower than every sibling section's.
        Column {
            if (!connected) {
                Text(
                    text = "Strengthen your profile with evidence from your own public projects.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Spacing.md))
                Text(text = "GitHub", style = MaterialTheme.typography.titleSmall)
                Text(
                    text = "Not connected",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Spacing.md))
                PrimaryButton(
                    text = "Connect GitHub",
                    onClick = onConnect,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "GitHub", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.width(Spacing.sm))
                    if (needsReconnect) {
                        Icon(
                            imageVector = Icons.Filled.WarningAmber,
                            contentDescription = "Needs reconnecting",
                            modifier = Modifier.size(18.dp),
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = "Connected",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                presence.username?.let {
                    Text(
                        text = "@$it",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = Spacing.xs),
                    )
                }
                if (needsReconnect) {
                    Text(
                        text = "Needs reconnecting: GitHub stopped accepting access. " +
                            "Your evidence is kept.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = Spacing.xs),
                    )
                }
                // "Never" rather than a blank: a connection that has never synced is a real,
                // actionable state, and an empty line reads as a rendering fault.
                Text(
                    text = "Last synced: " + (presence.lastSyncedAt?.take(10) ?: "Never"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
                Spacer(Modifier.height(Spacing.sm))
                Row {
                    CountPill("Repositories", presence.repositoryCount)
                    Spacer(Modifier.width(Spacing.md))
                    CountPill("Evidence", presence.evidenceCount)
                }
                Spacer(Modifier.height(Spacing.md))
                if (needsReconnect) {
                    PrimaryButton(
                        text = "Reconnect GitHub",
                        onClick = onConnect,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(Spacing.sm))
                    SecondaryButton(
                        text = "View Professional Presence",
                        onClick = onOpen,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    PrimaryButton(
                        text = "View Professional Presence",
                        onClick = onOpen,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun CountPill(label: String, value: Int) {
    Column {
        Text(text = value.toString(), style = MaterialTheme.typography.titleMedium)
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
