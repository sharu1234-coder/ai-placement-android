package com.aiplacementos.app.ui.atscheck

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aiplacementos.app.ui.common.AppCard
import com.aiplacementos.app.ui.common.PrimaryButton
import com.aiplacementos.app.ui.common.SecondaryButton
import com.aiplacementos.app.ui.theme.Spacing

private val ANALYZED_ON = listOf(
    "ATS Compatibility",
    "Content Quality",
    "Professional Formatting",
    "Skills & Experience Strength",
    "Actionable Improvement Tips",
)

@Composable
fun AtsCheckLandingScreen(
    onCheckMyResume: () -> Unit,
    onUploadNewResume: () -> Unit,
    onOpenJobMatchPrep: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item {
            Icon(
                imageVector = Icons.Filled.DocumentScanner,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(56.dp),
            )
        }
        item {
            Text(text = "Check Your Resume ATS Readiness", style = MaterialTheme.typography.headlineSmall)
            Text(
                text = "See how ready your resume is for Applicant Tracking Systems.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
        item {
            Text(
                text = "Upload your resume and get a detailed ATS health report, formatting check, " +
                    "content analysis, and actionable improvement tips -- no job description required.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        item {
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Your resume is analyzed on:",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Column(modifier = Modifier.padding(top = Spacing.sm)) {
                    ANALYZED_ON.forEach { label ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 2.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(start = Spacing.sm),
                            )
                        }
                    }
                }
            }
        }
        item {
            PrimaryButton(text = "Check My Resume", onClick = onCheckMyResume, modifier = Modifier.fillMaxWidth())
        }
        item {
            SecondaryButton(text = "Upload New Resume", onClick = onUploadNewResume, modifier = Modifier.fillMaxWidth())
        }
        item {
            Text(
                text = "Applying for a specific job? Analyze a Job Description →",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.sm)
                    .clickable(onClick = onOpenJobMatchPrep),
            )
        }
    }
}
