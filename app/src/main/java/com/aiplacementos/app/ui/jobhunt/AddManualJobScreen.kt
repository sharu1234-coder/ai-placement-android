package com.aiplacementos.app.ui.jobhunt

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aiplacementos.app.ui.common.AppCard
import com.aiplacementos.app.ui.common.DetailTopBar
import com.aiplacementos.app.ui.common.PrimaryButton
import com.aiplacementos.app.ui.common.networkModule
import com.aiplacementos.app.ui.theme.Spacing

/**
 * Add a job the student found somewhere else.
 *
 * The one way into the tracker that does not depend on the job provider, which is what makes
 * the rest of the product usable for a student whose jobs come from campus placement, a
 * referral, a company's own site or a message from a senior. Nothing here is imported or
 * synced -- there is no external source to import from -- so the language stays "add" and
 * "track".
 *
 * Only the title is required. Everything else is optional because the model says it is: a
 * referral may genuinely arrive without a company name attached yet, and refusing to record
 * it would lose the application rather than improve the data.
 */
@Composable
fun AddManualJobScreen(
    onBack: () -> Unit,
    onAdded: () -> Unit,
) {
    val network = networkModule()
    val viewModel: AddManualJobViewModel = viewModel(
        factory = viewModelFactory { initializer { AddManualJobViewModel(network.jobHuntRepository) } },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Keyed off the server's confirmation, never off the submit call returning, so a refused
    // request can never look like a successful one.
    LaunchedEffect(state.added) {
        if (state.added != null) onAdded()
    }

    Scaffold(
        topBar = { DetailTopBar(title = "Add a job", onBack = onBack) },
    ) { padding ->
        AddManualJobContent(
            state = state,
            onTitleChange = viewModel::onTitleChange,
            onCompanyChange = viewModel::onCompanyChange,
            onLocationChange = viewModel::onLocationChange,
            onUrlChange = viewModel::onUrlChange,
            onDescriptionChange = viewModel::onDescriptionChange,
            onStageChange = viewModel::onStageChange,
            onSubmit = viewModel::submit,
            modifier = Modifier.padding(padding),
        )
    }
}

/**
 * The form itself, with no view model and no network module of its own.
 *
 * Split out for the same reason JobDetailContent is: a composable that builds its own
 * dependencies cannot be rendered in a test, so the rendering, the validation message and the
 * disabled states would only ever be checked by looking at them.
 */
@Composable
internal fun AddManualJobContent(
    state: AddManualJobUiState,
    onTitleChange: (String) -> Unit,
    onCompanyChange: (String) -> Unit,
    onLocationChange: (String) -> Unit,
    onUrlChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onStageChange: (ApplicationStage) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        AppCard(containerColor = MaterialTheme.colorScheme.secondaryContainer) {
            Text(
                text = "Found a job somewhere else?",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                // Says what this does and, just as importantly, what it does not: nothing
                // is fetched from the link and no application is submitted anywhere.
                text = "Campus placement, a referral, LinkedIn, a company site, a message " +
                    "from a senior -- add it here and track it like any other application. " +
                    "We only store what you type.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }

        OutlinedTextField(
            value = state.title,
            onValueChange = onTitleChange,
            label = { Text("Job title") },
            placeholder = { Text("e.g. Data Engineer") },
            singleLine = true,
            isError = state.titleError != null,
            supportingText = state.titleError?.let { { Text(it) } },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = state.company,
            onValueChange = onCompanyChange,
            label = { Text("Company (optional)") },
            placeholder = { Text("e.g. Contoso") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = state.location,
            onValueChange = onLocationChange,
            label = { Text("Location (optional)") },
            placeholder = { Text("e.g. Hyderabad") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = state.url,
            onValueChange = onUrlChange,
            label = { Text("Link (optional)") },
            placeholder = { Text("https://...") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = state.description,
            onValueChange = onDescriptionChange,
            label = { Text("Job description (optional)") },
            placeholder = { Text("Paste the description if you have it") },
            // Multi-line and bounded: a pasted description is the longest thing anyone
            // types here, and an unbounded field would push the button off the screen.
            minLines = 3,
            modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp),
        )

        Text(
            text = "Where is this application now?",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = "Already applied or interviewing? Say so -- you don't have to start again.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Scrolls horizontally rather than wrapping, matching the tracker's own stage row:
        // at a large font scale five chips do not fit on one line, and a clipped final
        // stage would be unreachable.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Spacing.chipGap),
        ) {
            ApplicationStage.pipelineStages.forEach { stage ->
                FilterChip(
                    selected = state.stage == stage,
                    onClick = { onStageChange(stage) },
                    enabled = !state.isSubmitting,
                    label = { Text(stage.label) },
                )
            }
        }

        state.submitError?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        PrimaryButton(
            text = "Add to my applications",
            loading = state.isSubmitting,
            onClick = onSubmit,
            enabled = state.canSubmit,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
