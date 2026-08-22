package com.aiplacementos.app.ui.job

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aiplacementos.app.data.remote.AssessmentBlueprintDto
import com.aiplacementos.app.data.remote.BlueprintSectionDto
import com.aiplacementos.app.ui.common.AppCard
import com.aiplacementos.app.ui.common.ErrorState
import com.aiplacementos.app.ui.common.LoadingState
import com.aiplacementos.app.ui.common.PrimaryButton
import com.aiplacementos.app.ui.common.UiState
import com.aiplacementos.app.ui.common.networkModule
import com.aiplacementos.app.ui.theme.Spacing

@Composable
fun PrepareMeSummaryScreen(jdId: Int, onStartTests: (blueprintId: Int) -> Unit) {
    val network = networkModule()
    val viewModel: PrepareMeSummaryViewModel = viewModel(
        factory = viewModelFactory {
            initializer { PrepareMeSummaryViewModel(jdId, network.assessmentRepository) }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    when (val current = state) {
        is UiState.Loading -> LoadingState(modifier = Modifier.fillMaxSize())
        is UiState.Error -> ErrorState(message = current.message, modifier = Modifier.fillMaxSize())
        is UiState.Empty -> LoadingState(modifier = Modifier.fillMaxSize())
        is UiState.Success<AssessmentBlueprintDto> -> BlueprintSummary(
            blueprint = current.data,
            onStartTests = onStartTests,
        )
    }
}

@Composable
private fun BlueprintSummary(blueprint: AssessmentBlueprintDto, onStartTests: (blueprintId: Int) -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(Spacing.md)) {
        Text(text = "Your preparation plan", style = MaterialTheme.typography.headlineSmall)
        Text(
            text = "${blueprint.sections.size} skills to focus on, prioritized by what you're missing.",
            style = MaterialTheme.typography.bodyMedium,
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            items(blueprint.sections, key = { it.skill }) { section ->
                SectionCard(section)
            }
        }

        PrimaryButton(
            text = "Start Tests",
            onClick = { onStartTests(blueprint.id) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Spacing.md),
        )
    }
}

@Composable
private fun SectionCard(section: BlueprintSectionDto) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Text(text = section.skill, style = MaterialTheme.typography.titleMedium)
        Text(
            text = "${section.assessmentType} · ${section.questionCount} questions" +
                if (section.isGap) " · needs work" else " · you've got this",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
