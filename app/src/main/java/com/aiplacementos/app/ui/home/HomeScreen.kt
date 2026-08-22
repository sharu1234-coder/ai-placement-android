package com.aiplacementos.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aiplacementos.app.ui.common.AppCard
import com.aiplacementos.app.ui.common.EmptyState
import com.aiplacementos.app.ui.common.ErrorState
import com.aiplacementos.app.ui.common.LoadingState
import com.aiplacementos.app.ui.common.PrimaryButton
import com.aiplacementos.app.ui.common.UiState
import com.aiplacementos.app.ui.theme.Spacing

@Composable
fun HomeScreen(
    onStartPrepareMe: () -> Unit,
    viewModel: HomeViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    when (val current = state) {
        is UiState.Loading -> LoadingState(modifier = Modifier.fillMaxSize())
        is UiState.Error -> ErrorState(message = current.message, modifier = Modifier.fillMaxSize())
        is UiState.Empty -> EmptyState(message = "Nothing to show yet.", modifier = Modifier.fillMaxSize())
        is UiState.Success<HomeData> -> HomeContent(
            data = current.data,
            onStartPrepareMe = onStartPrepareMe,
        )
    }
}

@Composable
private fun HomeContent(data: HomeData, onStartPrepareMe: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(text = data.greeting, style = MaterialTheme.typography.headlineSmall)
        Text(text = "Your placement journey", style = MaterialTheme.typography.bodyMedium)

        AppCard(modifier = Modifier.fillMaxWidth()) {
            Text(text = "Prepare for a job", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Paste a job description or choose a target role to get started.",
                style = MaterialTheme.typography.bodyMedium,
            )
            PrimaryButton(
                text = "Prepare Me for This Job",
                onClick = onStartPrepareMe,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.sm),
            )
        }
    }
}
