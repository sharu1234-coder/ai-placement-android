package com.aiplacementos.app.ui.atscheck

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aiplacementos.app.data.remote.AtsIssueDto
import com.aiplacementos.app.ui.common.AppCard
import com.aiplacementos.app.ui.common.DetailTopBar
import com.aiplacementos.app.ui.common.EmptyState
import com.aiplacementos.app.ui.common.ErrorState
import com.aiplacementos.app.ui.common.LoadingState
import com.aiplacementos.app.ui.common.UiState
import com.aiplacementos.app.ui.common.networkModule
import com.aiplacementos.app.ui.theme.Spacing

private val SEVERITIES = listOf("high", "medium", "low")

@Composable
fun AtsCheckIssuesScreen(checkId: Int, onBack: () -> Unit) {
    val network = networkModule()
    val viewModel: AtsCheckIssuesViewModel = viewModel(
        factory = viewModelFactory { initializer { AtsCheckIssuesViewModel(checkId, network.atsCheckRepository) } },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(topBar = { DetailTopBar(title = "Top Issues", onBack = onBack) }) { padding ->
        when (val current = state) {
            is UiState.Loading -> LoadingState(modifier = Modifier.fillMaxSize().padding(padding))
            is UiState.Error -> ErrorState(
                message = current.message,
                onRetry = viewModel::load,
                modifier = Modifier.fillMaxSize().padding(padding),
            )
            is UiState.Empty -> EmptyState(
                message = "No issues found -- your resume looks solid!",
                modifier = Modifier.fillMaxSize().padding(padding),
            )
            is UiState.Success<List<AtsIssueDto>> -> IssuesContent(issues = current.data, modifier = Modifier.padding(padding))
        }
    }
}

@Composable
private fun IssuesContent(issues: List<AtsIssueDto>, modifier: Modifier = Modifier) {
    var selectedSeverity by remember { mutableStateOf(SEVERITIES.first { severity -> issues.any { it.severity == severity } }) }
    val counts = SEVERITIES.associateWith { severity -> issues.count { it.severity == severity } }
    val filtered = issues.filter { it.severity == selectedSeverity }
    val selectedIndex = SEVERITIES.indexOf(selectedSeverity).coerceAtLeast(0)

    Column(modifier = modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedIndex) {
            SEVERITIES.forEach { severity ->
                Tab(
                    selected = severity == selectedSeverity,
                    onClick = { selectedSeverity = severity },
                    text = { Text("${severity.replaceFirstChar { it.uppercase() }} (${counts[severity] ?: 0})") },
                )
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            items(filtered, key = { it.title }) { issue -> IssueCard(issue) }
        }
    }
}

@Composable
private fun IssueCard(issue: AtsIssueDto) {
    var expanded by remember { mutableStateOf(false) }
    AppCard(modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }) {
        Row {
            Surface(color = severityColor(issue.severity).copy(alpha = 0.15f), shape = MaterialTheme.shapes.small) {
                Text(
                    text = "${issue.severity.replaceFirstChar { it.uppercase() }} Priority",
                    style = MaterialTheme.typography.labelSmall,
                    color = severityColor(issue.severity),
                    modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 2.dp),
                )
            }
        }
        Text(
            text = issue.title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = Spacing.xs),
        )
        Text(text = issue.description, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 2.dp))
        if (expanded) {
            Text(
                text = issue.howToFix,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = Spacing.sm),
            )
        } else {
            Text(
                text = "How to fix →",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = Spacing.sm),
            )
        }
    }
}
