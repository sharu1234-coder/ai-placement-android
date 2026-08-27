package com.aiplacementos.app.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aiplacementos.app.ui.common.AppCard
import com.aiplacementos.app.ui.common.ErrorState
import com.aiplacementos.app.ui.common.LoadingState
import com.aiplacementos.app.ui.common.PrimaryButton
import com.aiplacementos.app.ui.common.UiState
import com.aiplacementos.app.ui.common.networkModule
import com.aiplacementos.app.ui.theme.Spacing

@Composable
fun OnboardingConfirmationScreen(
    onEditName: () -> Unit,
    onEditEducation: () -> Unit,
    onEditResume: () -> Unit,
    onEditSkills: () -> Unit,
    onEditRole: () -> Unit,
    onEditGoals: () -> Unit,
    onEditExperienceLevel: () -> Unit,
    onRoadmapCreated: () -> Unit,
) {
    val network = networkModule()
    val viewModel: OnboardingConfirmationViewModel = viewModel(
        factory = viewModelFactory {
            initializer { OnboardingConfirmationViewModel(network.profileRepository, network.placementPathRepository) }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val creating by viewModel.creating.collectAsStateWithLifecycle()

    when (val current = state) {
        is UiState.Loading -> LoadingState(modifier = Modifier.fillMaxSize())
        is UiState.Error -> ErrorState(
            message = current.message,
            onRetry = viewModel::load,
            modifier = Modifier.fillMaxSize(),
        )
        is UiState.Empty -> LoadingState(modifier = Modifier.fillMaxSize())
        is UiState.Success<OnboardingSummary> -> {
            val summary = current.data
            val profile = summary.profile
            val roleName = profile.customTargetRole
                ?: summary.roles.find { it.id == profile.targetRoleId }?.name
                ?: "Not selected"

            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                item { Text(text = "Review your details", style = MaterialTheme.typography.headlineSmall) }
                item {
                    SummaryRow("Name", profile.fullName ?: "Not set", onEditName)
                }
                item {
                    SummaryRow(
                        "Education",
                        listOfNotNull(profile.degree, profile.branch, profile.college).joinToString(" • ")
                            .ifBlank { "Not set" },
                        onEditEducation,
                    )
                }
                item { SummaryRow("Resume", "Uploaded", onEditResume) }
                item {
                    SummaryRow(
                        "Skills",
                        if (summary.skills.isEmpty()) "None selected" else "${summary.skills.size} selected",
                        onEditSkills,
                    )
                }
                item { SummaryRow("Target role", roleName, onEditRole) }
                item {
                    SummaryRow(
                        "Goals",
                        profile.preparationGoals?.takeIf { it.isNotEmpty() }?.size?.let { "$it goals selected" }
                            ?: "None selected",
                        onEditGoals,
                    )
                }
                item {
                    SummaryRow(
                        "Preparation level",
                        profile.experienceLevel?.replaceFirstChar(Char::uppercase) ?: "Not set",
                        onEditExperienceLevel,
                    )
                }
                if (creating) {
                    item {
                        Text(
                            text = "Building your personalized roadmap -- this can take a couple of minutes.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = Spacing.md),
                        )
                    }
                }
                item {
                    PrimaryButton(
                        text = if (creating) "Creating..." else "Create My Roadmap",
                        onClick = { viewModel.createRoadmap(onDone = onRoadmapCreated) },
                        enabled = !creating,
                        modifier = Modifier.fillMaxWidth().padding(top = Spacing.md),
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String, onEdit: () -> Unit) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = label, style = MaterialTheme.typography.labelMedium)
                Text(text = value, style = MaterialTheme.typography.bodyLarge)
            }
            TextButton(onClick = onEdit) { Text("Edit") }
        }
    }
}
