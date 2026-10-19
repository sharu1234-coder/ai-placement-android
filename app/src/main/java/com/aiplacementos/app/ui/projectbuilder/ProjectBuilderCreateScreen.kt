package com.aiplacementos.app.ui.projectbuilder

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aiplacementos.app.ui.common.DetailTopBar
import com.aiplacementos.app.ui.common.PrimaryButton
import com.aiplacementos.app.ui.common.networkModule
import com.aiplacementos.app.ui.theme.Spacing

@Composable
fun ProjectBuilderCreateScreen(onBack: () -> Unit, onCreated: (buildId: Int) -> Unit) {
    val network = networkModule()
    val viewModel: ProjectBuildCreateViewModel = viewModel(
        factory = viewModelFactory { initializer { ProjectBuildCreateViewModel(network.projectBuilderRepository) } },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    var title by rememberSaveable { mutableStateOf("") }
    var stackSummary by rememberSaveable { mutableStateOf("") }

    val current = state
    LaunchedEffect(current) {
        if (current is ProjectBuildCreateUiState.Success) onCreated(current.buildId)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        DetailTopBar(title = "New Project", onBack = onBack)
        Column(
            // Scrollable: this column is taller than a handset once the system font scale is
            // raised, and without a scroll its bottom action cannot be reached.
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(Spacing.md),
        ) {
            Text(
                text = "Build one real project, one guided stage at a time -- from understanding the " +
                    "problem through to a GitHub-ready README and interview prep.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(text = "Project Title", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = Spacing.md))
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                placeholder = { Text("e.g. Real-time Analytics Pipeline") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs),
            )
            Text(text = "Tech Stack (optional)", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = Spacing.md))
            OutlinedTextField(
                value = stackSummary,
                onValueChange = { stackSummary = it },
                placeholder = { Text("e.g. Python, Kafka, PostgreSQL") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs),
            )

            if (current is ProjectBuildCreateUiState.Error) {
                Text(
                    text = current.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = Spacing.sm),
                )
            }

            if (current is ProjectBuildCreateUiState.Submitting) {
                CircularProgressIndicator(modifier = Modifier.padding(top = Spacing.lg))
            } else {
                PrimaryButton(
                    text = "Start Building",
                    onClick = { viewModel.create(title, stackSummary) },
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.lg),
                )
            }
        }
    }
}
