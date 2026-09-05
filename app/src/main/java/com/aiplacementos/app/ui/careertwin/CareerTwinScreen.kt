package com.aiplacementos.app.ui.careertwin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.Surface
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
import com.aiplacementos.app.ui.common.humanizeWeaknessTag
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
            // readinessPercent is null when the backend has a target role but no defined skills
            // to measure against -- an explicit "we don't know yet", not a score of zero. The
            // old `?: 0` rendered that unknown as a confident 0% ring (and, further down, a
            // "See Why You're at 0%" paywall title), which reads as "you are measured, and you
            // scored nothing". Show the reason instead; ScoreRing only ever gets a real number.
            val readiness = twin.readinessPercent
            if (readiness == null) {
                AppCard(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                    Text(
                        text = "Not enough data yet",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = twin.reasons.firstOrNull()
                            ?: "We don't have enough about this role yet to work out a readiness score.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Spacing.xs),
                    )
                }
            } else {
                AppCard(containerColor = MaterialTheme.colorScheme.primaryContainer) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ScoreRing(percent = readiness, size = 96.dp, label = "Readiness")
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
        }
        // Real, derived-not-fabricated summary chips: strengths from the same skills list
        // already rendered in detail below (verified/strong state, highest confidence first),
        // gaps from the backend's own weak_skill_tags -- never invented categories.
        val topStrengths = twin.skills
            .filter { it.state == "verified" || it.state == "strong" }
            .sortedByDescending { it.confidencePercent }
            .take(3)
        if (topStrengths.isNotEmpty()) {
            item { ChipSummaryRow(title = "Top Strengths", labels = topStrengths.map { it.skill }, isWarning = false) }
        }
        if (twin.weakSkillTags.isNotEmpty()) {
            // "Flagged Weak Areas", not "Biggest Gaps": the card further down is titled
            // "Biggest Gap" (singular) and is computed from something else entirely --
            // biggest_gap_skill, the lowest-confidence *role* skill -- whereas these chips come
            // from weak_skill_tags (interview dimensions and test tags). The two lists routinely
            // don't overlap, so sharing a name meant a student could see a "Biggest Gap" that
            // wasn't in "Biggest Gaps". Tags are humanized -- they arrive as machine keys like
            // "interview:clarity".
            item {
                ChipSummaryRow(
                    title = "Flagged Weak Areas",
                    labels = twin.weakSkillTags.map(::humanizeWeaknessTag),
                    isWarning = true,
                )
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
                // Same reason as the ring above: with no readiness number, the title must not
                // invent one. Falls back to score-free wording rather than "0%".
                val readiness = twin.readinessPercent
                PaywallGate(
                    title = if (readiness != null) "See Why You're at $readiness%" else "See How Your Readiness Is Worked Out",
                    description = "Premium unlocks the full evidence breakdown behind your readiness score.",
                    onUpgrade = onUpgrade,
                ) {
                    AppCard {
                        ExpandableSection(title = if (readiness != null) "Why $readiness%?" else "How is this worked out?") {
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
 * NextBestActionCard stays the reusable, independent widget it was designed as (Home
 * deliberately shows its own honest milestone-progress card instead, see HomeScreen's
 * NextMilestoneCard). A loading/error state here renders nothing rather than disrupting the
 * rest of the Career Twin screen -- the same "a secondary widget failing shouldn't blank the
 * whole screen" philosophy HomeViewModel's per-source runCatching already uses. */
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipSummaryRow(title: String, labels: List<String>, isWarning: Boolean) {
    Column {
        Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        FlowRow(
            modifier = Modifier.padding(top = Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            // Purely informational tags, not actions -- a chip component with an empty
            // onClick left these focusable but inert for keyboard/TalkBack users.
            val labelColor = if (isWarning) warningColor() else successColor()
            labels.forEach { label ->
                Surface(color = labelColor.copy(alpha = 0.15f), shape = MaterialTheme.shapes.small) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        color = labelColor,
                        modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                    )
                }
            }
        }
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
