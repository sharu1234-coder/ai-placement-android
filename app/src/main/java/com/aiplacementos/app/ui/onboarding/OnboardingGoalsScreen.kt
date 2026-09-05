package com.aiplacementos.app.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aiplacementos.app.data.remote.ProfileDto
import com.aiplacementos.app.ui.common.ErrorState
import com.aiplacementos.app.ui.common.LoadingState
import com.aiplacementos.app.ui.common.PrimaryButton
import com.aiplacementos.app.ui.common.StepProgressIndicator
import com.aiplacementos.app.ui.common.UiState
import com.aiplacementos.app.ui.common.networkModule
import com.aiplacementos.app.ui.theme.Spacing

// internal (not private) -- reused by ProfileScreen's Preparation Goals section.
internal val GOALS = listOf(
    "DSA", "SQL", "Programming", "System Design", "Aptitude", "Logical Reasoning",
    "Technical Interviews", "HR Interviews", "Mock Interviews", "Resume Building",
    "Communication", "Core Subjects", "Projects", "Coding Practice",
    "Competitive Programming", "Domain Knowledge", "Soft Skills", "Other",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingGoalsScreen(onNext: () -> Unit) {
    val network = networkModule()
    val viewModel: OnboardingGoalsViewModel = viewModel(
        factory = viewModelFactory { initializer { OnboardingGoalsViewModel(network.profileRepository) } },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val saving by viewModel.saving.collectAsStateWithLifecycle()
    // An explicit listSaver rather than the default autoSaver: a Set is only incidentally
    // Serializable (it depends on which concrete Set implementation the +/- operators happen to
    // return), and a saver that silently stops working would put us back to losing the
    // student's goal picks on rotation with no compile-time signal.
    var selected by rememberSaveable(
        stateSaver = listSaver<Set<String>, String>(save = { it.toList() }, restore = { it.toSet() }),
    ) { mutableStateOf<Set<String>>(emptySet()) }
    var prefilled by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state) {
        val profile = (state as? UiState.Success<ProfileDto>)?.data
        if (profile != null && !prefilled) {
            selected = profile.preparationGoals.orEmpty().toSet()
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
            StepProgressIndicator(step = 6, total = 7)
            Text(text = "What do you want to improve?", style = MaterialTheme.typography.headlineSmall)
            // chipGap (not xs) between these -- see Spacing.chipGap's own doc comment.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.chipGap)) {
                GOALS.forEach { goal ->
                    FilterChip(
                        selected = goal in selected,
                        onClick = { selected = if (goal in selected) selected - goal else selected + goal },
                        label = { Text(goal) },
                    )
                }
            }
            PrimaryButton(
                text = if (saving) "Saving..." else "Next",
                onClick = { viewModel.save(selected.toList(), onSaved = onNext) },
                enabled = !saving,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
