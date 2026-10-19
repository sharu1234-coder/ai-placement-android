package com.aiplacementos.app.ui.roadmap

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aiplacementos.app.data.remote.RoadmapDto
import com.aiplacementos.app.data.remote.RoadmapTaskDto
import com.aiplacementos.app.ui.common.AppCard
import com.aiplacementos.app.ui.common.ErrorState
import com.aiplacementos.app.ui.common.HeroCard
import com.aiplacementos.app.ui.common.PrimaryButton
import com.aiplacementos.app.ui.common.RouteIllustration
import com.aiplacementos.app.ui.common.SecondaryButton
import com.aiplacementos.app.ui.common.SectionHeader
import com.aiplacementos.app.ui.common.SkeletonList
import com.aiplacementos.app.ui.common.networkModule
import com.aiplacementos.app.ui.theme.Spacing
import com.aiplacementos.app.ui.theme.accentCyanColor
import kotlin.math.roundToInt

/**
 * jdId=0 (bottom-nav "Roadmap" tab) never auto-generates -- a blind tab visit shouldn't spend
 * an AI call unprompted. A jdId from the Prepare Me flow (autoGenerateIfMissing=true) DOES
 * auto-generate, since that screen's whole purpose is showing the roadmap the flow just
 * produced weakness data for.
 */
@Composable
fun RoadmapScreen(jdId: Int = 0, autoGenerateIfMissing: Boolean = false, onOpenTask: (taskId: Int) -> Unit) {
    val network = networkModule()
    val viewModel: RoadmapViewModel = viewModel(
        factory = viewModelFactory {
            initializer { RoadmapViewModel(jdId, autoGenerateIfMissing, network.roadmapRepository) }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    when (val current = state) {
        is RoadmapUiState.Loading -> SkeletonList(count = 4, modifier = Modifier.fillMaxSize().padding(Spacing.md))
        is RoadmapUiState.Error -> ErrorState(
            message = current.message,
            onRetry = viewModel::load,
            modifier = Modifier.fillMaxSize(),
        )
        is RoadmapUiState.NoRoadmap -> NoRoadmapPrompt(onGenerate = { viewModel.generate() })
        is RoadmapUiState.Success -> RoadmapContent(
            roadmap = current.roadmap,
            isBusy = current.isBusy,
            onOpenTask = onOpenTask,
            onToggleDone = { task ->
                viewModel.updateTaskStatus(task.id, if (task.status == "done") "pending" else "done")
            },
            onReassess = { viewModel.reassess() },
        )
    }
}

@Composable
private fun NoRoadmapPrompt(onGenerate: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        RouteIllustration(
            modifier = Modifier.size(width = 200.dp, height = 140.dp),
            track = accentCyanColor().copy(alpha = 0.14f),
            line = accentCyanColor(),
            accent = MaterialTheme.colorScheme.tertiary,
            ground = MaterialTheme.colorScheme.background,
        )
        Spacer(Modifier.height(Spacing.lg))
        Text(
            text = "No practice plan yet",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = "Once you have completed a test or an interview, we turn the weak spots " +
                "it found into a day-by-day practice schedule.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Spacing.sm),
        )
        PrimaryButton(
            text = "Generate my plan",
            onClick = onGenerate,
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.lg),
        )
    }
}

@Composable
private fun RoadmapContent(
    roadmap: RoadmapDto,
    isBusy: Boolean,
    onOpenTask: (taskId: Int) -> Unit,
    onToggleDone: (RoadmapTaskDto) -> Unit,
    onReassess: () -> Unit,
) {
    val doneCount = roadmap.tasks.count { it.status == "done" }
    val progressPercent = if (roadmap.tasks.isNotEmpty()) {
        ((doneCount.toDouble() / roadmap.tasks.size) * 100).roundToInt()
    } else {
        0
    }
    val tasksByDay = roadmap.tasks.groupBy { it.dayNumber }.toSortedMap()

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            item {
                Text(
                    text = "Your ${roadmap.durationDays}-day plan",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.semantics { heading() },
                )
            }
            item {
                HeroCard(modifier = Modifier.fillMaxWidth(), contentPadding = Spacing.md) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "$doneCount of ${roadmap.tasks.size} tasks done",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "$progressPercent%",
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
                            .semantics { contentDescription = "Progress: $progressPercent per cent" },
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progressPercent.coerceIn(0, 100) / 100f)
                                .fillMaxHeight()
                                .clip(MaterialTheme.shapes.extraSmall)
                                .background(MaterialTheme.colorScheme.onPrimary),
                        )
                    }
                }
            }

            tasksByDay.forEach { (day, tasks) ->
                item {
                    val doneToday = tasks.count { it.status == "done" }
                    SectionHeader(
                        title = "Day $day",
                        supporting = "$doneToday of ${tasks.size} done",
                        modifier = Modifier.padding(top = Spacing.md, bottom = Spacing.xs),
                    )
                }
                items(tasks, key = { it.id }) { task ->
                    TaskCard(task = task, onOpen = { onOpenTask(task.id) }, onToggleDone = { onToggleDone(task) })
                }
            }
        }

        // A footer, not an overlay: the last task of the last day stays reachable.
        Surface(color = MaterialTheme.colorScheme.background) {
            Column {
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                SecondaryButton(
                    text = "Reassess plan",
                    loading = isBusy,
                    onClick = onReassess,
                    modifier = Modifier.fillMaxWidth().padding(Spacing.md),
                )
            }
        }
    }
}

@Composable
private fun TaskCard(task: RoadmapTaskDto, onOpen: () -> Unit, onToggleDone: () -> Unit) {
    val done = task.status == "done"
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Checkbox(
                checked = done,
                onCheckedChange = { onToggleDone() },
                modifier = Modifier.semantics { contentDescription = "Mark \"${task.title}\" as done" },
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = Spacing.xs)
                    .clickable(onClick = onOpen),
            ) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (done) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    textDecoration = if (done) TextDecoration.LineThrough else null,
                )
                Text(
                    text = "${task.taskType.replaceFirstChar { it.uppercase() }} · ${task.skillTag}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
