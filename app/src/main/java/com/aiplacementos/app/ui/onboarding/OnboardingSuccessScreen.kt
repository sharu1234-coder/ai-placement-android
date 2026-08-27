package com.aiplacementos.app.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.aiplacementos.app.ui.common.PrimaryButton
import com.aiplacementos.app.ui.theme.Spacing

@Composable
fun OnboardingSuccessScreen(onGoToHome: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = Spacing.lg),
        )
        Text(text = "All Set!", style = MaterialTheme.typography.headlineLarge)
        Text(
            text = "Your personalized roadmap is ready. Let's start your placement journey.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Spacing.sm),
        )
        PrimaryButton(
            text = "Go to Home",
            onClick = onGoToHome,
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.xl),
        )
    }
}
