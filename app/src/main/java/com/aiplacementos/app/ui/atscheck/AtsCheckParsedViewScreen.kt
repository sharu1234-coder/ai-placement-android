package com.aiplacementos.app.ui.atscheck

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aiplacementos.app.data.remote.AtsCheckReportDto
import com.aiplacementos.app.data.remote.SectionAnalysisDto
import com.aiplacementos.app.ui.common.AppCard
import com.aiplacementos.app.ui.common.DetailTopBar
import com.aiplacementos.app.ui.common.EmptyState
import com.aiplacementos.app.ui.common.ErrorState
import com.aiplacementos.app.ui.common.LoadingState
import com.aiplacementos.app.ui.common.UiState
import com.aiplacementos.app.ui.common.networkModule
import com.aiplacementos.app.ui.theme.Spacing

private val TABS = listOf("Parsed View", "Raw Text")

@Composable
fun AtsCheckParsedViewScreen(checkId: Int, onBack: () -> Unit) {
    val network = networkModule()
    val viewModel: AtsCheckParsedViewViewModel = viewModel(
        factory = viewModelFactory { initializer { AtsCheckParsedViewViewModel(checkId, network.atsCheckRepository) } },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var tabIndex by remember { mutableIntStateOf(0) }

    Scaffold(topBar = { DetailTopBar(title = "How ATS Reads Your Resume", onBack = onBack) }) { padding ->
        when (val current = state) {
            is UiState.Loading -> LoadingState(modifier = Modifier.fillMaxSize().padding(padding))
            is UiState.Error -> ErrorState(
                message = current.message,
                onRetry = viewModel::load,
                modifier = Modifier.fillMaxSize().padding(padding),
            )
            is UiState.Empty -> EmptyState(
                title = "Nothing was parsed",
                message = "No fields could be read from this resume.",
                modifier = Modifier.fillMaxSize().padding(padding),
            )
            is UiState.Success<AtsCheckReportDto> -> Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                TabRow(selectedTabIndex = tabIndex) {
                    TABS.forEachIndexed { index, title ->
                        Tab(selected = tabIndex == index, onClick = { tabIndex = index }, text = { Text(title) })
                    }
                }
                if (tabIndex == 0) {
                    ParsedSectionsList(current.data.sectionAnalysis)
                } else {
                    RawTextView(current.data.rawText)
                }
            }
        }
    }
}

@Composable
private fun ParsedSectionsList(sections: List<SectionAnalysisDto>) {
    val withContent = sections.filter { !it.content.isNullOrBlank() }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        items(withContent, key = { it.section }) { section ->
            Column {
                Text(
                    text = sectionLabel(section.section).uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = section.content.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
        }
    }
}

@Composable
private fun RawTextView(rawText: String) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(Spacing.md)) {
        item {
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Text(text = rawText, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
