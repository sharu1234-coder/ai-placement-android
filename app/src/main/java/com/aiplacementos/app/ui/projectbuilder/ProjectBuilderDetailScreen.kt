package com.aiplacementos.app.ui.projectbuilder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aiplacementos.app.data.remote.GitHubEvidenceDto
import com.aiplacementos.app.data.remote.ProjectBuildDto
import com.aiplacementos.app.data.remote.ProjectBuildStageDto
import com.aiplacementos.app.ui.common.AppCard
import com.aiplacementos.app.ui.common.DetailTopBar
import com.aiplacementos.app.ui.common.ErrorState
import com.aiplacementos.app.ui.common.LoadingState
import com.aiplacementos.app.ui.common.PrimaryButton
import com.aiplacementos.app.ui.common.SkillBar
import com.aiplacementos.app.ui.common.networkModule
import com.aiplacementos.app.ui.theme.Spacing

@Composable
fun ProjectBuilderDetailScreen(buildId: Int, onBack: () -> Unit, onViewProof: (buildId: Int) -> Unit) {
    val network = networkModule()
    val viewModel: ProjectBuildDetailViewModel = viewModel(
        factory = viewModelFactory { initializer { ProjectBuildDetailViewModel(buildId, network.projectBuilderRepository) } },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val actionMessage by viewModel.actionMessage.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        DetailTopBar(title = "Project", onBack = onBack)
        when (val current = state) {
            is ProjectBuildDetailUiState.Loading -> LoadingState(modifier = Modifier.fillMaxSize())
            is ProjectBuildDetailUiState.Error -> ErrorState(
                message = current.message,
                icon = if (current.isNetworkError) Icons.Filled.WifiOff else Icons.Filled.ErrorOutline,
                modifier = Modifier.fillMaxSize(),
                onRetry = viewModel::load,
            )
            is ProjectBuildDetailUiState.Success -> ProjectBuildContent(
                build = current.build,
                actionMessage = actionMessage,
                onDismissActionMessage = viewModel::clearActionMessage,
                onStart = viewModel::startStage,
                onComplete = viewModel::completeStage,
                onViewProof = { onViewProof(buildId) },
                onCheckGithub = viewModel::checkGithub,
            )
        }
    }
}

@Composable
private fun ProjectBuildContent(
    build: ProjectBuildDto,
    actionMessage: String?,
    onDismissActionMessage: () -> Unit,
    onStart: (String) -> Unit,
    onComplete: (String, String) -> Unit,
    onViewProof: () -> Unit,
    onCheckGithub: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item {
            Column {
                Text(text = build.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                build.targetRoleName?.let {
                    Text(text = "For: $it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                build.stackSummary?.let {
                    Text(text = "Stack: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (build.stages.isNotEmpty()) {
            item { OverallProgressCard(build) }
        }
        item { GitHubCheckCard(evidence = build.githubEvidence, onCheck = onCheckGithub) }
        if (actionMessage != null) {
            item {
                AppCard(modifier = Modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.errorContainer) {
                    Text(text = actionMessage, color = MaterialTheme.colorScheme.onErrorContainer)
                    PrimaryButton(text = "Dismiss", onClick = onDismissActionMessage, modifier = Modifier.padding(top = Spacing.xs))
                }
            }
        }
        val firstUnlockedIndex = build.stages.indexOfFirst { it.status != "completed" }
        itemsIndexed(build.stages, key = { _, s -> s.stageKey }) { index, stage ->
            val isLocked = stage.status == "not_started" && index != firstUnlockedIndex
            ProjectBuildStageCard(
                stage = stage,
                isLocked = isLocked,
                onStart = { onStart(stage.stageKey) },
                onComplete = { notes -> onComplete(stage.stageKey, notes) },
            )
        }
        if (build.status == "completed") {
            item {
                PrimaryButton(text = "View Proof", onClick = onViewProof, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

private fun statusLabel(status: String): String = when (status) {
    "completed" -> "Completed"
    "in_progress" -> "In Progress"
    else -> "Not Started"
}

/** A real count over build.stages -- never a separately-tracked or estimated percentage, so
 * it can't drift from the actual stage statuses rendered below it. */
@Composable
private fun OverallProgressCard(build: ProjectBuildDto) {
    val completedCount = build.stages.count { it.status == "completed" }
    val percent = (completedCount * 100) / build.stages.size
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(text = statusLabel(build.status), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text = "$completedCount / ${build.stages.size} stages", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SkillBar(label = "Overall Progress", percent = percent, modifier = Modifier.padding(top = Spacing.xs))
    }
}

/** Unauthenticated, public-repos-only -- see github_verification_service's docstring on the
 * backend. A result is real but LIMITED evidence (a public repo exists, has a README, its
 * language matches the declared stack), never a claim the student wrote the code or that it's
 * "verified" -- evidenceLabel is always rendered verbatim, never paraphrased into a stronger
 * claim than the backend actually made. */
@Composable
private fun GitHubCheckCard(evidence: GitHubEvidenceDto?, onCheck: (String) -> Unit) {
    var repoUrl by rememberSaveable { mutableStateOf(evidence?.repoUrl.orEmpty()) }

    AppCard(modifier = Modifier.fillMaxWidth()) {
        Text(text = "GitHub Verification", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            text = "Link a public GitHub repo for this project -- checked against GitHub's public API only, no sign-in required.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.xs),
        )
        OutlinedTextField(
            value = repoUrl,
            onValueChange = { repoUrl = it },
            placeholder = { Text("https://github.com/owner/repo") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
        )
        PrimaryButton(
            text = "Check Repository",
            onClick = { onCheck(repoUrl) },
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
        )
        if (evidence != null) {
            Text(
                text = evidence.evidenceLabel,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = Spacing.sm),
            )
            if (evidence.repoExists) {
                evidence.primaryLanguage?.let {
                    Text(text = "Primary language: $it", style = MaterialTheme.typography.bodySmall)
                }
                Text(text = "Stars: ${evidence.stars}", style = MaterialTheme.typography.bodySmall)
                evidence.matchesDeclaredStack?.let { matches ->
                    Text(
                        text = if (matches) "Matches your declared stack" else "Doesn't match your declared stack",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProjectBuildStageCard(
    stage: ProjectBuildStageDto,
    isLocked: Boolean,
    onStart: () -> Unit,
    onComplete: (notes: String) -> Unit,
) {
    var notes by remember(stage.stageKey) { mutableStateOf(stage.notes.orEmpty()) }

    AppCard(modifier = Modifier.fillMaxWidth()) {
        val statusLabel = when {
            stage.status == "completed" -> "Completed"
            isLocked -> "Locked"
            stage.status == "in_progress" -> "In progress"
            else -> "Not started"
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.semantics(mergeDescendants = true) {
                contentDescription = "${stage.title}, $statusLabel"
            },
        ) {
            val icon = when {
                stage.status == "completed" -> Icons.Filled.CheckCircle
                isLocked -> Icons.Filled.Lock
                stage.status == "in_progress" -> Icons.Filled.PlayCircle
                else -> Icons.Filled.RadioButtonUnchecked
            }
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (stage.status == "completed") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(Spacing.sm))
            Text(text = stage.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }

        if (!isLocked) {
            Text(
                text = stage.guidanceText,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = Spacing.sm),
            )

            when (stage.status) {
                "not_started" -> PrimaryButton(text = "Start Stage", onClick = onStart, modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm))
                "in_progress" -> {
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Your notes (optional)") },
                        modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
                    )
                    PrimaryButton(
                        text = "Complete Stage",
                        onClick = { onComplete(notes) },
                        modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
                    )
                }
                "completed" -> if (stage.notes != null) {
                    Text(
                        text = "Notes: ${stage.notes}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Spacing.sm),
                    )
                }
            }
        }
    }
}
