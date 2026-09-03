package com.aiplacementos.app.ui.recruiterattack

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aiplacementos.app.data.remote.ProjectBuildListItemDto
import com.aiplacementos.app.ui.common.AppCard
import com.aiplacementos.app.ui.common.DetailTopBar
import com.aiplacementos.app.ui.common.ErrorState
import com.aiplacementos.app.ui.common.LoadingState
import com.aiplacementos.app.ui.common.PrimaryButton
import com.aiplacementos.app.ui.common.SecondaryButton
import com.aiplacementos.app.ui.common.networkModule
import com.aiplacementos.app.ui.theme.Spacing

private enum class ClaimSource { PROJECT, RESUME }

@Composable
fun RecruiterAttackCreateScreen(onBack: () -> Unit, onCreated: (sessionId: Int) -> Unit) {
    val network = networkModule()
    val viewModel: RecruiterAttackCreateViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                RecruiterAttackCreateViewModel(network.recruiterAttackRepository, network.projectBuilderRepository)
            }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var source by remember { mutableStateOf(ClaimSource.PROJECT) }
    var claimText by remember { mutableStateOf("") }

    val current = state
    LaunchedEffect(current) {
        if (current is RecruiterAttackCreateUiState.Success) onCreated(current.sessionId)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        DetailTopBar(title = "New Recruiter Attack", onBack = onBack)
        when (current) {
            is RecruiterAttackCreateUiState.Loading -> LoadingState(modifier = Modifier.fillMaxSize())
            is RecruiterAttackCreateUiState.Success -> LoadingState(modifier = Modifier.fillMaxSize())
            is RecruiterAttackCreateUiState.Error ->
                ErrorState(message = current.message, modifier = Modifier.fillMaxSize(), onRetry = viewModel::load)
            is RecruiterAttackCreateUiState.Ready -> Column(modifier = Modifier.fillMaxSize().padding(Spacing.md)) {
                Text(
                    text = "Pick one real claim -- a completed project, or a specific line from your resume -- " +
                        "and a skeptical recruiter will fire pointed follow-up questions at it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(modifier = Modifier.fillMaxWidth().padding(top = Spacing.md), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    if (source == ClaimSource.PROJECT) {
                        PrimaryButton(text = "From a Project", onClick = {}, modifier = Modifier.fillMaxWidth().weight(1f))
                    } else {
                        SecondaryButton(text = "From a Project", onClick = { source = ClaimSource.PROJECT }, modifier = Modifier.fillMaxWidth().weight(1f))
                    }
                    if (source == ClaimSource.RESUME) {
                        PrimaryButton(text = "From my Resume", onClick = {}, modifier = Modifier.fillMaxWidth().weight(1f))
                    } else {
                        SecondaryButton(text = "From my Resume", onClick = { source = ClaimSource.RESUME }, modifier = Modifier.fillMaxWidth().weight(1f))
                    }
                }

                if (current.submitError != null) {
                    Text(
                        text = current.submitError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = Spacing.sm),
                    )
                }

                when (source) {
                    ClaimSource.PROJECT -> ProjectSourcePicker(
                        builds = current.completedBuilds,
                        submitting = current.submitting,
                        onAttack = viewModel::startFromProject,
                    )
                    ClaimSource.RESUME -> ResumeClaimPicker(
                        claimText = claimText,
                        onClaimTextChange = { claimText = it },
                        submitting = current.submitting,
                        onAttack = { viewModel.startFromResumeClaim(claimText) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ProjectSourcePicker(
    builds: List<ProjectBuildListItemDto>,
    submitting: Boolean,
    onAttack: (buildId: Int) -> Unit,
) {
    if (builds.isEmpty()) {
        Text(
            text = "Finish a project in Project Builder first -- only completed projects can be attacked.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = Spacing.lg),
        )
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(top = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        items(builds, key = { it.id }) { build ->
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Text(text = build.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                build.targetRoleName?.let {
                    Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                PrimaryButton(
                    text = if (submitting) "Starting..." else "Attack This Project",
                    onClick = { onAttack(build.id) },
                    enabled = !submitting,
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
                )
            }
        }
    }
}

@Composable
private fun ResumeClaimPicker(
    claimText: String,
    onClaimTextChange: (String) -> Unit,
    submitting: Boolean,
    onAttack: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = Spacing.md)) {
        Text(
            text = "Paste one specific line from your uploaded resume (a bullet point, a skill, an achievement).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = claimText,
            onValueChange = onClaimTextChange,
            placeholder = { Text("e.g. Led a team of 5 to ship a fraud-detection pipeline") },
            enabled = !submitting,
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
        )
        PrimaryButton(
            text = if (submitting) "Starting..." else "Attack This Claim",
            onClick = onAttack,
            enabled = !submitting && claimText.isNotBlank(),
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.md),
        )
    }
}
