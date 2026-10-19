package com.aiplacementos.app.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aiplacementos.app.data.remote.SkillDto
import com.aiplacementos.app.ui.common.ErrorState
import com.aiplacementos.app.ui.common.LoadingState
import com.aiplacementos.app.ui.common.PrimaryButton
import com.aiplacementos.app.ui.common.SkillPicker
import com.aiplacementos.app.ui.common.StepProgressIndicator
import com.aiplacementos.app.ui.common.UiState
import com.aiplacementos.app.ui.common.networkModule
import com.aiplacementos.app.ui.theme.Spacing

@Composable
fun OnboardingSkillsScreen(onNext: () -> Unit) {
    val network = networkModule()
    val viewModel: OnboardingSkillsViewModel = viewModel(
        factory = viewModelFactory { initializer { OnboardingSkillsViewModel(network.profileRepository) } },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()

    when (val current = state) {
        is UiState.Loading -> LoadingState(modifier = Modifier.fillMaxSize())
        is UiState.Error -> ErrorState(
            message = current.message,
            onRetry = viewModel::load,
            modifier = Modifier.fillMaxSize(),
        )
        is UiState.Empty -> LoadingState(modifier = Modifier.fillMaxSize())
        is UiState.Success<List<SkillDto>> -> Column(
            modifier = Modifier.fillMaxSize().padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            OnboardingHeader(
                step = 4,
                total = 7,
                title = "Select your skills",
                subtitle = "Pick what you already know. Your roadmap is built around the gaps, not the things you can already do.",
            )
            SkillPicker(
                skills = current.data,
                busy = busy,
                onToggle = viewModel::toggleSkill,
                // SkillPicker no longer scrolls itself (see its doc comment); here it has a
                // bounded height from weight(), so the scrolling is supplied at this call site.
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            )
            PrimaryButton(
                text = "Next",
                onClick = onNext,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
