package com.aiplacementos.app.ui.job

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aiplacementos.app.data.remote.JobDescriptionParseResponseDto
import com.aiplacementos.app.ui.common.PrimaryButton
import com.aiplacementos.app.ui.common.UiState
import com.aiplacementos.app.ui.common.networkModule
import com.aiplacementos.app.ui.theme.Spacing

@Composable
fun JDPasteScreen(onContinue: (jdId: Int) -> Unit) {
    val network = networkModule()
    val viewModel: JDPasteViewModel = viewModel(
        factory = viewModelFactory { initializer { JDPasteViewModel(network.jobRepository) } },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    var jdText by remember { mutableStateOf("") }

    LaunchedEffect(state) {
        val current = state
        if (current is UiState.Success<JobDescriptionParseResponseDto>) {
            onContinue(current.data.id)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(text = "Prepare for a job", style = MaterialTheme.typography.headlineSmall)
        Text(
            text = "Paste the job description below.",
            style = MaterialTheme.typography.bodyMedium,
        )

        OutlinedTextField(
            value = jdText,
            onValueChange = { jdText = it },
            label = { Text("Job description") },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 220.dp),
        )

        if (state is UiState.Error) {
            Text(
                text = (state as UiState.Error).message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        PrimaryButton(
            text = if (state is UiState.Loading) "Analyzing..." else "Prepare Me for This Job",
            onClick = { viewModel.parse(jdText) },
            enabled = state !is UiState.Loading && jdText.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
