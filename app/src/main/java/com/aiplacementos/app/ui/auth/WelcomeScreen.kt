package com.aiplacementos.app.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aiplacementos.app.ui.common.Benefit
import com.aiplacementos.app.ui.common.BenefitRow
import com.aiplacementos.app.ui.common.BrandMark
import com.aiplacementos.app.ui.common.PRODUCT_TAGLINE
import com.aiplacementos.app.ui.common.PrimaryButton
import com.aiplacementos.app.ui.common.TargetIllustration
import com.aiplacementos.app.ui.theme.Spacing

@Composable
fun WelcomeScreen(onGetStarted: () -> Unit, onNavigateToLogin: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            // The benefits row pushes the sign-in link below a short screen at a raised font
            // scale, and a welcome screen whose sign-in link cannot be reached is a dead end
            // for every returning student.
            .verticalScroll(rememberScrollState())
            .padding(Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        TargetIllustration(
            modifier = Modifier.size(140.dp),
            ring = MaterialTheme.colorScheme.primary,
            accent = MaterialTheme.colorScheme.tertiary,
        )
        // The wordmark, not a 96dp rocket. A stock glyph in place of the brand is the first
        // thing a student sees, and it was also carrying the wrong tagline -- "Your Personal
        // Placement Partner" rather than the approved "Your Potential, Accelerated."
        BrandMark(
            modifier = Modifier.padding(top = Spacing.lg),
            showTagline = false,
        )
        Text(
            text = PRODUCT_TAGLINE,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.xs),
        )
        Text(
            text = "A personalised roadmap, practice, assessments and mock interviews " +
                "built around your resume and goals.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Spacing.md),
        )

        BenefitRow(
            modifier = Modifier.padding(top = Spacing.lg),
            benefits = listOf(
                Benefit(Icons.Filled.Route, "A plan built from your resume"),
                Benefit(Icons.Filled.RecordVoiceOver, "Spoken mock interviews"),
                Benefit(Icons.Filled.Insights, "Progress you can measure"),
            ),
        )

        PrimaryButton(
            text = "Get Started",
            onClick = onGetStarted,
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.lg),
        )
        TextButton(onClick = onNavigateToLogin) {
            Text("Already have an account? Sign In")
        }
    }
}
