package com.aiplacementos.app.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.aiplacementos.app.ui.theme.Spacing

/** "Step X of [total]" label + a determinate progress bar -- used across the onboarding
 * flow's numbered steps. No shared onboarding state is threaded through this; each step
 * screen just passes its own position. */
@Composable
fun StepProgressIndicator(step: Int, total: Int, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Step $step of $total",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LinearProgressIndicator(
            progress = { step.toFloat() / total.toFloat() },
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs),
        )
    }
}
