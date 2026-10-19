package com.aiplacementos.app.ui.onboarding

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.aiplacementos.app.ui.common.StepProgressIndicator
import com.aiplacementos.app.ui.theme.Spacing

/**
 * The top of an onboarding step: where you are, what is being asked, and why.
 *
 * ## The gap this closes
 *
 * Every step asked a bare question -- "What's your name?", "Select your skills" -- with no
 * statement of what the answer is for. Onboarding is the one place in the product where a
 * student has given nothing and received nothing, so it is the place where an unexplained
 * demand for personal detail costs the most. A single supporting line per step is the whole
 * fix, and it belongs in a shared component so a later step cannot quietly ship without one.
 *
 * [subtitle] must say what the app does with the answer, or that it can be changed later.
 * It must not promise behaviour the product does not have -- an onboarding promise is the
 * hardest kind to withdraw.
 */
@Composable
fun OnboardingHeader(
    step: Int,
    total: Int,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        StepProgressIndicator(step = step, total = total)
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier
                .padding(top = Spacing.md)
                .semantics { heading() },
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.xs),
        )
    }
}
