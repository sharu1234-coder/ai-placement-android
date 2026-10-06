package com.aiplacementos.app.ui.tests

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aiplacementos.app.data.remote.AssessmentDto
import com.aiplacementos.app.ui.common.ErrorState
import com.aiplacementos.app.ui.common.LoadingState
import com.aiplacementos.app.ui.common.PrimaryButton
import com.aiplacementos.app.ui.common.UiState
import com.aiplacementos.app.ui.common.networkModule
import com.aiplacementos.app.ui.theme.Spacing

@Composable
fun TestListScreen(blueprintId: Int, onStartTest: (assessmentId: Int) -> Unit) {
    val network = networkModule()
    val viewModel: TestListViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                TestListViewModel(blueprintId, network.assessmentRepository, createSavedStateHandle())
            }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    when (val current = state) {
        is UiState.Loading -> LoadingState(modifier = Modifier.fillMaxSize())
        is UiState.Error -> ErrorState(
            message = current.message,
            modifier = Modifier.fillMaxSize(),
            onRetry = viewModel::start,
        )
        is UiState.Empty -> LoadingState(modifier = Modifier.fillMaxSize())
        is UiState.Success<AssessmentDto> -> ReadyToStart(
            onBegin = { onStartTest(current.data.id) },
        )
    }
}

@Composable
private fun ReadyToStart(onBegin: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.md),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = "Your test is ready", style = MaterialTheme.typography.headlineSmall)
        Text(
            text = "Questions adapt as you go -- a correct answer raises the difficulty on " +
                "that skill, an incorrect one eases it back.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = Spacing.sm, bottom = Spacing.lg),
        )
        PrimaryButton(text = "Begin", onClick = onBegin, modifier = Modifier.fillMaxWidth())
    }
}
