package com.aiplacementos.app.ui.careergoal

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aiplacementos.app.data.remote.CareerGoalListItemDto
import com.aiplacementos.app.ui.common.AppCard
import com.aiplacementos.app.ui.common.ConfirmationDialog
import com.aiplacementos.app.ui.common.EmptyState
import com.aiplacementos.app.ui.common.ErrorState
import com.aiplacementos.app.ui.common.SkeletonList
import com.aiplacementos.app.ui.common.networkModule
import com.aiplacementos.app.ui.theme.Spacing

/** Mirrors the backend's own ceiling so an over-long paste is refused here with an explanation
 * rather than by a 422 the student has to interpret. */
private const val MAX_JOB_DESCRIPTION_LENGTH = 20_000

@Composable
fun CareerGoalListScreen(onOpenGoal: (goalId: Int) -> Unit) {
    val network = networkModule()
    val viewModel: CareerGoalListViewModel = viewModel(
        factory = viewModelFactory { initializer { CareerGoalListViewModel(network.careerGoalRepository) } },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
    val saveError by viewModel.saveError.collectAsStateWithLifecycle()
    var showForm by remember { mutableStateOf(false) }

    LifecycleResumeEffect(Unit) {
        viewModel.load()
        onPauseOrDispose {}
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showForm = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Add a target job")
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (val current = state) {
                is CareerGoalListUiState.Loading ->
                    SkeletonList(count = 3, modifier = Modifier.fillMaxSize().padding(Spacing.md))

                is CareerGoalListUiState.Error -> ErrorState(
                    message = current.message,
                    icon = if (current.isNetworkError) Icons.Filled.WifiOff else Icons.Filled.ErrorOutline,
                    modifier = Modifier.fillMaxSize(),
                    onRetry = viewModel::load,
                )

                is CareerGoalListUiState.Success -> if (current.goals.isEmpty()) {
                    EmptyState(
                        title = "No Target Jobs Yet",
                        message = "Add a job you're preparing for, and this app will work out " +
                            "what you can already show for it and what to practise next.",
                        icon = Icons.Filled.Flag,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    CareerGoalList(goals = current.goals, onOpenGoal = onOpenGoal)
                }
            }
        }
    }

    if (showForm) {
        AddCareerGoalDialog(
            isSaving = isSaving,
            errorMessage = saveError,
            onDismiss = {
                showForm = false
                viewModel.dismissSaveError()
            },
            onSave = { title, company, location, jobDescription ->
                viewModel.createGoal(title, company, location, jobDescription) {
                    showForm = false
                }
            },
        )
    }
}

@Composable
private fun CareerGoalList(goals: List<CareerGoalListItemDto>, onOpenGoal: (Int) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        items(goals, key = { it.id }) { goal ->
            CareerGoalCard(goal = goal, onClick = { onOpenGoal(goal.id) })
        }
    }
}

@Composable
private fun CareerGoalCard(goal: CareerGoalListItemDto, onClick: () -> Unit) {
    AppCard(
        modifier = Modifier
            .fillMaxWidth()
            // 48dp is Android's minimum touch target; the card's own content can be shorter
            // than that at a small font scale.
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick),
    ) {
        Text(
            text = goal.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            // Location is genuinely optional -- a posting that does not state one shows the
            // company alone rather than a stray separator.
            text = listOfNotNull(goal.company, goal.location).joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = preparationStateLabel(goal.preparationState),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            if (goal.status != "active") {
                Text(
                    text = goalStatusLabel(goal.status),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddCareerGoalDialog(
    isSaving: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onSave: (title: String, company: String, location: String, jobDescription: String) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var company by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var jobDescription by remember { mutableStateOf("") }

    val tooLong = jobDescription.length > MAX_JOB_DESCRIPTION_LENGTH
    val canSave = title.isNotBlank() && company.isNotBlank() && jobDescription.isNotBlank() && !tooLong

    ConfirmationDialog(
        title = "Add a target job",
        message = "Paste the posting. Nothing is sent for analysis until you ask for it.",
        confirmText = if (isSaving) "Saving…" else "Save",
        confirmEnabled = canSave && !isSaving,
        dismissEnabled = !isSaving,
        onConfirm = { onSave(title, company, location, jobDescription) },
        onDismiss = onDismiss,
    ) {
        OutlinedTextField(
            value = title,
            onValueChange = { title = it.take(200) },
            label = { Text("Job title") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = company,
            onValueChange = { company = it.take(200) },
            label = { Text("Company") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = location,
            onValueChange = { location = it.take(200) },
            label = { Text("Location (optional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = jobDescription,
            onValueChange = { jobDescription = it },
            label = { Text("Job description") },
            minLines = 4,
            isError = tooLong,
            supportingText = {
                if (tooLong) {
                    Text("That's longer than this app can analyze. Paste the main sections only.")
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        if (errorMessage != null) {
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}
