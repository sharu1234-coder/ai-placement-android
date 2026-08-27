package com.aiplacementos.app.ui.atscheck

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.aiplacementos.app.ui.theme.successColor
import com.aiplacementos.app.ui.theme.warningColor

/** score_category machine keys, matching AtsScoringConfig.SCORE_BANDS on the backend
 * exactly -- Android derives display label/color client-side, backend never sends copy. */
fun scoreBandLabel(scoreCategory: String): String = when (scoreCategory) {
    "excellent" -> "Excellent"
    "very_good" -> "Very Good"
    "good" -> "Good"
    "needs_improvement" -> "Needs Improvement"
    else -> "Needs Attention"
}

@Composable
fun scoreBandColor(scoreCategory: String): Color = when (scoreCategory) {
    "excellent", "very_good" -> successColor()
    "good" -> MaterialTheme.colorScheme.primary
    "needs_improvement" -> warningColor()
    else -> MaterialTheme.colorScheme.error
}

fun atsCategoryLabel(category: String): String = when (category) {
    "parsing" -> "ATS Compatibility"
    "structure" -> "Structure"
    "content" -> "Content Quality"
    "skills" -> "Skills"
    "experience" -> "Experience"
    "projects" -> "Projects"
    "contact" -> "Contact Info"
    "readability" -> "Readability"
    else -> category.replaceFirstChar { it.uppercase() }
}

fun sectionLabel(section: String): String = when (section) {
    "summary" -> "Summary"
    "skills" -> "Skills"
    "experience" -> "Experience"
    "education" -> "Education"
    "projects" -> "Projects"
    "certifications" -> "Certifications"
    "achievements" -> "Achievements"
    "languages" -> "Languages"
    "contact" -> "Contact Information"
    else -> section.replaceFirstChar { it.uppercase() }
}

@Composable
fun severityColor(severity: String): Color = when (severity) {
    "high" -> MaterialTheme.colorScheme.error
    "medium" -> warningColor()
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}
