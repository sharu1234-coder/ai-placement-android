package com.aiplacementos.app.ui.careerstrategy

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.PauseCircleOutline
import androidx.compose.material.icons.filled.TrendingFlat
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material.icons.filled.WorkOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aiplacementos.app.data.remote.CareerStrategyDto
import com.aiplacementos.app.data.remote.JobConditionsDto
import com.aiplacementos.app.data.remote.JobStrategyDto
import com.aiplacementos.app.data.remote.StrategyBottleneckDto
import com.aiplacementos.app.data.remote.StrategyContradictionDto
import com.aiplacementos.app.data.remote.StrategyDeferredItemDto
import com.aiplacementos.app.data.remote.StrategyQueueItemDto
import com.aiplacementos.app.data.remote.StrategySeriesDto
import com.aiplacementos.app.ui.common.AppCard
import com.aiplacementos.app.ui.common.EmptyState
import com.aiplacementos.app.ui.common.ErrorState
import com.aiplacementos.app.ui.common.ExpandableSection
import com.aiplacementos.app.ui.common.PrimaryButton
import com.aiplacementos.app.ui.common.ScoreRing
import com.aiplacementos.app.ui.common.SecondaryButton
import com.aiplacementos.app.ui.common.SkeletonList
import com.aiplacementos.app.ui.common.networkModule
import com.aiplacementos.app.ui.theme.Elevation
import com.aiplacementos.app.ui.theme.Spacing
import com.aiplacementos.app.ui.theme.warningColor

/**
 * Career Strategy -- the student's command centre.
 *
 * The screen is built around one claim: there is a single most valuable thing to do next, and
 * the app can say what it is and why. So the layout is a decision, not a dashboard. State,
 * bottleneck and next action come first at full weight; everything below them is supporting
 * evidence, and the queue is deliberately capped and numbered rather than presented as a
 * backlog.
 *
 * Two rules run through every composable here.
 *
 * **Empty is not the same as locked.** A free student's response genuinely does not contain the
 * queue, contradictions or history -- the server never serialised them. So an empty list is
 * ambiguous on its own, and `lockedSections` is what disambiguates it. Every gated section asks
 * that list before deciding between "nothing to show" and "upgrade to see this".
 *
 * **Null is not zero.** Readiness, impact and effort are null when the backend could not judge
 * them, and they render as "Not checked yet" rather than 0%. Telling a student they scored zero
 * on something they have not attempted is the single most damaging thing this screen could do.
 */
@Composable
fun CareerStrategyScreen(
    /**
     * Non-null when this screen was opened against one job advert, which adds the job-specific
     * section. The id is all that crosses the navigation boundary: the verdict itself is fetched
     * from the server every time, so a decision can never outlive the data it was computed from.
     */
    listingId: Int? = null,
    onSelectRole: () -> Unit,
    onUpgrade: () -> Unit,
    onOpenDestination: (String) -> Unit,
) {
    val network = networkModule()
    val viewModel: CareerStrategyViewModel = viewModel(
        factory = viewModelFactory { initializer { CareerStrategyViewModel(network.careerStrategyRepository) } },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Evidence, interviews and applications all change elsewhere in the app, and each of them
    // can move the bottleneck. Refreshing on resume keeps this screen from confidently showing
    // a strategy the student has already invalidated.
    LifecycleResumeEffect(Unit) {
        viewModel.load()
        onPauseOrDispose {}
    }

    // Keyed on the id and deliberately not inside the resume effect above: the job verdict is
    // fetched once per advert rather than on every resume, so returning to this screen does not
    // re-request the same listing. That is what keeps opening a job a constant number of calls.
    LaunchedEffect(listingId) {
        if (listingId != null) viewModel.analyzeJob(listingId)
    }

    when (val current = state) {
        is CareerStrategyUiState.Loading ->
            SkeletonList(count = 4, modifier = Modifier.fillMaxSize().padding(Spacing.md))

        is CareerStrategyUiState.Error -> ErrorState(
            message = current.message,
            icon = if (current.isNetworkError) Icons.Filled.WifiOff else Icons.Filled.ErrorOutline,
            modifier = Modifier.fillMaxSize(),
            onRetry = viewModel::load,
        )

        is CareerStrategyUiState.NoRole -> EmptyState(
            message = current.reason,
            title = "Pick a target first",
            icon = Icons.Filled.Explore,
            actionLabel = "Choose a Target Role",
            onAction = onSelectRole,
            modifier = Modifier.fillMaxSize(),
        )

        // Not an error and not a failure on the student's part -- there simply is not enough
        // recorded yet, and the honest screen says so with a way forward.
        is CareerStrategyUiState.InsufficientData -> EmptyState(
            message = current.reason,
            title = "Not enough to go on yet",
            icon = Icons.Filled.Insights,
            actionLabel = "Build Your First Evidence",
            onAction = { onOpenDestination("practice") },
            modifier = Modifier.fillMaxSize(),
        )

        is CareerStrategyUiState.Success -> CareerStrategyContent(
            strategy = current.strategy,
            viewModel = viewModel,
            showJobStrategy = listingId != null,
            onUpgrade = onUpgrade,
            onOpenDestination = onOpenDestination,
        )
    }
}

// -- state presentation -----------------------------------------------------------------------

/** Backend state -> (headline, supporting line). One table so the wording cannot drift between
 * the header and the cards below it. */
private fun stateHeadline(state: String): Pair<String, String> = when (state) {
    "READY_TO_APPLY" -> "You're in good shape" to "No open gaps against your target role right now."
    "FOCUS_ON_SKILLS" -> "Focus on skills" to "A skill your target role depends on is what's worth your time."
    "FOCUS_ON_EVIDENCE" -> "Focus on proof" to "You have the skills. What's missing is evidence of them."
    "FOCUS_ON_INTERVIEW" -> "Focus on interviews" to "Your technical side is holding up. How you come across isn't."
    "FOCUS_ON_RESUME" -> "Focus on your resume" to "What you can already prove isn't reaching your resume."
    "FOCUS_ON_APPLICATION_STRATEGY" -> "Focus on how you apply" to "The work is in where and how you're applying."
    "FOCUS_ON_EXPERIENCE" -> "Experience is the constraint" to "Not something you can change this month."
    "FOCUS_ON_PROFILE" -> "Finish your profile" to "A fuller profile unlocks a sharper strategy."
    else -> "Your strategy" to ""
}

private fun bandLabel(band: String?): String = when (band) {
    "very_high" -> "Very high"
    "high" -> "High"
    "medium" -> "Medium"
    "moderate" -> "Moderate"
    "low" -> "Low"
    null -> "Not checked yet"
    else -> band.replaceFirstChar { it.uppercase() }
}

@Composable
private fun impactColor(impact: String?): Color = when (impact) {
    "very_high", "high" -> MaterialTheme.colorScheme.primary
    "medium" -> warningColor()
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

/** True when a section the student cannot see was withheld rather than simply being empty. */
private fun CareerStrategyDto.isLocked(section: String): Boolean = lockedSections.contains(section)

// -- content ----------------------------------------------------------------------------------

@Composable
private fun CareerStrategyContent(
    strategy: CareerStrategyDto,
    viewModel: CareerStrategyViewModel,
    showJobStrategy: Boolean,
    onUpgrade: () -> Unit,
    onOpenDestination: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item { StrategyHeader(strategy, showJobStrategy) }
        item { CurrentStateCard(strategy) }

        strategy.bottleneck?.let { bottleneck ->
            item { BottleneckCard(bottleneck, strategy, onUpgrade) }
            item { NextActionCard(bottleneck, onOpenDestination) }
            item { WhySection(bottleneck, strategy, onUpgrade) }
        }

        // Everything from here down supports the decision above rather than competing with it.
        item { SectionEyebrow("THE DETAIL BEHIND IT") }

        // Directly under the bottleneck when present: "how does this advert change what I was
        // just told" only makes sense once the student has read what they were told.
        if (showJobStrategy) {
            item { JobStrategySection(viewModel, onUpgrade) }
        }

        item { DeferredSection(strategy, onUpgrade) }
        item { DiminishingReturnsSection(strategy, onUpgrade) }
        item { QueueSection(strategy, onUpgrade, onOpenDestination) }
        item { ContradictionsSection(strategy, onUpgrade) }

        item { SectionEyebrow("LOOKING WIDER") }
        item { TargetComparisonSection(viewModel, onUpgrade) }
        // Only when this screen is not already about one advert -- see TargetJobsCard.
        if (!showJobStrategy) {
            item { TargetJobsCard(onOpenDestination) }
        }
        item { HistorySection(viewModel, onUpgrade) }
        item { Spacer(Modifier.height(Spacing.xl)) }
    }
}

/**
 * A supporting section.
 *
 * Flat on purpose. The bottleneck and the next action are raised; everything below them is
 * detail that supports those two, and on a screen where every card sits at the same elevation
 * nothing reads as more important than anything else -- which is the failure mode these briefs
 * call "a dashboard full of cards". Material's own rule applies: raised means primary.
 */
@Composable
private fun SupportingCard(content: @Composable () -> Unit) {
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        elevation = Elevation.level0,
        content = content,
    )
}

/** A tier label. Turns a flat list of cards into two readable groups. */
@Composable
private fun SectionEyebrow(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = Spacing.sm, bottom = Spacing.xs),
    )
}

@Composable
private fun StrategyHeader(strategy: CareerStrategyDto, showJobStrategy: Boolean = false) {
    Column {
        // Says out loud which of the two questions this screen is answering. Without it, a
        // student who arrived from a job advert reads the career-level bottleneck as a verdict
        // on that advert -- two different claims that look identical on screen.
        if (showJobStrategy) {
            Text(
                text = "YOUR CAREER STRATEGY, AND THIS JOB",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = Spacing.xs),
            )
        }
        Text(
            text = "Your career strategy",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "One next move. Based on everything you've done.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (strategy.snapshotIsNew) {
            Text(
                text = "Your strategy changed since you last looked.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
    }
}

@Composable
private fun CurrentStateCard(strategy: CareerStrategyDto) {
    val (headline, supporting) = stateHeadline(strategy.state)
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                strategy.displayRoleName?.let { role ->
                    Text(
                        text = if (strategy.roleIsEstimate) "Targeting $role (estimated)" else "Targeting $role",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Text(
                    text = headline,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                if (supporting.isNotBlank()) {
                    Text(
                        text = supporting,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(top = Spacing.xs),
                    )
                }
            }

            // Readiness is Career Twin's number, shown here so the two screens always agree.
            // Absent rather than zeroed when there is no basis for it.
            val readiness = strategy.readinessPercent
            if (readiness != null) {
                Spacer(Modifier.width(Spacing.md))
                ScoreRing(
                    percent = readiness,
                    size = 84.dp,
                    label = if (strategy.readinessIsEstimate) "est." else "ready",
                    modifier = Modifier.semantics {
                        contentDescription = "Readiness $readiness percent" +
                            if (strategy.readinessIsEstimate) ", estimated" else ""
                    },
                )
            }
        }

        if (strategy.readinessPercent == null) {
            Text(
                text = "Readiness: not checked yet",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(top = Spacing.sm),
            )
        }

        // Career Twin's own working, beside its own number. A percentage with nothing behind it
        // is a grade; with the reasons it is something the student can check.
        strategy.readinessReasons.forEach { reason ->
            Text(
                text = reason,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
    }
}

@Composable
private fun BottleneckCard(
    bottleneck: StrategyBottleneckDto,
    strategy: CareerStrategyDto,
    onUpgrade: () -> Unit,
) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.ReportProblem,
                contentDescription = null,
                tint = warningColor(),
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = "Your biggest constraint",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = Spacing.xs),
            )
        }
        Text(
            text = bottleneck.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = Spacing.xs),
        )

        // Premium: the "why". Free students see the name and the action, which is a useful
        // screen on its own -- the reasoning is what they are paying for.
        if (bottleneck.description != null) {
            Text(
                text = bottleneck.description,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = Spacing.xs),
            )
            Row(modifier = Modifier.padding(top = Spacing.sm)) {
                MetricChip("Impact", bandLabel(bottleneck.impact), impactColor(bottleneck.impact))
                Spacer(Modifier.width(Spacing.sm))
                MetricChip("Effort", bandLabel(bottleneck.effort), MaterialTheme.colorScheme.onSurfaceVariant)
                bottleneck.confidence?.let {
                    Spacer(Modifier.width(Spacing.sm))
                    MetricChip("Confidence", bandLabel(it), MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else if (strategy.isLocked("why")) {
            LockedNote(
                text = "Premium shows why this is your constraint, and what it's costing you.",
                onUpgrade = onUpgrade,
            )
        }
    }
}

@Composable
private fun NextActionCard(bottleneck: StrategyBottleneckDto, onOpenDestination: (String) -> Unit) {
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.Bolt,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = "Do this next",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(start = Spacing.xs),
            )
        }
        Text(
            text = bottleneck.recommendedAction,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(top = Spacing.xs),
        )
        // Every recommendation lands somewhere real. A suggestion with nowhere to go is not
        // advice, and the backend guarantees a destination for exactly this reason.
        PrimaryButton(
            text = "Start",
            onClick = { onOpenDestination(bottleneck.destination) },
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
        )
    }
}

@Composable
private fun WhySection(
    bottleneck: StrategyBottleneckDto,
    strategy: CareerStrategyDto,
    onUpgrade: () -> Unit,
) {
    if (bottleneck.evidence.isEmpty()) {
        if (strategy.isLocked("evidence")) {
            SupportingCard {
                Text("Why this, and not something else?", style = MaterialTheme.typography.titleMedium)
                LockedNote(
                    text = "Premium shows the actual signals behind this decision.",
                    onUpgrade = onUpgrade,
                )
            }
        }
        return
    }

    AppCard(modifier = Modifier.fillMaxWidth()) {
        ExpandableSection(title = "Why this, and not something else?") {
            Column(modifier = Modifier.padding(top = Spacing.xs)) {
                bottleneck.evidence.forEach { line ->
                    Row(modifier = Modifier.padding(vertical = Spacing.xs)) {
                        Text("•", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = Spacing.sm),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DeferredSection(strategy: CareerStrategyDto, onUpgrade: () -> Unit) {
    val deferred = strategy.doNotDoYet
    if (deferred.isEmpty() && !strategy.isLocked("do_not_do_yet")) return

    SupportingCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.PauseCircleOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = "Don't do this yet",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = Spacing.xs),
            )
        }
        if (deferred.isEmpty()) {
            LockedNote(
                text = "Premium tells you what to stop spending time on, and why.",
                onUpgrade = onUpgrade,
            )
        } else {
            deferred.forEach { DeferredRow(it) }
        }
    }
}

@Composable
private fun DeferredRow(item: StrategyDeferredItemDto) {
    Column(modifier = Modifier.padding(top = Spacing.sm)) {
        Text(item.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        Text(
            text = item.reason,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        item.instead?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
    }
}

@Composable
private fun QueueSection(
    strategy: CareerStrategyDto,
    onUpgrade: () -> Unit,
    onOpenDestination: (String) -> Unit,
) {
    SupportingCard {
        Text("What's next after that", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (strategy.queue.isEmpty()) {
            if (strategy.isLocked("queue")) {
                LockedNote(
                    text = "Premium shows your full priority queue, ranked by impact and effort.",
                    onUpgrade = onUpgrade,
                )
            } else {
                Text(
                    text = "Nothing else is queued up right now.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
            return@SupportingCard
        }
        strategy.queue.forEachIndexed { index, item ->
            QueueRow(position = index + 1, item = item, onOpenDestination = onOpenDestination)
        }
    }
}

@Composable
private fun QueueRow(position: Int, item: StrategyQueueItemDto, onOpenDestination: (String) -> Unit) {
    Column(modifier = Modifier.padding(top = Spacing.md)) {
        Row(verticalAlignment = Alignment.Top) {
            // The number is information: it is the order the engine decided, not decoration.
            Text(
                text = "$position",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.width(24.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = item.reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(modifier = Modifier.padding(top = Spacing.xs)) {
                    MetricChip("Impact", bandLabel(item.impact), impactColor(item.impact))
                    Spacer(Modifier.width(Spacing.sm))
                    MetricChip("Effort", bandLabel(item.effort), MaterialTheme.colorScheme.onSurfaceVariant)
                }
                // A blocked item is reported but cannot be acted on -- you can't practise your
                // way to seniority. Showing a button would be a dead end.
                if (item.status == "BLOCKED") {
                    Text(
                        text = "Worth knowing about, but not something you can change quickly.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Spacing.xs),
                    )
                } else {
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
private fun ContradictionsSection(strategy: CareerStrategyDto, onUpgrade: () -> Unit) {
    val contradictions = strategy.contradictions
    if (contradictions.isEmpty() && !strategy.isLocked("contradictions")) return

    SupportingCard {
        Text(
            text = "You have a strategy mismatch",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        if (contradictions.isEmpty()) {
            LockedNote(
                text = "Premium spots where your claims, your evidence and your behaviour disagree.",
                onUpgrade = onUpgrade,
            )
        } else {
            contradictions.forEach { ContradictionRow(it) }
        }
    }
}

@Composable
private fun ContradictionRow(item: StrategyContradictionDto) {
    Column(modifier = Modifier.padding(top = Spacing.sm).animateContentSize()) {
        // Both sides are shown. Neither is discarded -- the point is that two true things
        // disagree, not that one of them is wrong.
        Text(item.leftStatement, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        Text(
            text = "vs",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = Spacing.xs),
        )
        Text(item.rightStatement, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        Text(
            text = item.explanation,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.xs),
        )
    }
}

@Composable
private fun TargetComparisonSection(viewModel: CareerStrategyViewModel, onUpgrade: () -> Unit) {
    val state by viewModel.targets.collectAsStateWithLifecycle()

    SupportingCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.CompareArrows,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = "Compare target roles",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = Spacing.xs),
            )
        }

        when (val current = state) {
            is StrategySectionState.Idle -> SecondaryButton(
                text = "Compare",
                onClick = viewModel::loadTargets,
                modifier = Modifier.padding(top = Spacing.sm),
            )
            is StrategySectionState.Loading -> SkeletonList(count = 2, modifier = Modifier.padding(top = Spacing.sm))
            is StrategySectionState.Locked -> LockedNote(
                text = "Premium compares your strategy across several target roles at once.",
                onUpgrade = onUpgrade,
            )
            is StrategySectionState.Error -> Text(
                text = current.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = Spacing.sm),
            )
            is StrategySectionState.Loaded -> {
                val comparison = current.data
                if (comparison.locked) {
                    LockedNote(
                        text = "Premium compares your strategy across several target roles at once.",
                        onUpgrade = onUpgrade,
                    )
                } else if (comparison.targets.isEmpty()) {
                    Text(
                        text = "Add a target role to compare against.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Spacing.xs),
                    )
                } else {
                    comparison.targets.forEach { target ->
                        Column(modifier = Modifier.padding(top = Spacing.sm)) {
                            Text(
                                text = target.displayRoleName +
                                    if (target.isCurrentTarget) " (your target)" else "",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                text = target.readinessPercent?.let { "$it% ready" } ?: "Readiness not checked yet",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            target.bottleneckTitle?.let {
                                Text(
                                    text = "Biggest constraint: $it",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    comparison.tradeoffs.forEach { line ->
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = Spacing.sm),
                        )
                    }
                    // An effort estimate without its assumptions reads as a measurement.
                    if (comparison.assumptions.isNotEmpty()) {
                        ExpandableSection(
                            title = "How these estimates are worked out",
                            modifier = Modifier.padding(top = Spacing.sm),
                        ) {
                            comparison.assumptions.forEach {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = Spacing.xs),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistorySection(viewModel: CareerStrategyViewModel, onUpgrade: () -> Unit) {
    val state by viewModel.history.collectAsStateWithLifecycle()

    SupportingCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.History,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = "How your strategy has changed",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = Spacing.xs),
            )
        }

        when (val current = state) {
            is StrategySectionState.Idle -> SecondaryButton(
                text = "View history",
                onClick = viewModel::loadHistory,
                modifier = Modifier.padding(top = Spacing.sm),
            )
            is StrategySectionState.Loading -> SkeletonList(count = 2, modifier = Modifier.padding(top = Spacing.sm))
            is StrategySectionState.Locked -> LockedNote(
                text = "Premium keeps a record of every time your strategy actually moved.",
                onUpgrade = onUpgrade,
            )
            is StrategySectionState.Error -> Text(
                text = current.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = Spacing.sm),
            )
            is StrategySectionState.Loaded -> {
                val snapshots = current.data.snapshots
                if (snapshots.isEmpty()) {
                    Text(
                        text = "Nothing recorded yet. Your strategy is saved the first time it changes.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Spacing.xs),
                    )
                } else {
                    snapshots.forEach { snapshot ->
                        Column(modifier = Modifier.padding(top = Spacing.sm)) {
                            Text(
                                text = stateHeadline(snapshot.state).first,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                            )
                            // Each line comes from the backend's own diff of two recorded
                            // decisions. Nothing here claims a cause the data cannot support.
                            snapshot.changeSummary.forEach { line ->
                                Text(
                                    text = line,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = Spacing.xs),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// -- shared pieces -----------------------------------------------------------------------------

/**
 * A labelled band, never a raw number. The engine works in bands because that is the honest
 * resolution of what it knows; rendering "0.42" would claim a precision it does not have.
 */
@Composable
private fun MetricChip(label: String, value: String, tint: Color) {
    Box(
        modifier = Modifier
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(Spacing.sm),
            )
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs)
            // One description for the pair, so TalkBack reads "Impact: High" rather than two
            // disconnected fragments.
            .clearAndSetSemantics { contentDescription = "$label: $value" },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = tint,
                modifier = Modifier.padding(start = Spacing.xs),
            )
        }
    }
}

/**
 * An in-card upgrade prompt for one withheld section.
 *
 * Deliberately not PaywallGate: that composable re-checks entitlement client-side and hides its
 * content, which is the wrong shape here. The server has already decided and already withheld
 * the data -- this only explains the absence. One source of truth for entitlement, and it is
 * the backend.
 */
@Composable
private fun LockedNote(text: String, onUpgrade: () -> Unit) {
    Column(modifier = Modifier.padding(top = Spacing.sm)) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SecondaryButton(
            text = "Upgrade to Premium",
            onClick = onUpgrade,
            modifier = Modifier.padding(top = Spacing.xs),
        )
    }
}

// -- diminishing returns (D2) ------------------------------------------------------------------

/**
 * "You already have this. More of it is not your best next hour."
 *
 * Every judgement here is the server's. The backend decides whether a series has enough
 * observations to mean anything (`isSufficient`) and what it concludes (`verdict`); this
 * composable only chooses words for those values. Deciding on the client that three assessments
 * look flat would be a second analysis engine, and it would be free to disagree with the one
 * that produced the queue on the same screen.
 *
 * Two rules the wording has to keep. A series the backend could not judge says so plainly and
 * never renders a number -- a mean over two results is those two results, not a trend. And
 * nothing here says learning is wasted: the claim is about *ordering*, that this is already
 * strong enough that the next hour is worth more elsewhere.
 */
@Composable
private fun DiminishingReturnsSection(strategy: CareerStrategyDto, onUpgrade: () -> Unit) {
    val series = strategy.diminishingReturns
    val locked = strategy.isLocked("diminishing_returns")
    // Nothing to say and nothing to sell: render nothing rather than an empty card.
    if (series.isEmpty() && !locked) return

    SupportingCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.TrendingFlat,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = "Where more effort buys you less",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = Spacing.xs),
            )
        }

        if (series.isEmpty()) {
            LockedNote(
                text = "Premium shows which of your strengths have stopped improving, so you can " +
                    "spend your next session where it still counts.",
                onUpgrade = onUpgrade,
            )
        } else {
            Text(
                text = "These are strengths, not problems. They are just no longer where your next " +
                    "hour goes furthest.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xs),
            )
            series.forEach { DiminishingReturnsRow(it) }

            // The alternative, named rather than implied. "Lower priority" is only meaningful
            // against something, and the something is this student's actual bottleneck -- read
            // from the same response, not decided here.
            strategy.bottleneck?.let { bottleneck ->
                Text(
                    text = "Higher priority right now: ${bottleneck.title}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = Spacing.md),
                )
                Text(
                    text = bottleneck.recommendedAction,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
        }
    }
}

@Composable
private fun DiminishingReturnsRow(item: StrategySeriesDto) {
    Column(modifier = Modifier.padding(top = Spacing.sm)) {
        Text(
            text = item.subject,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
        )
        // The backend's own sentence for why it reached this verdict, including the case where
        // it reached none because there was not enough to look at.
        Text(
            text = item.reason,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.xs),
        )
        // Only shown once the server says the series is long enough to carry a verdict. Below
        // that, a "latest score" chip would read as a measurement of a trend that was never
        // measured.
        if (item.isSufficient && item.latest != null) {
            Row(modifier = Modifier.padding(top = Spacing.xs)) {
                MetricChip(
                    label = "Latest",
                    value = "${(item.latest * 100).toInt()}%",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(Spacing.xs))
                MetricChip(
                    label = "Observations",
                    value = item.observationCount.toString(),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// -- job-specific strategy (D1) ----------------------------------------------------------------

/** Decision -> (headline, supporting line). One table, so the four outcomes cannot be worded
 * inconsistently between here and anywhere else that renders them. None of them advises against
 * applying: that call is the student's, and adverts routinely overstate their requirements. */
internal fun jobDecisionLabel(decision: String): Pair<String, String> = when (decision) {
    "APPLY_NOW" -> "Apply now" to "Nothing is standing between you and a credible application."
    "FIX_BEFORE_APPLYING" -> "Worth fixing something first" to
        "You can apply whenever you like - this would make it land harder."
    "NOT_A_CURRENT_PRIORITY" -> "Not where your effort goes today" to
        "This one is a long way off, and closing its gaps would not move what is actually holding you back."
    "INSUFFICIENT_INFORMATION" -> "We can't judge this one yet" to
        "There isn't enough recorded to give you an honest answer about this job."
    else -> "This job" to ""
}

/**
 * Salary, with its period attached or not shown at all.
 *
 * The period is never defaulted. An amount whose period the provider did not state is rendered
 * without one, because guessing "per year" on an hourly figure would overstate it by roughly two
 * thousand times, and a student comparing offers would have no way to know.
 */
internal fun salaryLabel(conditions: JobConditionsDto): String? {
    val min = conditions.salaryMin
    val max = conditions.salaryMax
    if (min == null && max == null) return null
    val currency = conditions.salaryCurrency?.let { "$it " }.orEmpty()
    val amount = when {
        min != null && max != null && min != max -> "$currency$min-$max"
        else -> "$currency${min ?: max}"
    }
    val period = conditions.salaryPeriod?.let { " per $it" } ?: " (period not stated)"
    val predicted = if (conditions.salaryIsPredicted) " - estimated, not published by the employer" else ""
    return "$amount$period$predicted"
}

/**
 * The job-specific strategy, shown only when this screen was opened against one advert.
 *
 * Everything rendered is the server's verdict about this listing: the decision, the reasons, the
 * blockers and their order. In particular the blockers arrive already ordered with conditions
 * first, and this composable does not re-sort them -- the backend put "this is in a city you
 * ruled out" ahead of "you are missing Kafka" for a reason, and reordering on the client would
 * undo exactly the fix that ordering represents.
 *
 * A 403 renders as an upgrade prompt and a 404 as a real message, because "this job is gone" and
 * "this job has no blockers" look identical if both collapse into an empty card.
 */
@Composable
private fun JobStrategySection(viewModel: CareerStrategyViewModel, onUpgrade: () -> Unit) {
    val state by viewModel.jobStrategy.collectAsStateWithLifecycle()

    AppCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.WorkOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = "This job, against your strategy",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = Spacing.xs),
            )
        }

        when (val current = state) {
            is StrategySectionState.Idle,
            is StrategySectionState.Loading,
            -> SkeletonList(count = 2, modifier = Modifier.padding(top = Spacing.sm))

            is StrategySectionState.Locked -> LockedNote(
                text = "Premium tells you whether a specific job is worth applying to today, and " +
                    "what to settle first if it isn't.",
                onUpgrade = onUpgrade,
            )

            is StrategySectionState.Error -> Text(
                text = current.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = Spacing.sm),
            )

            is StrategySectionState.Loaded -> JobStrategyBody(current.data)
        }
    }
}

@Composable
private fun JobStrategyBody(job: JobStrategyDto) {
    val (headline, support) = jobDecisionLabel(job.decision)

    Column(modifier = Modifier.padding(top = Spacing.sm)) {
        // Which job this is about, always first. A verdict with no job attached is unreadable.
        Text(
            text = listOfNotNull(job.jobTitle, job.company).joinToString(" - "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = headline,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = Spacing.xs),
        )
        // The server's own sentence, which already names the decisive thing -- a location the
        // student ruled out outranks a missing skill here, and this renders that as written.
        Text(
            text = job.headline,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = Spacing.xs),
        )
        if (support.isNotEmpty()) {
            Text(
                text = support,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }

        JobConditionsBlock(job.conditions)

        // Rendered as stored. The status string is shown verbatim because the backend does not
        // document what its values mean, and this client does not get to decide for it.
        if (job.application.appliedAt != null || job.application.isSaved) {
            Text(
                text = buildString {
                    if (job.application.appliedAt != null) append("Applied") else append("Saved")
                    job.application.status?.let { append(" - tracker status: ").append(it) }
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }

        // Readiness, or an honest absence of it. Never a zero: the backend sends null exactly
        // when it refused to stand behind a number, and a "0%" here would invent the verdict it
        // declined to give.
        Text(
            text = job.readinessPercent?.let { "Application readiness: $it%" }
                ?: "Application readiness: not checked yet",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.sm),
        )
        job.matchRationale?.let { rationale ->
            Text(
                text = rationale,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }

        // Why the match came out the way it did, in the server's own words.
        if (job.decidingFactors.isNotEmpty()) {
            Text(
                text = "What decided this",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = Spacing.md),
            )
            job.decidingFactors.forEach { factor ->
                Text(
                    text = factor,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
        }

        job.reasons.forEach { reason ->
            Text(
                text = "- $reason",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }

        // "We don't know" kept visibly apart from "you don't have it". Merging these two lists
        // is the single most damaging thing this screen could do.
        if (job.unclearSkills.isNotEmpty()) {
            Text(
                text = "We can't tell yet whether you have: ${job.unclearSkills.joinToString(", ")}. " +
                    "Add your skills and we can answer this properly.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.sm),
            )
        }
        if (job.unknownFactors.isNotEmpty()) {
            Text(
                text = "This listing doesn't say: ${job.unknownFactors.joinToString("; ")}.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }

        if (job.blockers.isNotEmpty()) {
            Text(
                text = "What would change this",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = Spacing.md),
            )
            // Server order preserved. See JobStrategySection.
            job.blockers.forEach { blocker ->
                Column(modifier = Modifier.padding(top = Spacing.xs)) {
                    Text(blocker.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text(
                        text = blocker.detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        job.biggestGap?.let { gap ->
            Text(
                text = "Highest-value fix: ${gap.title}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = Spacing.md),
            )
            Text(
                text = gap.detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
    }
}

@Composable
private fun JobConditionsBlock(conditions: JobConditionsDto) {
    val terms = listOfNotNull(
        conditions.location,
        conditions.workMode,
        conditions.employmentType,
        salaryLabel(conditions),
    )
    if (terms.isEmpty()) return
    Text(
        text = terms.joinToString(" - "),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = Spacing.sm),
    )
}

/**
 * The way into job-specific strategy from the strategy screen itself.
 *
 * Deliberately a route into the existing tracker rather than a job picker built here. The
 * student's saved and tracked jobs already have a screen, that screen already opens Job Detail,
 * and Job Detail now carries the "what does this job mean for my strategy?" action. Rebuilding
 * a list of their jobs on this screen would be a second place that answers "which jobs am I
 * looking at", free to disagree with the first one about dismissals, staleness and ordering.
 *
 * Hidden while the job section is already on screen: offering "analyse a job" underneath an
 * analysed job reads as though the analysis did not take.
 */
@Composable
private fun TargetJobsCard(onOpenDestination: (String) -> Unit) {
    SupportingCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.WorkOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = "A specific job",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = Spacing.xs),
            )
        }
        Text(
            text = "Everything above is about your career. Open one of your saved jobs to see what " +
                "that advert in particular asks of you, and whether it is worth applying to yet.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.xs),
        )
        SecondaryButton(
            text = "Pick a job to analyse",
            onClick = { onOpenDestination("tracker") },
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
        )
    }
}
