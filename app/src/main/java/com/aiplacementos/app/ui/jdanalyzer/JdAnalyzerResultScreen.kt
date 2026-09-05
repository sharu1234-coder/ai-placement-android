package com.aiplacementos.app.ui.jdanalyzer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aiplacementos.app.data.remote.InterviewPrepQuestionDto
import com.aiplacementos.app.data.remote.JobPreparationPlanDto
import com.aiplacementos.app.data.remote.PreparationPlanCategoryDto
import com.aiplacementos.app.data.remote.RequiredSkillDto
import com.aiplacementos.app.data.remote.SkillGapStatusDto
import com.aiplacementos.app.ui.common.AppCard
import com.aiplacementos.app.ui.common.ConfirmationDialog
import com.aiplacementos.app.ui.common.DetailTopBar
import com.aiplacementos.app.ui.common.ErrorState
import com.aiplacementos.app.ui.common.LoadingState
import com.aiplacementos.app.ui.common.ScoreRing
import com.aiplacementos.app.ui.common.SecondaryButton
import com.aiplacementos.app.ui.common.networkModule
import com.aiplacementos.app.ui.theme.Spacing
import com.aiplacementos.app.ui.theme.successColor
import com.aiplacementos.app.ui.theme.warningColor
import kotlinx.coroutines.launch

@Composable
fun JdAnalyzerResultScreen(
    jdId: Int,
    onBack: () -> Unit,
    onGenerateRoadmap: () -> Unit,
    onDeleted: () -> Unit,
    onCareerPathUpdated: (pathId: Int) -> Unit,
) {
    val network = networkModule()
    val viewModel: JdAnalyzerResultViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                JdAnalyzerResultViewModel(jdId, network.jobRepository, network.roadmapRepository, network.careerPathRepository)
            }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmingDelete by remember { mutableStateOf(false) }

    if (confirmingDelete) {
        ConfirmationDialog(
            title = "Delete this analysis?",
            message = "This removes the saved analysis and its preparation plan. This can't be undone.",
            confirmText = "Delete",
            isDestructive = true,
            onConfirm = {
                confirmingDelete = false
                viewModel.delete(onDeleted = onDeleted)
            },
            onDismiss = { confirmingDelete = false },
        )
    }

    // Matches every other drill-into-item screen reached from a history list (e.g.
    // AtsCheckIssuesScreen, SkillPassportDetailScreen) -- this was the one result screen in
    // that family without a back arrow. The delete action moves from an inline header Row
    // into the top bar's actions slot so it survives alongside the new back arrow.
    Scaffold(
        topBar = {
            DetailTopBar(
                title = "Your Preparation Plan",
                onBack = onBack,
                actions = {
                    if (state is JdAnalyzerResultUiState.Success) {
                        IconButton(onClick = { confirmingDelete = true }) {
                            Icon(imageVector = Icons.Filled.Delete, contentDescription = "Delete analysis")
                        }
                    }
                },
            )
        },
    ) { padding ->
        when (val current = state) {
            is JdAnalyzerResultUiState.Loading -> LoadingState(modifier = Modifier.fillMaxSize().padding(padding))
            is JdAnalyzerResultUiState.Error -> ErrorState(
                message = current.message,
                onRetry = viewModel::load,
                modifier = Modifier.fillMaxSize().padding(padding),
            )
            is JdAnalyzerResultUiState.Success -> ResultContent(
                plan = current.plan,
                isGeneratingRoadmap = current.isGeneratingRoadmap,
                onGenerateRoadmap = { days -> viewModel.generateRoadmap(days, onReady = onGenerateRoadmap) },
                isUpdatingCareerPath = current.isUpdatingCareerPath,
                careerPathUpdateError = current.careerPathUpdateError,
                onUpdateCareerPath = { viewModel.updateCareerPath(onDone = onCareerPathUpdated) },
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun ResultContent(
    plan: JobPreparationPlanDto,
    isGeneratingRoadmap: Boolean,
    onGenerateRoadmap: (durationDays: Int) -> Unit,
    isUpdatingCareerPath: Boolean,
    careerPathUpdateError: String?,
    onUpdateCareerPath: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // Real, derived match signal -- the share of required skills the student's profile
    // already covers, from the same preparation_gaps the "Top Missing Skills" section below
    // reads. Never shown when there's no gap data at all, rather than rendering a
    // meaningless 0%.
    val matchPercent = plan.preparationGaps.takeIf { it.isNotEmpty() }
        ?.let { gaps -> (gaps.count { it.status == "strong" } * 100) / gaps.size }
    val totalInterviewQuestions = plan.interviewPrep.size
    // "Generate Roadmap" is this screen's actual start-preparation action; index computed so
    // the top CTA below scrolls to it regardless of how many conditional sections precede it.
    // Baseline lowered by 1 (was 3) now that the header row moved into the top app bar and is
    // no longer an item in this LazyColumn.
    val generateRoadmapIndex = 2 + (if (matchPercent != null) 1 else 0) +
        (if (plan.requiredSkills.isNotEmpty()) 1 else 0) + (if (plan.preparationPlan.isNotEmpty()) 1 else 0)

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth().padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        if (matchPercent != null) {
            item { MatchScoreCard(matchPercent) }
        }

        item { JobSummarySection(plan) }

        item {
            Text(
                text = "Start Preparation Plan →",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable {
                    scope.launch { listState.animateScrollToItem(generateRoadmapIndex) }
                },
            )
        }

        item { PrepStatsRow(preparationCategories = plan.preparationPlan.size, interviewQuestions = totalInterviewQuestions) }

        item { TopMissingSkillsSection(plan.preparationGaps, plan.requiredSkills) }
        item { RequiredSkillsSection(plan.requiredSkills) }
        item { PreparationPlanSection(plan.preparationPlan) }
        item {
            GenerateRoadmapSection(isBusy = isGeneratingRoadmap, onGenerate = onGenerateRoadmap)
        }
        item {
            UpdateCareerPathSection(isBusy = isUpdatingCareerPath, error = careerPathUpdateError, onUpdate = onUpdateCareerPath)
        }
        item { InterviewPrepSection(plan.interviewPrep) }
    }
}

@Composable
private fun MatchScoreCard(matchPercent: Int) {
    val (label, color) = when {
        matchPercent >= 75 -> "Strong Match" to successColor()
        matchPercent >= 50 -> "Good Match" to warningColor()
        else -> "Needs Preparation" to MaterialTheme.colorScheme.error
    }
    AppCard(modifier = Modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.primaryContainer) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ScoreRing(percent = matchPercent, size = 72.dp, ringColor = color)
            Column(modifier = Modifier.padding(start = Spacing.md)) {
                Text(text = label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
                Text(
                    text = "Based on how many required skills your profile already covers.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

/** Two honest at-a-glance counts, not a fabricated hours estimate -- there's no real
 * per-JD time-to-prepare model, so this never invents one. */
@Composable
private fun PrepStatsRow(preparationCategories: Int, interviewQuestions: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.fillMaxWidth()) {
        PrepStatCard(value = preparationCategories.toString(), label = "prep categories", modifier = Modifier.weight(1f))
        PrepStatCard(value = interviewQuestions.toString(), label = "interview topics", modifier = Modifier.weight(1f))
    }
}

@Composable
private fun PrepStatCard(value: String, label: String, modifier: Modifier = Modifier) {
    AppCard(modifier = modifier) {
        Text(text = value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Career Path's ONE deliberate integration point with the JD Analyzer -- the two stay
 * distinct concepts (general role prep vs. this specific job posting); this just lets a
 * student fold one JD's requirements into whichever Career Path they currently have
 * selected. Never touches Placement Path/Roadmap. */
@Composable
private fun UpdateCareerPathSection(isBusy: Boolean, error: String?, onUpdate: () -> Unit) {
    Column {
        SectionTitle("Update My Career Path")
        Text(
            text = "Fold this job's requirements into your currently selected Career Path.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.xs, bottom = Spacing.sm),
        )
        SecondaryButton(
            text = if (isBusy) "Updating..." else "Update My Career Path",
            onClick = onUpdate,
            enabled = !isBusy,
            modifier = Modifier.fillMaxWidth(),
        )
        error?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text = text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun JobSummarySection(plan: JobPreparationPlanDto) {
    Column {
        SectionTitle("Job Summary")
        AppCard(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
        ) {
            val summary = plan.jobSummary
            SummaryRow("Company", summary.companyName ?: "Not specified")
            SummaryRow("Role", summary.jobTitle)
            SummaryRow("Experience", summary.experienceExpectation ?: "Not specified")
            SummaryRow("Location", summary.location ?: "Not specified")
            SummaryRow("Employment type", summary.employmentType ?: "Not specified")
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun RequiredSkillsSection(skills: List<RequiredSkillDto>) {
    if (skills.isEmpty()) return
    Column {
        SectionTitle("Required Skills")
        AppCard(modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm)) {
            skills.forEach { skill ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(text = skill.value, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = skill.importance.replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Only the gaps that actually need attention -- skills already marked "strong" are real
 * evidence the student is fine there, so they're left out of a list titled "missing." Each
 * remaining gap's severity comes from the matching RequiredSkillDto's own importance field
 * when one is found by name, never invented independently of that real signal. */
@Composable
private fun TopMissingSkillsSection(gaps: List<SkillGapStatusDto>, requiredSkills: List<RequiredSkillDto>) {
    if (gaps.isEmpty()) return
    val missing = gaps.filter { it.status != "strong" }
        .sortedByDescending { importanceRank(it, requiredSkills) }
    Column {
        SectionTitle("Top Missing Skills")
        if (missing.isEmpty()) {
            AppCard(modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm)) {
                Text(
                    text = "No major skill gaps found for this role based on your current profile.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = successColor(),
                )
            }
        } else {
            AppCard(modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm)) {
                missing.forEach { gap ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(text = gap.skill, style = MaterialTheme.typography.bodyMedium)
                        GapStatusBadge(gap.status, importanceLabel(gap, requiredSkills))
                    }
                }
            }
        }
    }
}

private fun importanceRank(gap: SkillGapStatusDto, requiredSkills: List<RequiredSkillDto>): Int =
    when (requiredSkills.find { it.value.equals(gap.skill, ignoreCase = true) }?.importance) {
        "high" -> 2
        "medium" -> 1
        else -> 0
    }

private fun importanceLabel(gap: SkillGapStatusDto, requiredSkills: List<RequiredSkillDto>): String? =
    requiredSkills.find { it.value.equals(gap.skill, ignoreCase = true) }?.importance
        ?.replaceFirstChar { it.uppercase() }

@Composable
private fun GapStatusBadge(status: String, importance: String?) {
    val (label, color) = when (status) {
        // "On Your Resume", not "Strong": this status means the JD's skill was matched against
        // the student's resume and nothing flagged it weak -- a claim, not evidence. The Skill
        // Passport's "Strong" is evidence-backed and means something stricter; using one word
        // for both let the weaker claim borrow the stronger one's credibility. See
        // ui/careerpath/CareerPathLabels.kt for the full note.
        "strong" -> "On Your Resume" to MaterialTheme.colorScheme.primary
        "needs_improvement" -> "Needs Improvement" to warningColor()
        else -> "Missing" to MaterialTheme.colorScheme.error
    }
    Column(horizontalAlignment = Alignment.End) {
        Surface(color = color.copy(alpha = 0.15f), shape = MaterialTheme.shapes.small) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = color,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 2.dp),
            )
        }
        if (importance != null) {
            Text(
                text = importance,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PreparationPlanSection(categories: List<PreparationPlanCategoryDto>) {
    if (categories.isEmpty()) return
    Column {
        SectionTitle("Personalized Preparation Plan")
        Column(modifier = Modifier.padding(top = Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            categories.forEach { category ->
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Text(text = category.category, style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = category.reason,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun GenerateRoadmapSection(isBusy: Boolean, onGenerate: (Int) -> Unit) {
    Column {
        SectionTitle("Generate Roadmap")
        Text(
            text = "Build a day-by-day preparation plan for this role.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.xs, bottom = Spacing.sm),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.fillMaxWidth()) {
            listOf(7, 14, 30).forEach { days ->
                SecondaryButton(
                    text = "$days-day",
                    onClick = { onGenerate(days) },
                    enabled = !isBusy,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        if (isBusy) {
            Text(
                text = "Generating...",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
    }
}

@Composable
private fun InterviewPrepSection(questions: List<InterviewPrepQuestionDto>) {
    if (questions.isEmpty()) return
    val grouped = questions.groupBy { it.bucket }
    Column {
        SectionTitle("Expected Interview Preparation")
        Column(modifier = Modifier.padding(top = Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            grouped.forEach { (bucket, bucketQuestions) ->
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Text(text = bucketLabel(bucket), style = MaterialTheme.typography.titleSmall)
                    bucketQuestions.forEach { q ->
                        Text(
                            text = "• ${q.question}",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = Spacing.xs),
                        )
                    }
                }
            }
        }
    }
}

private fun bucketLabel(bucket: String): String = when (bucket) {
    "technical" -> "Technical Questions"
    "coding" -> "Coding Topics"
    "sql" -> "SQL Questions"
    "system_design" -> "System Design"
    "hr" -> "HR Questions"
    "company_specific" -> "Company-Specific"
    else -> bucket.replaceFirstChar { it.uppercase() }
}
