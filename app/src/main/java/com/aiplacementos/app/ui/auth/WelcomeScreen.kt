package com.aiplacementos.app.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aiplacementos.app.ui.common.PrimaryButton
import com.aiplacementos.app.ui.theme.Spacing

@Composable
fun WelcomeScreen(onGetStarted: () -> Unit, onNavigateToLogin: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.RocketLaunch,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(96.dp),
        )
        Text(
            text = "AI Placement OS",
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(top = Spacing.lg),
        )
        Text(
            text = "Your Personal Placement Partner",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.xs),
        )
        Text(
            text = "A personalized roadmap, practice, assessments, and mock interviews " +
                "built around your resume and goals.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Spacing.md),
        )

        PrimaryButton(
            text = "Get Started",
            onClick = onGetStarted,
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.xl),
        )
        TextButton(onClick = onNavigateToLogin) {
            Text("Already have an account? Sign In")
        }
    }
}
