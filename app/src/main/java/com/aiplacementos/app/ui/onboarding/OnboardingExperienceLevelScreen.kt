package com.aiplacementos.app.ui.onboarding

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aiplacementos.app.data.remote.ProfileDto
import com.aiplacementos.app.ui.common.AppCard
import com.aiplacementos.app.ui.common.ErrorState
import com.aiplacementos.app.ui.common.LoadingState
import com.aiplacementos.app.ui.common.PrimaryButton
import com.aiplacementos.app.ui.common.StepProgressIndicator
import com.aiplacementos.app.ui.common.UiState
import com.aiplacementos.app.ui.common.networkModule
import com.aiplacementos.app.ui.theme.Spacing

// internal (not private) -- reused by ProfileScreen's Experience Level section.
internal val LEVELS = listOf(
    "beginner" to "Just getting started",
    "intermediate" to "Some experience",
    "advanced" to "Very comfortable",
)

@Composable
fun OnboardingExperienceLevelScreen(onNext: () -> Unit) {
    val network = networkModule()
    val viewModel: OnboardingExperienceLevelViewModel = viewModel(
        factory = viewModelFactory { initializer { OnboardingExperienceLevelViewModel(network.profileRepository) } },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val saving by viewModel.saving.collectAsStateWithLifecycle()
    var selectedLevel by remember { mutableStateOf<String?>(null) }
    var prefilled by remember { mutableStateOf(false) }

    LaunchedEffect(state) {
        val profile = (state as? UiState.Success<ProfileDto>)?.data
        if (profile != null && !prefilled) {
            selectedLevel = profile.experienceLevel
            prefilled = true
        }
    }

    when (val current = state) {
        is UiState.Loading -> LoadingState(modifier = Modifier.fillMaxSize())
        is UiState.Error -> ErrorState(
            message = current.message,
            onRetry = viewModel::load,
            modifier = Modifier.fillMaxSize(),
        )
        is UiState.Empty -> LoadingState(modifier = Modifier.fillMaxSize())
        is UiState.Success<ProfileDto> -> Column(
            modifier = Modifier.fillMaxSize().padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            StepProgressIndicator(step = 7, total = 7)
            Text(text = "What is your preparation level?", style = MaterialTheme.typography.headlineSmall)
            LEVELS.forEach { (value, description) ->
                val isSelected = selectedLevel == value
                AppCard(
                    containerColor = if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                    modifier = Modifier.fillMaxWidth().clickable { selectedLevel = value },
                ) {
                    Text(text = value.replaceFirstChar(Char::uppercase), style = MaterialTheme.typography.titleMedium)
                    Text(text = description, style = MaterialTheme.typography.bodyMedium)
                }
            }
            PrimaryButton(
                text = if (saving) "Saving..." else "Next",
                onClick = { selectedLevel?.let { viewModel.save(it, onSaved = onNext) } },
                enabled = !saving && selectedLevel != null,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
