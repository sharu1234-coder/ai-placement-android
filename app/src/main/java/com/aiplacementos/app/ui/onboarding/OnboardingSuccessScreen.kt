package com.aiplacementos.app.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aiplacementos.app.ui.common.Benefit
import com.aiplacementos.app.ui.common.BenefitRow
import com.aiplacementos.app.ui.common.PrimaryButton
import com.aiplacementos.app.ui.common.SummitIllustration
import com.aiplacementos.app.ui.theme.Spacing

@Composable
fun OnboardingSuccessScreen(onGoToHome: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        SummitIllustration(
            modifier = Modifier.size(width = 190.dp, height = 130.dp),
            track = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
            line = MaterialTheme.colorScheme.primary,
            accent = MaterialTheme.colorScheme.tertiary,
        )
        Text(
            text = "You are all set",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier
                .padding(top = Spacing.lg)
                .semantics { heading() },
        )
        Text(
            // Deliberately does not claim the roadmap exists. Generation runs on the previous
            // step and can fail; a success screen that announces a finished roadmap would be
            // telling a student something this screen has no way to know.
            text = "Your profile is set up. Your roadmap, practice and mock interviews all " +
                "start from your home screen.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Spacing.sm),
        )
        BenefitRow(
            modifier = Modifier.padding(top = Spacing.lg),
            benefits = listOf(
                Benefit(Icons.Filled.Route, "Follow your roadmap"),
                Benefit(Icons.AutoMirrored.Filled.Assignment, "Take a test"),
                Benefit(Icons.Filled.RecordVoiceOver, "Try a mock interview"),
            ),
        )
        PrimaryButton(
            text = "Go to Home",
            onClick = onGoToHome,
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.lg),
        )
    }
}
