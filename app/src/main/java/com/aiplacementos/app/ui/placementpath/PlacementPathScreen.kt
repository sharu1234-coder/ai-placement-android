package com.aiplacementos.app.ui.placementpath

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aiplacementos.app.data.remote.NextBestActionDto
import com.aiplacementos.app.data.remote.PlacementPathDto
import com.aiplacementos.app.data.remote.PlacementPathTopicDto
import com.aiplacementos.app.data.remote.progressPercent
import com.aiplacementos.app.ui.common.AccentCard
import com.aiplacementos.app.ui.common.AppCard
import com.aiplacementos.app.ui.common.Benefit
import com.aiplacementos.app.ui.common.BenefitRow
import com.aiplacementos.app.ui.common.ErrorState
import com.aiplacementos.app.ui.common.FilterChipRow
import com.aiplacementos.app.ui.common.HeroCard
import com.aiplacementos.app.ui.common.LoadingState
import com.aiplacementos.app.ui.common.PrimaryButton
import com.aiplacementos.app.ui.common.ScreenHeader
import com.aiplacementos.app.ui.common.SectionHeader
import com.aiplacementos.app.ui.common.SkeletonList
import com.aiplacementos.app.ui.common.StatusPill
import com.aiplacementos.app.ui.common.SummitIllustration
import com.aiplacementos.app.ui.common.networkModule
import com.aiplacementos.app.ui.theme.Elevation
import com.aiplacementos.app.ui.theme.Spacing
import com.aiplacementos.app.ui.theme.successColor
import com.aiplacementos.app.ui.theme.warningColor

internal val STAGE_LABELS = mapOf(
    "foundation" to "Foundation",
    "core_technical" to "Core Technical Skills",
    "role_specific" to "Role-Specific Preparation",
    "practice_assessments" to "Practice & Assessments",
    "interview_prep" to "Interview Preparation",
    "final_readiness" to "Final Placement Readiness",
)

@Composable
fun PlacementPathScreen(onOpenTopic: (topicId: Int) -> Unit) {
    val network = networkModule()
    val viewModel: PlacementPathViewModel = viewModel(
        factory = viewModelFactory {
            initializer { PlacementPathViewModel(network.placementPathRepository) }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    when (val current = state) {
        is PlacementPathUiState.Loading -> SkeletonList(count = 3, modifier = Modifier.fillMaxSize().padding(Spacing.md))
        // Generation is an AI call and takes far longer than a fetch. A skeleton of three
        // cards implies content is already on its way; this says what is actually happening.
        is PlacementPathUiState.Generating -> LoadingState(
            modifier = Modifier.fillMaxSize(),
            label = "Building your roadmap from your skills...",
        )
        is PlacementPathUiState.Error -> ErrorState(
            message = current.message,
            icon = if (current.isNetworkError) Icons.Filled.WifiOff else Icons.Filled.ErrorOutline,
            onRetry = viewModel::load,
            modifier = Modifier.fillMaxSize(),
        )
        is PlacementPathUiState.NoPath -> NoPathPrompt(onGenerate = { viewModel.generate() })
        is PlacementPathUiState.Success -> PlacementPathContent(
            path = current.path,
            isBusy = current.isBusy,
            onOpenTopic = onOpenTopic,
            onRegenerate = { viewModel.generate() },
        )
    }
}

/**
 * The empty state.
 *
 * It was a centred sentence and a button on an otherwise blank screen -- the single clearest
 * example of the product looking unfinished, because it is also the first thing a new student
 * sees on this tab. What it needed was not decoration but an explanation: a roadmap is
 * generated from the resume they have already uploaded, which is not obvious from the word
 * "Generate".
 */
@Composable
private fun NoPathPrompt(onGenerate: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            // The benefits row plus the illustration card overflow a short screen once the
            // system font scale is raised, and an empty state that cannot reach its own
            // button is worse than a plain one.
            .verticalScroll(rememberScrollState())
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        ScreenHeader(
            title = "Your Learning Roadmap",
            subtitle = "A personalised plan to help you close your weak spots and reach your target role.",
        )

        AppCard(
            modifier = Modifier.fillMaxWidth(),
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            elevation = Elevation.level0,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                SummitIllustration(
                    modifier = Modifier.size(width = 190.dp, height = 130.dp),
                    track = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                    line = MaterialTheme.colorScheme.onPrimaryContainer,
                    accent = MaterialTheme.colorScheme.tertiary,
                )
                Spacer(Modifier.height(Spacing.md))
                Text(
                    text = "No roadmap yet",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = "We read the skills on your resume, compare them with your target " +
                        "role, and lay out the topics to cover in order -- foundations first, " +
                        "interview preparation last.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = Spacing.sm),
                )
                PrimaryButton(
                    text = "Generate my roadmap",
                    onClick = onGenerate,
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.md),
                )
                Text(
                    text = "You need a resume on your profile first.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = Spacing.sm),
                )
            }
        }

        // What the tap actually produces. The previous version stated a fact -- "No roadmap
        // yet" -- and then asked for a tap without ever saying what it gets you.
        BenefitRow(
            benefits = listOf(
                Benefit(Icons.Filled.Route, "Personalised learning path"),
                Benefit(Icons.Filled.TrackChanges, "Focus on your weak areas"),
                Benefit(Icons.Filled.Insights, "Track your progress"),
            ),
        )
    }
}

@Composable
private fun PlacementPathContent(
    path: PlacementPathDto,
    isBusy: Boolean,
    onOpenTopic: (topicId: Int) -> Unit,
    onRegenerate: () -> Unit,
) {
    val progressPercent = path.progressPercent()
    // "All topics" plus the stages this path actually has -- never the full STAGE_LABELS
    // catalogue, which would offer a student a filter that matches nothing.
    val stageNames = remember(path) {
        listOf(ALL_TOPICS) + path.stages.map { STAGE_LABELS[it.stage] ?: it.stage }
    }
    var stageFilter by rememberSaveable(path.id) { mutableStateOf(ALL_TOPICS) }
    val visibleStages = remember(path, stageFilter) {
        path.stages.filter { stageFilter == ALL_TOPICS || (STAGE_LABELS[it.stage] ?: it.stage) == stageFilter }
    }
    // A locked topic names the topic that unlocks it, where the payload says which one that
    // is. "Locked" with no reason is the kind of dead end a student cannot act on.
    val titlesById = remember(path) { path.stages.flatMap { it.topics }.associate { it.id to it.title } }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            item {
                Text(
                    text = "Your Placement Roadmap",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.semantics { heading() },
                )
            }

            item {
                RoadmapOverview(
                    completed = path.completedTopics,
                    total = path.totalTopics,
                    percent = progressPercent,
                )
            }

            path.nextBestAction?.let { action ->
                item { NextStepCard(action = action, onOpen = { onOpenTopic(action.topicId) }) }
            }

            if (stageNames.size > 2) {
                item {
                    FilterChipRow(
                        options = stageNames,
                        selected = stageFilter,
                        onSelect = { stageFilter = it },
                        modifier = Modifier.padding(top = Spacing.xs),
                    )
                }
            }

            visibleStages.forEach { stage ->
                item {
                    SectionHeader(
                        title = STAGE_LABELS[stage.stage] ?: stage.stage,
                        modifier = Modifier.padding(top = Spacing.md, bottom = Spacing.xs),
                    )
                }
                itemsIndexed(stage.topics) { index, topic ->
                    TopicRow(
                        topic = topic,
                        isFirst = index == 0,
                        isLast = index == stage.topics.lastIndex,
                        prerequisiteTitle = topic.prerequisiteTopicId?.let { titlesById[it] },
                        onOpen = { onOpenTopic(topic.id) },
                    )
                }
            }
        }

        // A footer, not an overlay: the list ends above it rather than scrolling underneath,
        // so the last topic in the last stage is always reachable.
        Surface(color = MaterialTheme.colorScheme.background) {
            Column {
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                PrimaryButton(
                    text = "Regenerate Roadmap",
                    loading = isBusy,
                    onClick = onRegenerate,
                    modifier = Modifier.fillMaxWidth().padding(Spacing.md),
                )
            }
        }
    }
}

/** Completed out of total, as a figure and as a bar. Compact on purpose -- a tall banner here
 * pushes the first topic below the fold, which is the content the screen exists for. */
@Composable
private fun RoadmapOverview(completed: Int, total: Int, percent: Int) {
    HeroCard(modifier = Modifier.fillMaxWidth(), contentPadding = Spacing.md) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "$completed of $total topics complete",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "$percent%",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Spacing.sm)
                .height(8.dp)
                .clip(MaterialTheme.shapes.extraSmall)
                .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.25f))
                .semantics { contentDescription = "Overall progress: $percent per cent" },
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(percent.coerceIn(0, 100) / 100f)
                    .fillMaxHeight()
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(MaterialTheme.colorScheme.onPrimary),
            )
        }
    }
}

@Composable
private fun NextStepCard(action: NextBestActionDto, onOpen: () -> Unit) {
    AccentCard(
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs).clickable(onClick = onOpen),
        accent = MaterialTheme.colorScheme.secondary,
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Text(
            text = "YOUR NEXT STEP",
            style = MaterialTheme.typography.labelSmall,
            letterSpacing = 1.sp,
            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f),
        )
        Text(
            text = action.title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(top = Spacing.xs),
        )
        Text(
            text = action.reason,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f),
            modifier = Modifier.padding(top = Spacing.xs),
        )
    }
}

/**
 * One topic, on the timeline.
 *
 * The status used to be a leading glyph inside the title string -- a tick, an arrow, or a
 * padlock emoji concatenated onto the text. That put status into the same string a screen
 * reader reads as the topic's name, and rendered the padlock as a full-colour emoji in the
 * middle of a typographic interface. It is now a node on a connecting line, with a word for
 * the state, so the sequence of topics reads as a sequence.
 */
@Composable
private fun TopicRow(
    topic: PlacementPathTopicDto,
    isFirst: Boolean,
    isLast: Boolean,
    prerequisiteTitle: String?,
    onOpen: () -> Unit,
) {
    val locked = topic.status == "locked"
    val state = topicState(topic.status)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // The connector needs the row's measured height before it can be drawn, and an
            // unbounded Row would give the gutter zero height.
            .height(IntrinsicSize.Min)
            .semantics(mergeDescendants = true) {
                contentDescription = "${topic.title}. ${state.label}. Priority ${topic.priority}."
            },
    ) {
        TimelineConnector(
            icon = state.icon,
            color = state.color,
            filled = topic.status == "completed" || topic.status == "in_progress",
            isFirst = isFirst,
            isLast = isLast,
        )
        AppCard(
            modifier = Modifier
                .weight(1f)
                .clickable(enabled = !locked, onClick = onOpen),
            // A locked topic recedes rather than disappearing: the student can still see what
            // is ahead of them, which is most of what a roadmap is for.
            elevation = if (locked) Elevation.level0 else Elevation.level1,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = topic.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (locked) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                    Text(
                        text = topic.skillTag,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (locked && prerequisiteTitle != null) {
                        Text(
                            text = "Unlocks after $prerequisiteTitle",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
                Spacer(Modifier.width(Spacing.sm))
                Column(horizontalAlignment = Alignment.End) {
                    PriorityBadge(priority = topic.priority)
                    Text(
                        text = state.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                if (!locked) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

/** The vertical line plus this topic's node. [isFirst]/[isLast] trim the line so a stage reads
 * as a run of connected topics rather than one line through the whole screen. */
@Composable
private fun TimelineConnector(
    icon: ImageVector,
    color: Color,
    filled: Boolean,
    isFirst: Boolean,
    isLast: Boolean,
) {
    val lineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    Box(modifier = Modifier.width(TimelineGutter).fillMaxHeight()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val x = size.width / 2f
            val centre = NodeCentre.toPx()
            val gap = NodeSize.toPx() / 2f + 2.dp.toPx()
            val stroke = 2.dp.toPx()
            if (!isFirst) {
                drawLine(lineColor, Offset(x, 0f), Offset(x, centre - gap), stroke, StrokeCap.Round)
            }
            if (!isLast) {
                drawLine(lineColor, Offset(x, centre + gap), Offset(x, size.height), stroke, StrokeCap.Round)
            }
        }
        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = NodeCentre - NodeSize / 2)
                .size(NodeSize),
            shape = CircleShape,
            color = if (filled) color else MaterialTheme.colorScheme.background,
            border = if (filled) null else BorderStroke(1.5.dp, color),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (filled) MaterialTheme.colorScheme.onPrimary else color,
                    modifier = Modifier.size(13.dp),
                )
            }
        }
    }
}

private val TimelineGutter = 28.dp
private val NodeSize = 22.dp

/** Where the node sits from the top of the row -- level with the topic's title, which is one
 * card padding plus roughly half a line of `titleSmall`. */
private val NodeCentre = 24.dp

private data class TopicState(val label: String, val icon: ImageVector, val color: Color)

@Composable
private fun topicState(status: String): TopicState = when (status) {
    "completed" -> TopicState("Completed", Icons.Filled.Check, successColor())
    "in_progress" -> TopicState("In progress", Icons.Filled.PlayArrow, MaterialTheme.colorScheme.secondary)
    "locked" -> TopicState("Locked", Icons.Filled.Lock, MaterialTheme.colorScheme.onSurfaceVariant)
    else -> TopicState("Not started", Icons.Filled.RadioButtonUnchecked, MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Priority as a word on a tinted ground: never colour alone, same rule as everywhere else. */
@Composable
private fun PriorityBadge(priority: String) {
    val (label, color) = when (priority) {
        "high" -> "HIGH" to MaterialTheme.colorScheme.error
        "medium" -> "MEDIUM" to warningColor()
        else -> "LOW" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    StatusPill(label = label, color = color)
}

/** The unfiltered option. Named once so the filter and the predicate cannot drift apart. */
private const val ALL_TOPICS = "All topics"
