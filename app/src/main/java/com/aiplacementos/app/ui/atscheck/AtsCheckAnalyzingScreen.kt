package com.aiplacementos.app.ui.atscheck

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aiplacementos.app.ui.common.AppCard
import com.aiplacementos.app.ui.common.ErrorState
import com.aiplacementos.app.ui.common.networkModule
import com.aiplacementos.app.ui.theme.Spacing

@Composable
fun AtsCheckAnalyzingScreen(
    resumeId: Int,
    targetRole: String?,
    onComplete: (checkId: Int) -> Unit,
) {
    val network = networkModule()
    val viewModel: AtsCheckAnalyzingViewModel = viewModel(
        factory = viewModelFactory {
            initializer { AtsCheckAnalyzingViewModel(resumeId, targetRole, network.atsCheckRepository) }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.start(onComplete) }

    when (val current = state) {
        is AtsCheckAnalyzingUiState.Error -> ErrorState(
            message = current.message,
            onRetry = { viewModel.start(onComplete) },
            modifier = Modifier.fillMaxSize(),
        )
        is AtsCheckAnalyzingUiState.InProgress -> AnalyzingContent(stageIndex = current.stageIndex)
    }
}

@Composable
private fun AnalyzingContent(stageIndex: Int) {
    Column(
        modifier = Modifier.fillMaxSize().padding(Spacing.lg),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Filled.Insights,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(56.dp),
        )
        Text(
            text = "Analyzing your resume...",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = Spacing.md),
        )
        Text(
            text = "This may take a few seconds",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        AppCard(modifier = Modifier.fillMaxWidth().padding(top = Spacing.lg)) {
            AtsCheckStageLabels.forEachIndexed { index, label ->
                StageRow(label = label, done = index < stageIndex, active = index == stageIndex)
            }
        }

        AppCard(
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.md),
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ) {
            Text(
                text = "Tip: A well-structured resume has higher chances of being shortlisted by ATS systems.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Composable
private fun StageRow(label: String, done: Boolean, active: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        val (icon, tint) = when {
            done -> Icons.Filled.CheckCircle to MaterialTheme.colorScheme.primary
            active -> Icons.Filled.RadioButtonChecked to MaterialTheme.colorScheme.primary
            else -> Icons.Filled.RadioButtonUnchecked to MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
        }
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            color = if (done || active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = Spacing.sm),
        )
    }
}
