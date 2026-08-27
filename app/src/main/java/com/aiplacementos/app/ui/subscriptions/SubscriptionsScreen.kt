package com.aiplacementos.app.ui.subscriptions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aiplacementos.app.ui.common.AppCard
import com.aiplacementos.app.ui.common.ErrorState
import com.aiplacementos.app.ui.common.LoadingState
import com.aiplacementos.app.ui.common.UiState
import com.aiplacementos.app.ui.common.networkModule
import com.aiplacementos.app.ui.theme.Spacing

@Composable
fun SubscriptionsScreen() {
    val network = networkModule()
    val viewModel: SubscriptionsViewModel = viewModel(
        factory = viewModelFactory { initializer { SubscriptionsViewModel(network.subscriptionRepository) } },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    when (val current = state) {
        is UiState.Loading -> LoadingState(modifier = Modifier.fillMaxSize())
        is UiState.Error -> ErrorState(
            message = current.message,
            onRetry = viewModel::load,
            modifier = Modifier.fillMaxSize(),
        )
        is UiState.Empty -> LoadingState(modifier = Modifier.fillMaxSize())
        is UiState.Success -> Column(
            modifier = Modifier.fillMaxSize().padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(text = "Subscription", style = MaterialTheme.typography.headlineSmall)

            AppCard(modifier = Modifier.fillMaxWidth()) {
                Icon(
                    imageVector = Icons.Filled.WorkspacePremium,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "You're on the Free plan",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = Spacing.sm),
                )
                Text(
                    text = "Payments are currently unavailable -- no payment is being " +
                        "collected today.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }

            AppCard(modifier = Modifier.fillMaxWidth()) {
                Text(text = "Free", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "Your current plan",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = Spacing.sm),
                )
                FREE_FEATURES.forEach { FeatureRow(it) }
            }

            AppCard(modifier = Modifier.fillMaxWidth()) {
                Text(text = "Premium", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "Coming soon",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = Spacing.sm),
                )
                // Premium's value proposition is free's features plus these -- all shown as
                // included (not "locked"), since there's no purchase to unlock yet at all;
                // "Coming soon" above already communicates that.
                (FREE_FEATURES + PREMIUM_ONLY_FEATURES).forEach { FeatureRow(it) }
            }
        }
    }
}

private val FREE_FEATURES = listOf(
    "Personalized placement roadmap",
    "Practice tests and mock interviews",
    "Job Description Analyzer",
    "Placement Alarm",
)

private val PREMIUM_ONLY_FEATURES = listOf(
    "Unlimited test attempts",
    "Deeper performance analytics",
    "Priority AI response times",
)

@Composable
private fun FeatureRow(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
        Icon(
            imageVector = Icons.Filled.Check,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(end = Spacing.sm),
        )
        Text(text = text, style = MaterialTheme.typography.bodyMedium)
    }
}
