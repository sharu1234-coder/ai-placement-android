package com.aiplacementos.app.ui.careerstrategy

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.aiplacementos.app.ui.common.networkModule
import com.aiplacementos.app.ui.theme.Spacing

/**
 * The Home entry point into Career Strategy.
 *
 * Self-contained and fail-quiet, matching TodaysMissionSection: Home already loads several
 * sources concurrently and each one degrades on its own, so a strategy that cannot be fetched
 * removes this card rather than putting an error banner on the student's home screen. A failure
 * here is not something they can act on, and Home is not the place to tell them about it.
 *
 * It shows the one line that matters -- what is blocking them and what to do -- and never a
 * readiness percentage on its own. Home already carries a readiness ring; repeating the number
 * here would add nothing, while repeating the *decision* is the whole point of the feature.
 */
@Composable
fun YourCareerStrategyCard(onOpen: () -> Unit) {
    val network = networkModule()
    val viewModel: CareerStrategyViewModel = viewModel(
        factory = viewModelFactory { initializer { CareerStrategyViewModel(network.careerStrategyRepository) } },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val current = state
    // Loading, error, no-role and not-enough-data all render nothing here. Each of those has a
    // proper screen of its own; a stub on Home would just be noise the student cannot act on.
    if (current !is CareerStrategyUiState.Success) return
    val bottleneck = current.strategy.bottleneck ?: return

    AppCard(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(22.dp),
            )
            Text(
                text = "Your Career Strategy",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(start = Spacing.sm),
            )
        }
        Column(modifier = Modifier.padding(top = Spacing.sm)) {
            Text(
                text = "Biggest constraint: ${bottleneck.title}",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                text = bottleneck.recommendedAction,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(top = Spacing.xs),
            )
            if (current.strategy.snapshotIsNew) {
                Text(
                    text = "This changed since you last looked.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
        }
    }
}
