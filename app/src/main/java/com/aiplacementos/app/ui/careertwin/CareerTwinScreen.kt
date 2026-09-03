package com.aiplacementos.app.ui.careertwin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aiplacementos.app.data.remote.CareerTwinDto
import com.aiplacementos.app.data.remote.SkillTwinItemDto
import com.aiplacementos.app.ui.common.AppCard
import com.aiplacementos.app.ui.common.EmptyState
import com.aiplacementos.app.ui.common.ErrorState
import com.aiplacementos.app.ui.common.ExpandableSection
import com.aiplacementos.app.ui.common.NextBestActionCard
import com.aiplacementos.app.ui.common.PaywallGate
import com.aiplacementos.app.ui.common.ScoreRing
import com.aiplacementos.app.ui.common.SkeletonList
import com.aiplacementos.app.ui.common.SkillBar
import com.aiplacementos.app.ui.common.networkModule
import com.aiplacementos.app.ui.nextbestaction.NextBestActionUiState
import com.aiplacementos.app.ui.nextbestaction.NextBestActionViewModel
import com.aiplacementos.app.ui.theme.Spacing
import com.aiplacementos.app.ui.theme.successColor
import com.aiplacementos.app.ui.theme.warningColor

/** state -> (display label, whether it counts as a gap for SkillBar's warning/success tint).
 * A single source of truth so the label and the color can never drift apart from each other
 * or from evidence_engine's own state definitions. */
private fun stateDisplay(state: String): Pair<String, Boolean> = when (state) {
    "verified" -> "Verified" to false
    "strong" -> "Strong" to false
    "supported" -> "Developing" to true
    "stale" -> "Stale" to true
    else -> "Gap" to true // "claimed"
}

@Composable
fun CareerTwinScreen(onSelectRole: () -> Unit, onUpgrade: () -> Unit) {
    val network = networkModule()
    val viewModel: CareerTwinViewModel = viewModel(
        factory = viewModelFactory { initializer { CareerTwinViewModel(network.careerTwinRepository) } },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Evidence changes constantly (a new assessment, a completed project) elsewhere in the
    // app -- refresh on every resume, not just first composition, same as CareerPathHomeScreen.
    LifecycleResumeEffect(Unit) {
        viewModel.load()
        onPauseOrDispose {}
    }

    when (val current = state) {
        is CareerTwinUiState.Loading -> SkeletonList(count = 3, modifier = Modifier.fillMaxSize().padding(Spacing.md))
        is CareerTwinUiState.Error -> ErrorState(
            message = current.message,
            icon = if (current.isNetworkError) Icons.Filled.WifiOff else Icons.Filled.ErrorOutline,
            modifier = Modifier.fillMaxSize(),
            onRetry = viewModel::load,
        )
        is CareerTwinUiState.NoRole -> EmptyState(
            message = current.reason,
            title = "Your Career Twin Isn't Built Yet",
            icon = Icons.Filled.Psychology,
            actionLabel = "Choose a Target Role",
            onAction = onSelectRole,
            modifier = Modifier.fillMaxSize(),
        )
        is CareerTwinUiState.Success -> CareerTwinContent(twin = current.twin, onUpgrade = onUpgrade)
    }
}

@Composable
private fun CareerTwinContent(twin: CareerTwinDto, onUpgrade: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item {
            Text(text = "Your Career Twin", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                text = "Target: ${twin.displayRoleName ?: "—"}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            AppCard(containerColor = MaterialTheme.colorScheme.primaryContainer) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ScoreRing(percent = twin.readinessPercent ?: 0, size = 96.dp, label = "Readiness")
                    Spacer(modifier = Modifier.width(Spacing.md))
                    Column {
                        Text(
                            text = "Career Readiness",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        val trend = twin.readinessTrendPercent
                        if (trend != null) {
                            val sign = if (trend >= 0) "+" else ""
                            Text(
                                text = "$sign$trend% this week",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (trend >= 0) successColor() else warningColor(),
                            )
                        }
                        if (twin.isEstimate) {
                            Text(
                                text = "Estimated — this role's skill list isn't fully verified yet.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
        if (twin.skills.isEmpty()) {
            item {
                AppCard {
                    Text(text = twin.reasons.firstOrNull() ?: "No required skills are defined for this role yet.")
                }
            }
        } else {
            item {
                Text(text = "Skills", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            items(twin.skills, key = { it.skill }) { skill -> SkillTwinRow(skill) }
        }
        if (twin.reasons.isNotEmpty()) {
            item {
                PaywallGate(
                    title = "See Why You're at ${twin.readinessPercent ?: 0}%",
                    description = "Premium unlocks the full evidence breakdown behind your readiness score.",
                    onUpgrade = onUpgrade,
                ) {
                    AppCard {
                        ExpandableSection(title = "Why ${twin.readinessPercent ?: 0}%?") {
                            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                                twin.reasons.forEach { reason ->
                                    Text(text = "•  $reason", style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
            }
        }
        if (twin.biggestGapSkill != null) {
            item {
                AppCard(containerColor = MaterialTheme.colorScheme.errorContainer) {
                    Text(text = "Biggest Gap", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    Text(text = twin.biggestGapSkill, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        item { NextBestActionSection() }
    }
}

/** A richer, independently-ranked recommendation than twin.nextActionHint (which only ever
 * looks at the single biggest skill gap) -- see next_best_action_service.py's 9-tier priority
 * order. Fetched via its own small ViewModel rather than folded into CareerTwinViewModel, so
 * NextBestActionCard stays the reusable, independent widget Home will also embed later. A
 * loading/error state here renders nothing rather than disrupting the rest of the Career Twin
 * screen -- the same "a secondary widget failing shouldn't blank the whole screen" philosophy
 * HomeViewModel's per-source runCatching already uses. */
@Composable
private fun NextBestActionSection() {
    val network = networkModule()
    val viewModel: NextBestActionViewModel = viewModel(
        factory = viewModelFactory { initializer { NextBestActionViewModel(network.nextBestActionRepository) } },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val current = state
    if (current is NextBestActionUiState.Success) {
        NextBestActionCard(action = current.action)
    }
}

@Composable
private fun SkillTwinRow(skill: SkillTwinItemDto) {
    val (label, isGap) = stateDisplay(skill.state)
    AppCard {
        SkillBar(label = "${skill.skill} — $label", percent = skill.confidencePercent, isGap = isGap)
        if (skill.measuredScore != null) {
            Spacer(modifier = Modifier.height(Spacing.xs))
            Text(
                text = "Measured: ${skill.measuredScore.toInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
