package com.aiplacementos.app.ui.careerdecision

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aiplacementos.app.data.remote.CareerDecisionRoleDto
import com.aiplacementos.app.data.remote.RoleDto
import com.aiplacementos.app.ui.common.AppCard
import com.aiplacementos.app.ui.common.EmptyState
import com.aiplacementos.app.ui.common.ErrorState
import com.aiplacementos.app.ui.common.PaywallGate
import com.aiplacementos.app.ui.common.PrimaryButton
import com.aiplacementos.app.ui.common.ScreenHeader
import com.aiplacementos.app.ui.common.SkeletonList
import com.aiplacementos.app.ui.common.SkillBar
import com.aiplacementos.app.ui.common.networkModule
import com.aiplacementos.app.ui.theme.Spacing
import com.aiplacementos.app.ui.theme.successColor
import com.aiplacementos.app.ui.theme.warningColor

private fun classificationLabel(classification: String?): String = when (classification) {
    "best_fit" -> "Best Fit"
    "alternative" -> "Alternative"
    "stretch" -> "Stretch"
    // Null means the backend could not measure this role at all (no required skills defined).
    // The old `else -> "Stretch"` caught that case and reported a verdict the server never
    // made -- which is exactly the fabrication Phase 2F removes on the backend, so the client
    // must not reintroduce it here.
    else -> "Not enough data"
}

/**
 * The whole feature is premium (see the Phase 13 gating decision) -- unlike Career Twin/
 * Recruiter Attack, where only an advanced detail section is gated, Career Decision Lab is a
 * planning/simulation tool rather than core prep, so the entire screen sits behind PaywallGate
 * rather than degrading the free experience within it.
 */
@Composable
fun CareerDecisionLabScreen(onUpgrade: () -> Unit) {
    PaywallGate(
        title = "Career Decision Lab is Premium",
        description = CAREER_DECISION_LAB_PAYWALL_DESCRIPTION,
        onUpgrade = onUpgrade,
        modifier = Modifier.fillMaxSize().padding(Spacing.md),
    ) {
        CareerDecisionLabContent()
    }
}

@Composable
private fun CareerDecisionLabContent() {
    val network = networkModule()
    val viewModel: CareerDecisionLabViewModel = viewModel(
        factory = viewModelFactory {
            initializer { CareerDecisionLabViewModel(network.careerDecisionRepository, network.profileRepository) }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LifecycleResumeEffect(Unit) {
        if (state !is CareerDecisionUiState.Ready) viewModel.loadRoles()
        onPauseOrDispose {}
    }

    when (val current = state) {
        is CareerDecisionUiState.LoadingRoles -> SkeletonList(count = 3, modifier = Modifier.fillMaxSize().padding(Spacing.md))
        is CareerDecisionUiState.RolesError -> ErrorState(
            message = current.message,
            icon = Icons.Filled.WifiOff,
            modifier = Modifier.fillMaxSize(),
            onRetry = viewModel::loadRoles,
        )
        is CareerDecisionUiState.Ready -> {
            if (current.availableRoles.isEmpty()) {
                EmptyState(
                    title = "No Roles Available",
                    message = "There are no catalog roles to compare yet.",
                    icon = Icons.AutoMirrored.Filled.CompareArrows,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                CareerDecisionContent(current, viewModel)
            }
        }
    }
}

@Composable
private fun CareerDecisionContent(state: CareerDecisionUiState.Ready, viewModel: CareerDecisionLabViewModel) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item {
            ScreenHeader(
                title = "Career Decision Lab",
                subtitle = "Compare target roles against the hours you can actually study.",
            )
            Text(
                text = CAREER_DECISION_LAB_INTRO,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item { RolePickerRow(state.availableRoles, state.selectedRoleIds, viewModel::toggleRole) }
        item {
            Column {
                Text(text = "If I study ${state.weeklyHours} hours/week", style = MaterialTheme.typography.labelLarge)
                Slider(
                    value = state.weeklyHours.toFloat(),
                    onValueChange = { viewModel.setWeeklyHours(it.toInt()) },
                    onValueChangeFinished = viewModel::onWeeklyHoursSettled,
                    valueRange = 1f..80f,
                )
            }
        }
        item {
            PrimaryButton(
                text = "Compare",
                loading = state.simulating,
                onClick = viewModel::compare,
                enabled = state.canCompare,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (!state.canCompare) {
            item {
                Text(
                    text = "Select 2 or 3 roles to compare.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (state.simulateError != null) {
            item {
                Text(text = state.simulateError, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
        state.result?.let { result ->
            if (result.sharedSkills.isNotEmpty()) {
                item {
                    Text(
                        text = "Shared across these roles: ${result.sharedSkills.joinToString(", ")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(result.roles, key = { it.roleId }) { role -> RoleComparisonCard(role) }
            item {
                Column {
                    result.assumptions.forEach {
                        Text(text = "• $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun RolePickerRow(roles: List<RoleDto>, selectedRoleIds: List<Int>, onToggle: (Int) -> Unit) {
    // chipGap (not sm) between these -- see Spacing.chipGap's own doc comment.
    LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.chipGap)) {
        items(roles, key = { it.id }) { role ->
            FilterChip(
                selected = role.id in selectedRoleIds,
                onClick = { onToggle(role.id) },
                label = { Text(role.name) },
            )
        }
    }
}

@Composable
private fun RoleComparisonCard(role: CareerDecisionRoleDto) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(text = role.displayRoleName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            // Purely informational classification badge, not an action -- a chip component
            // with an empty onClick left this focusable but inert for keyboard/TalkBack users.
            val classificationColor = if (role.classification == "best_fit") {
                successColor()
            } else if (role.classification == "stretch") {
                warningColor()
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
            Surface(color = classificationColor.copy(alpha = 0.15f), shape = MaterialTheme.shapes.small) {
                Text(
                    text = classificationLabel(role.classification),
                    style = MaterialTheme.typography.labelLarge,
                    color = classificationColor,
                    modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                )
            }
        }
        // "Evidence coverage", not "Readiness": the number is the share of this role's required
        // skills the student's evidence supports. A null means the role defines no skills, so
        // there is nothing to draw -- a bar at 0 would claim a measurement that never happened.
        // The label comes from CareerDecisionLabels so the prose above the card and the card
        // itself cannot drift apart again (F12-08).
        val coverage = role.evidenceCoveragePercent
        if (coverage != null) {
            SkillBar(
                label = EVIDENCE_COVERAGE_LABEL,
                percent = coverage,
                modifier = Modifier.padding(top = Spacing.sm),
                isGap = role.classification == "stretch",
            )
        } else {
            Text(
                text = "This role has no required skills defined yet, so it can't be compared.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.sm),
            )
        }
        role.biggestGapSkill?.let {
            Text(
                text = "Biggest gap: $it",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
        val hours = role.estimatedHoursToStrengthen
        if (hours != null && hours > 0) {
            val weeksText = role.estimatedWeeks?.let { " (~$it weeks at your pace)" } ?: ""
            Text(
                text = "Estimated ~${hours.toInt()} hours to strengthen$weeksText",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
        Column(modifier = Modifier.padding(top = Spacing.sm)) {
            role.reasons.forEach {
                Text(text = "• $it", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
