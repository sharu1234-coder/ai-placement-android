package com.aiplacementos.app.ui.jobprep

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aiplacementos.app.data.remote.GapGuidanceDto
import com.aiplacementos.app.data.remote.JobPreparationPlanDto
import com.aiplacementos.app.data.remote.ProjectConnectionDto
import com.aiplacementos.app.data.remote.RequiredSkillDto
import com.aiplacementos.app.data.remote.ResumeRecommendationsDto
import com.aiplacementos.app.data.remote.SkillGapStatusDto
import com.aiplacementos.app.ui.common.AppCard
import com.aiplacementos.app.ui.common.ConfirmationDialog
import com.aiplacementos.app.ui.common.ErrorState
import com.aiplacementos.app.ui.common.ExpandableSection
import com.aiplacementos.app.ui.common.PrimaryButton
import com.aiplacementos.app.ui.common.ScoreRing
import com.aiplacementos.app.ui.common.SecondaryButton
import com.aiplacementos.app.ui.common.SkeletonCard
import com.aiplacementos.app.ui.common.SkeletonText
import com.aiplacementos.app.ui.common.networkModule
import com.aiplacementos.app.ui.theme.Spacing
import com.aiplacementos.app.ui.theme.successColor
import com.aiplacementos.app.ui.theme.warningColor

/**
 * Everything the student needs for one job, in the order they actually ask it: what is this
 * job, how do I match, what am I missing, what should I change, what should I study, what will
 * they ask, what do I do now.
 *
 * Progressive disclosure is deliberate. The full plan is a lot of material, so the first screen
 * carries the summary, the match and the biggest gaps, and everything else opens on demand.
 */
@Composable
fun JobMatchPrepResultScreen(
    jdId: Int,
    onBack: () -> Unit,
    onGenerateRoadmap: () -> Unit,
    onStartPractice: (blueprintId: Int) -> Unit,
    onDeleted: () -> Unit,
    onCareerPathUpdated: (pathId: Int) -> Unit,
    onUploadResume: () -> Unit,
) {
    val network = networkModule()
    val viewModel: JobMatchPrepResultViewModel = viewModel(
        key = "job-match-prep-result-$jdId",
        factory = viewModelFactory {
            initializer {
                JobMatchPrepResultViewModel(
                    jdId = jdId,
                    jobRepository = network.jobRepository,
                    roadmapRepository = network.roadmapRepository,
                    assessmentRepository = network.assessmentRepository,
                    careerPathRepository = network.careerPathRepository,
                )
            }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    when (val current = state) {
        is JobMatchPrepResultUiState.Loading -> ResultLoadingSkeleton()
        is JobMatchPrepResultUiState.Error -> ErrorState(
            message = current.message,
            onRetry = viewModel::load,
            modifier = Modifier.fillMaxSize(),
        )
        is JobMatchPrepResultUiState.Success -> ResultContent(
            state = current,
            onGenerateRoadmap = { days -> viewModel.generateRoadmap(days, onGenerateRoadmap) },
            onStartPractice = { viewModel.startPractice(onStartPractice) },
            onDelete = { viewModel.delete(onDeleted) },
            onUpdateCareerPath = { viewModel.updateCareerPath(onCareerPathUpdated) },
            onUploadResume = onUploadResume,
            onBack = onBack,
        )
    }
}

@Composable
private fun ResultLoadingSkeleton() {
    Column(
        modifier = Modifier.fillMaxSize().padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        SkeletonText(widthFractions = listOf(0.5f))
        SkeletonCard(textLines = 3)
        SkeletonCard(textLines = 2)
        SkeletonCard(textLines = 4)
    }
}

@Composable
private fun ResultContent(
    state: JobMatchPrepResultUiState.Success,
    onGenerateRoadmap: (Int) -> Unit,
    onStartPractice: () -> Unit,
    onDelete: () -> Unit,
    onUpdateCareerPath: () -> Unit,
    onUploadResume: () -> Unit,
    onBack: () -> Unit,
) {
    val plan = state.plan
    var confirmingDelete by remember { mutableStateOf(false) }

    if (confirmingDelete) {
        ConfirmationDialog(
            title = "Delete this analysis?",
            message = "This removes the job, its analysis and its preparation plan. This can't be undone.",
            confirmText = "Delete",
            isDestructive = true,
            onConfirm = { confirmingDelete = false; onDelete() },
            onDismiss = { confirmingDelete = false },
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        // 1. Job summary
        item { JobSummaryCard(plan) }

        // 2. Internal match score
        matchPercent(plan.preparationGaps)?.let { percent ->
            item { MatchScoreCard(percent, plan.preparationGaps) }
        }

        if (state.actionError != null) {
            item {
                AppCard(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = state.actionError,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
            }
        }

        // 3. Requirements
        item { RequirementsSection(plan) }

        // 4/5. Your match, and where the gaps are
        if (plan.preparationGaps.isNotEmpty()) {
            item { YourMatchSection(plan.preparationGaps) }
        }

        // 6. Gap guidance
        plan.gapGuidance?.takeIf { it.isNotEmpty() }?.let { guidance ->
            item { GapGuidanceSection(guidance) }
        }

        // 7. Resume recommendations
        item { ResumeRecommendationsSection(plan.resumeRecommendations, onUploadResume) }

        // 8. Projects that already evidence this job
        plan.projectConnections?.takeIf { it.isNotEmpty() }?.let { connections ->
            item { ProjectConnectionsSection(connections) }
        }

        // 9. Day-by-day preparation plan
        item { PreparationPlanSection(plan, state.isGeneratingRoadmap, onGenerateRoadmap) }

        // 10. JD-specific questions
        if (plan.interviewPrep.isNotEmpty()) {
            item { InterviewQuestionsSection(plan) }
        }

        // 11. Start practice, plus the secondary actions
        item {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                PrimaryButton(
                    text = if (state.isStartingPractice) "Starting..." else "Start Practice",
                    enabled = !state.isStartingPractice,
                    onClick = onStartPractice,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "Practice questions built from this job's own requirements.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SecondaryButton(
                    text = if (state.isUpdatingCareerPath) "Updating..." else "Add to my Career Path",
                    enabled = !state.isUpdatingCareerPath,
                    onClick = onUpdateCareerPath,
                    modifier = Modifier.fillMaxWidth(),
                )
                SecondaryButton(
                    text = "Delete this analysis",
                    onClick = { confirmingDelete = true },
                    modifier = Modifier.fillMaxWidth(),
                )
                SecondaryButton(text = "Back", onClick = onBack, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun JobSummaryCard(plan: JobPreparationPlanDto) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(
                text = plan.jobSummary.jobTitle,
                style = MaterialTheme.typography.headlineSmall,
            )
            // Only real values are shown -- a field the job description never stated is left
            // out rather than rendered as "Not specified" noise.
            val facts = listOfNotNull(
                plan.jobSummary.companyName,
                plan.jobSummary.location,
                plan.jobSummary.employmentType,
            )
            if (facts.isNotEmpty()) {
                Text(
                    text = facts.joinToString(" • "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            plan.jobSummary.experienceExpectation?.let {
                Text(text = "Experience: $it", style = MaterialTheme.typography.bodySmall)
            }
            plan.jobSummary.educationEligibility?.let {
                Text(text = "Education: $it", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun MatchScoreCard(percent: Int, gaps: List<SkillGapStatusDto>) {
    val (label, color) = when {
        percent >= 75 -> "Strong match" to successColor()
        percent >= 50 -> "Good match" to warningColor()
        else -> "Early match" to MaterialTheme.colorScheme.error
    }
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The ring is decorative here -- the same number and label are read out by the
            // text beside it, so a screen reader should not announce it twice.
            ScoreRing(
                percent = percent,
                size = 72.dp,
                ringColor = color,
                modifier = Modifier.clearAndSetSemantics {},
            )
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(text = label, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "$percent% of this job's skills are already on your resume.",
                    style = MaterialTheme.typography.bodySmall,
                )
                // Said plainly, because a percentage next to a job invites exactly the wrong
                // reading. This is a comparison of two skill lists, nothing more.
                Text(
                    text = "This compares skill lists only. It isn't a prediction about being " +
                        "shortlisted or hired.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RequirementsSection(plan: JobPreparationPlanDto) {
    val deduped = dedupeRequirements(plan.requiredSkills)
    val required = deduped.filter { it.importance == "required" }
    val preferred = deduped.filter { it.importance != "required" }

    SectionCard(title = "What this job asks for") {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            if (required.isNotEmpty()) {
                Text(text = "Required", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.chipGap)) {
                    required.forEach { skill ->
                        AssistChip(onClick = {}, label = { Text(skill.value) })
                    }
                }
            }
            if (preferred.isNotEmpty()) {
                Text(
                    text = "Preferred",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.chipGap)) {
                    preferred.forEach { skill ->
                        AssistChip(onClick = {}, label = { Text(skill.value) })
                    }
                }
            }
            if (plan.requiredSkills.isEmpty()) {
                Text(
                    text = "This posting didn't list specific skills we could extract.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun YourMatchSection(gaps: List<SkillGapStatusDto>) {
    val onResume = gaps.filter { it.status == "strong" }
    val weak = gaps.filter { it.status == "needs_improvement" }
    val missing = gaps.filter { it.status == "missing" }

    SectionCard(title = "How you match") {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            MatchGroup("On your resume", onResume.map { it.skill }, successColor(), "✓")
            MatchGroup("Needs work", weak.map { it.skill }, warningColor(), "!")
            MatchGroup("Not evidenced yet", missing.map { it.skill }, MaterialTheme.colorScheme.error, "✕")
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MatchGroup(title: String, skills: List<String>, color: Color, marker: String) {
    if (skills.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(text = "$title (${skills.size})", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.chipGap)) {
            skills.forEach { skill ->
                // The marker glyph carries the status as well as the colour, so the grouping
                // is never conveyed by colour alone.
                AssistChip(
                    onClick = {},
                    label = { Text("$marker $skill") },
                    colors = AssistChipDefaults.assistChipColors(labelColor = color),
                    modifier = Modifier.semantics { contentDescription = "$skill: $title" },
                )
            }
        }
    }
}

@Composable
private fun GapGuidanceSection(guidance: List<GapGuidanceDto>) {
    SectionCard(title = "What to do about each gap") {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            guidance.forEach { item ->
                ExpandableSection(title = item.skill) {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        // Whether the posting stated this or we inferred it is a real
                        // distinction the student should see, not a detail to smooth over.
                        Text(
                            text = if (item.isExplicitRequirement) {
                                "Stated in the job description • ${item.requirementImportance}"
                            } else {
                                "Inferred from the role • ${item.requirementImportance}"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        LabelledParagraph("Why it matters", item.whyItMatters)
                        LabelledParagraph("Start with", item.recommendedAction)
                        LabelledParagraph("Practise by", item.practiceDirection)
                    }
                }
            }
        }
    }
}

@Composable
private fun ResumeRecommendationsSection(
    recommendations: ResumeRecommendationsDto?,
    onUploadResume: () -> Unit,
) {
    SectionCard(title = "Improving your resume for this job") {
        when {
            recommendations == null -> Text(
                text = "This analysis was saved before resume recommendations existed. " +
                    "Analyse the job again to see them.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            recommendations.status == "no_resume" -> Column(
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Text(
                    text = "Upload a resume to see how to tailor it to this job. Everything " +
                        "else on this page works without one.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                SecondaryButton(
                    text = "Upload Resume",
                    onClick = onUploadResume,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            recommendations.status == "insufficient_resume_data" -> Text(
                text = "We couldn't read enough from your resume to tailor it to this job. " +
                    "Replacing it with a text-based PDF usually fixes this.",
                style = MaterialTheme.typography.bodyMedium,
            )
            else -> Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                if (recommendations.missingKeywords.isNotEmpty()) {
                    MissingKeywords(recommendations.missingKeywords)
                }
                recommendations.items.forEach { item ->
                    ExpandableSection(title = item.area.replaceFirstChar(Char::uppercase)) {
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            // Current and suggested side by side, so it is always clear which
                            // is the student's own text and which is the suggestion. Nothing
                            // here changes the uploaded PDF.
                            LabelledParagraph("Currently", item.currentFact)
                            LabelledParagraph("Suggested", item.suggestion)
                            Text(
                                text = item.rationale,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Text(
                    text = "These are suggestions for you to apply yourself. Your uploaded " +
                        "resume is never changed automatically.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MissingKeywords(keywords: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(text = "Terms in this posting that aren't in your resume", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.chipGap)) {
            keywords.forEach { keyword -> AssistChip(onClick = {}, label = { Text(keyword) }) }
        }
        Text(
            text = "Only add a term if you genuinely have that experience.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ProjectConnectionsSection(connections: List<ProjectConnectionDto>) {
    SectionCard(title = "Your projects that fit this job") {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            connections.forEach { connection ->
                ExpandableSection(title = connection.projectTitle) {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Text(
                            text = "Relevant to: ${connection.requirement}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(text = connection.whyRelevant, style = MaterialTheme.typography.bodyMedium)
                        if (connection.likelyQuestions.isNotEmpty()) {
                            Text(
                                text = "They may ask",
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(top = Spacing.xs),
                            )
                            connection.likelyQuestions.forEach { question ->
                                Text(text = "• $question", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PreparationPlanSection(
    plan: JobPreparationPlanDto,
    isGenerating: Boolean,
    onGenerateRoadmap: (Int) -> Unit,
) {
    SectionCard(title = "What to study") {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            plan.preparationPlan.forEach { category ->
                Column {
                    Text(text = category.category, style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = category.reason,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = "Turn this into a day-by-day plan you can tick off:",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = Spacing.xs),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.fillMaxWidth()) {
                listOf(7, 14, 30).forEach { days ->
                    SecondaryButton(
                        text = "$days days",
                        enabled = !isGenerating,
                        onClick = { onGenerateRoadmap(days) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            if (isGenerating) {
                Text(
                    text = "Building your day-by-day plan...",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
    }
}

@Composable
private fun InterviewQuestionsSection(plan: JobPreparationPlanDto) {
    val byBucket = plan.interviewPrep.groupBy { it.bucket }
    SectionCard(title = "Questions they could ask") {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            byBucket.forEach { (bucket, questions) ->
                ExpandableSection(title = "${bucketLabel(bucket)} (${questions.size})") {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        questions.forEach { question ->
                            Text(text = "• ${question.question}", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
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
private fun LabelledParagraph(label: String, text: String) {
    Column {
        Text(text = label, style = MaterialTheme.typography.labelLarge)
        Text(text = text, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * The share of this job's skills already on the student's resume.
 *
 * Null when there are no gaps to measure -- with no resume the server returns none, and an
 * invented 0% would read as "you match nothing" rather than "we haven't looked".
 */
internal fun matchPercent(gaps: List<SkillGapStatusDto>): Int? {
    if (gaps.isEmpty()) return null
    val strong = gaps.count { it.status == "strong" }
    return (strong * 100) / gaps.size
}

/**
 * One chip per distinct requirement.
 *
 * The server stores a term the posting mentions as both a "skill" row and a "tool" row --
 * deliberately, because its matching engine treats the two categories differently. Rendering
 * the list verbatim therefore showed "SQL, Excel, Power BI, SQL, Excel, Power BI", which reads
 * as a bug to the student even though the data is correct. Collapsing happens here, in the
 * display layer, rather than server-side where both rows carry meaning.
 *
 * A term listed as both required and preferred collapses to required: the stricter statement
 * is the one the student needs to act on.
 */
internal fun dedupeRequirements(requirements: List<RequiredSkillDto>): List<RequiredSkillDto> =
    requirements
        .groupBy { it.value.trim().lowercase() }
        .map { (_, entries) -> entries.firstOrNull { it.importance == "required" } ?: entries.first() }

internal fun bucketLabel(bucket: String): String = when (bucket) {
    "technical" -> "Technical"
    "coding" -> "Coding"
    "sql" -> "SQL"
    "system_design" -> "System design"
    "hr" -> "HR and motivation"
    "company_specific" -> "About this company"
    else -> bucket.replace('_', ' ').replaceFirstChar(Char::uppercase)
}
