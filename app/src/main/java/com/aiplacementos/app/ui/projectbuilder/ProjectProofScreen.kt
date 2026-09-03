package com.aiplacementos.app.ui.projectbuilder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aiplacementos.app.data.remote.ProjectProofDto
import com.aiplacementos.app.ui.common.AppCard
import com.aiplacementos.app.ui.common.DetailTopBar
import com.aiplacementos.app.ui.common.ErrorState
import com.aiplacementos.app.ui.common.LoadingState
import com.aiplacementos.app.ui.common.networkModule
import com.aiplacementos.app.ui.theme.Spacing

@Composable
fun ProjectProofScreen(buildId: Int, onBack: () -> Unit) {
    val network = networkModule()
    val viewModel: ProjectProofViewModel = viewModel(
        factory = viewModelFactory { initializer { ProjectProofViewModel(buildId, network.projectBuilderRepository) } },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        DetailTopBar(title = "Project Proof", onBack = onBack)
        when (val current = state) {
            is ProjectProofUiState.Loading -> LoadingState(modifier = Modifier.fillMaxSize())
            is ProjectProofUiState.Error -> ErrorState(
                message = current.message,
                icon = if (current.isNetworkError) Icons.Filled.WifiOff else Icons.Filled.ErrorOutline,
                modifier = Modifier.fillMaxSize(),
                onRetry = viewModel::load,
            )
            is ProjectProofUiState.Success -> ProofContent(current.proof)
        }
    }
}

@Composable
private fun ProofContent(proof: ProjectProofDto) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item { ProofSection(title = "Resume Bullet", body = proof.resumeBullet) }
        item { ProofSection(title = "README Draft", body = proof.readmeMarkdown) }
        item { ProofSection(title = "60-Second Explanation", body = proof.explanation60s) }
        item { ProofSection(title = "5-Minute Explanation", body = proof.explanation5min) }
        item {
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Text(text = "Defense Questions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Column(modifier = Modifier.padding(top = Spacing.sm)) {
                    proof.defenseQuestions.forEach { question ->
                        Text(text = "• $question", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = Spacing.xs))
                    }
                }
            }
        }
    }
}

@Composable
private fun ProofSection(title: String, body: String) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(text = body, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = Spacing.sm))
    }
}
