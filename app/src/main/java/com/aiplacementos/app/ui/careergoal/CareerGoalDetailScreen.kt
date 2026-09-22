package com.aiplacementos.app.ui.careergoal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aiplacementos.app.data.remote.CareerGoalDetailDto
import com.aiplacementos.app.data.remote.GoalInterviewRiskDto
import com.aiplacementos.app.data.remote.GoalPreparationItemDto
import com.aiplacementos.app.data.remote.GoalSkillEvidenceDto
import com.aiplacementos.app.ui.common.AppCard
import com.aiplacementos.app.ui.common.ConfirmationDialog
import com.aiplacementos.app.ui.common.DetailTopBar
import com.aiplacementos.app.ui.common.ErrorState
import com.aiplacementos.app.ui.common.ExpandableSection
import com.aiplacementos.app.ui.common.PrimaryButton
import com.aiplacementos.app.ui.common.SecondaryButton
import com.aiplacementos.app.ui.common.SkeletonList
import com.aiplacementos.app.ui.common.networkModule
import com.aiplacementos.app.ui.theme.Spacing

/**
 * One job, and what this app can actually say about it.
 *
 * The screen's job is to stay honest under every state the backend can be in: never analyzed,
 * analyzed with nothing found, analyzed with evidence, and analysis failed. Each of those says
 * something different, and none of them renders as a zero.
 */
@Composable
fun CareerGoalDetailScreen(
    careerGoalId: Int,
    onBack: () -> Unit,
    onOpenDestination: (destination: String) -> Unit,
) {
    val network = networkModule()
    val viewModel: CareerGoalDetailViewModel = viewModel(
        key = "career-goal-$careerGoalId",
        factory = viewModelFactory {
            initializer { CareerGoalDetailViewModel(careerGoalId, network.careerGoalRepository) }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isAnalyzing by viewModel.isAnalyzing.collectAsStateWithLifecycle()
    val analysisError by viewModel.analysisError.collectAsStateWithLifecycle()
    val isDeleted by viewModel.isDeleted.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(isDeleted) {
        if (isDeleted) onBack()
    }

    Scaffold(
        topBar = {
            DetailTopBar(
                title = (state as? CareerGoalDetailUiState.Success)?.goal?.title ?: "Career goal",
                onBack = onBack,
            )
        },
    ) { padding ->
        when (val current = state) {
            is CareerGoalDetailUiState.Loading ->
                SkeletonList(count = 5, modifier = Modifier.fillMaxSize().padding(padding).padding(Spacing.md))

            is CareerGoalDetailUiState.Error -> ErrorState(
                message = current.message,
                icon = if (current.isNetworkError) Icons.Filled.WifiOff else Icons.Filled.ErrorOutline,
                modifier = Modifier.fillMaxSize().padding(padding),
                onRetry = viewModel::load,
            )

            is CareerGoalDetailUiState.Success -> CareerGoalDetail(
                goal = current.goal,
                isAnalyzing = isAnalyzing,
                analysisError = analysisError,
                modifier = Modifier.fillMaxSize().padding(padding),
                onAnalyze = viewModel::analyze,
                onOpenDestination = onOpenDestination,
                onArchive = { viewModel.setStatus("archived") },
                onDelete = { confirmDelete = true },
            )
        }
    }

    if (confirmDelete) {
        ConfirmationDialog(
            title = "Delete this career goal?",
            message = "The job description and everything worked out from it will be removed. " +
                "Archiving keeps it instead.",
            confirmText = "Delete",
            isDestructive = true,
            onConfirm = {
                confirmDelete = false
                viewModel.delete()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun CareerGoalDetail(
    goal: CareerGoalDetailDto,
    isAnalyzing: Boolean,
    analysisError: String?,
    modifier: Modifier = Modifier,
    onAnalyze: () -> Unit,
    onOpenDestination: (String) -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        item { TargetJobCard(goal) }
        item {
            AnalysisCard(
                goal = goal,
                isAnalyzing = isAnalyzing,
                analysisError = analysisError,
                onAnalyze = onAnalyze,
            )
        }

        if (goal.isAnalyzed) {
            item { ProgressCard(goal) }
            item { EvidenceCard(goal) }
            if (goal.interviewRisks.isNotEmpty()) {
                item { InterviewRisksCard(goal.interviewRisks) }
            }
            if (goal.preparationPlan.isNotEmpty()) {
                item { PreparationCard(goal.preparationPlan, onOpenDestination) }
            }
            goal.nextBestAction?.let { action ->
                if (action.hasRecommendation) {
                    item { NextActionCard(action.title, action.reasons, action.destination, onOpenDestination) }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                SecondaryButton(text = "Archive", onClick = onArchive, modifier = Modifier.weight(1f))
                SecondaryButton(text = "Delete", onClick = onDelete, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun TargetJobCard(goal: CareerGoalDetailDto) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        SectionHeading("Target job")
        Text(goal.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            text = listOfNotNull(goal.company, goal.location).joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = goalStatusLabel(goal.status),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.xs),
        )
        ExpandableSection(title = "Job description", modifier = Modifier.padding(top = Spacing.sm)) {
            Text(goal.jobDescription, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun AnalysisCard(
    goal: CareerGoalDetailDto,
    isAnalyzing: Boolean,
    analysisError: String?,
    onAnalyze: () -> Unit,
) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        SectionHeading("Job analysis")
        if (goal.isAnalyzed) {
            Text(
                text = "Read this posting and found ${goal.requirements.size} things it asks for.",
                style = MaterialTheme.typography.bodyMedium,
            )
            goal.detectedRoleTitle?.let {
                Text(
                    text = "Reads as: $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            // "Never analyzed" is its own state, not an error and not an empty result. The
            // wording has to make clear nothing is wrong -- there is simply a step to take.
            Text(
                text = "This posting hasn't been read yet. Analyzing it is what turns it into a " +
                    "preparation plan.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (analysisError != null) {
            // A failed analysis never replaces the page and never invents a result: the goal
            // and its job description are untouched, and the student can simply try again.
            Text(
                text = analysisError,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
        PrimaryButton(
            text = when {
                isAnalyzing -> "Analyzing…"
                goal.isAnalyzed -> "Analyze again"
                else -> "Analyze this job"
            },
            onClick = onAnalyze,
            enabled = !isAnalyzing,
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
        )
    }
}

@Composable
private fun ProgressCard(goal: CareerGoalDetailDto) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        SectionHeading("Preparation")
        Text(
            text = preparationStateLabel(goal.progress.state),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        val coverage = goal.progress.evidenceCoveragePercent
        Text(
            // Null coverage is NOT rendered as 0%. There is nothing to measure, and a zero
            // would read as a measured result -- the same rule as an ungraded interview.
            text = if (coverage == null) {
                "There's nothing measurable in this posting yet."
            } else {
                "Evidence covers $coverage% of this job's skills " +
                    "(${goal.progress.skillsWithEvidence} of ${goal.progress.skillsTotal})."
            },
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = Spacing.xs),
        )
        Text(
            text = "Interviews since you added this: ${goal.progress.interviewsSinceCreated}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EvidenceCard(goal: CareerGoalDetailDto) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        SectionHeading("What you can show")
        if (goal.aligned.isEmpty() && goal.needsAttention.isEmpty() && goal.unknown.isEmpty()) {
            Text(
                text = "This posting doesn't name any skills this app can match evidence against.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        SkillGroup(ALIGNED_HEADING, goal.aligned)
        SkillGroup(NEEDS_ATTENTION_HEADING, goal.needsAttention)
        if (goal.unknown.isNotEmpty()) {
            SkillGroup(UNKNOWN_HEADING, goal.unknown)
            Text(
                text = UNKNOWN_EXPLANATION,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
        goal.unavailableEvidenceSources.forEach { source ->
            // Named rather than silently absent: "we found nothing" and "we never looked" are
            // different statements, and only the first one is a finding.
            Text(
                text = unavailableSourceLabel(source),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
    }
}

@Composable
private fun SkillGroup(heading: String, skills: List<GoalSkillEvidenceDto>) {
    if (skills.isEmpty()) return
    Text(
        text = heading,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = Spacing.sm).semantics { heading() },
    )
    skills.forEach { skill ->
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(skill.skill, style = MaterialTheme.typography.bodyMedium)
                if (!skill.isExplicit) {
                    // The posting did not literally say this; the parser inferred it. Saying so
                    // is the difference between a requirement and a guess.
                    Text(
                        text = "Implied by the role, not stated in the posting",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (skill.sources.isNotEmpty()) {
                    Text(
                        text = skill.sources.joinToString(", ") { evidenceSourceLabel(it) },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                // The server's own wording, rendered verbatim -- the client never composes a
                // verdict of its own.
                text = skill.verdictLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun InterviewRisksCard(risks: List<GoalInterviewRiskDto>) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        SectionHeading("Worth preparing")
        risks.forEach { risk ->
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs)) {
                Text(risk.label, style = MaterialTheme.typography.bodyMedium)
                Text(
                    // A word as well as a colour: colour alone is not an accessible signal.
                    text = riskSeverityLabel(risk.severity),
                    style = MaterialTheme.typography.bodySmall,
                    color = riskSeverityColor(risk.severity),
                )
                // Null occurrences means no cross-interview record exists. Rendering "0 times"
                // would claim this had been checked and found clean.
                risk.occurrences?.let { occurrences ->
                    Text(
                        text = "Come up $occurrences ${if (occurrences == 1) "time" else "times"} so far",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun PreparationCard(
    plan: List<GoalPreparationItemDto>,
    onOpenDestination: (String) -> Unit,
) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        SectionHeading("Preparation plan")
        plan.forEach { item ->
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs)) {
                Text(item.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(
                    item.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // Every step opens a feature this app already has -- Phase 2D launches the
                // existing interview and practice systems rather than owning any of them.
                if (item.destination != "career_goal_analyze") {
                    SecondaryButton(
                        text = "Open",
                        onClick = { onOpenDestination(item.destination) },
                        modifier = Modifier.padding(top = Spacing.xs),
                    )
                }
            }
        }
    }
}

@Composable
private fun NextActionCard(
    title: String,
    reasons: List<String>,
    destination: String?,
    onOpenDestination: (String) -> Unit,
) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        SectionHeading("Next best action")
        Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        reasons.forEach { reason ->
            Text(
                text = "• $reason",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        destination?.let {
            SecondaryButton(
                text = "Open",
                onClick = { onOpenDestination(it) },
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
    }
}

@Composable
private fun SectionHeading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.semantics { heading() },
    )
}
