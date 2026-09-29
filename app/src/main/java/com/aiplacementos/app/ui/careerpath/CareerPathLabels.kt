package com.aiplacementos.app.ui.careerpath

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.aiplacementos.app.ui.theme.successColor
import com.aiplacementos.app.ui.theme.warningColor

/** phase_key machine keys, matching career_path.role_catalog.PHASE_ORDER on the backend
 * exactly -- Android derives display label/color client-side, backend never sends copy. */
fun phaseLabel(phaseKey: String): String = when (phaseKey) {
    "foundations" -> "Foundations"
    "core_skills" -> "Core Skills"
    "projects" -> "Projects"
    "interview_prep" -> "Interview Preparation"
    "job_ready" -> "Job Ready"
    else -> phaseKey.replace("_", " ").replaceFirstChar { it.uppercase() }
}

@Composable
fun statusColor(status: String): Color = when (status) {
    "completed" -> successColor()
    "in_progress" -> MaterialTheme.colorScheme.primary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

fun statusLabel(status: String): String = when (status) {
    "completed" -> "Completed"
    "in_progress" -> "In Progress"
    else -> "Not Started"
}

/**
 * Career Path / JD gap statuses.
 *
 * The "strong" wire value here does NOT mean what "Strong" means on the Skill Passport and
 * Career Twin. Those come from the Evidence Engine, where `strong` requires evidence from two
 * or more sources with at least one the student did not simply assert about themselves. This
 * one means only "this skill appears on your resume and nothing has flagged it as weak" --
 * a claim with no contradiction, not proof.
 *
 * Both were rendered as a green "Strong" badge, so the same word promised two very different
 * things and the weaker one borrowed the stronger one's credibility -- on the same student's
 * screens, for the same skill. The wire value is left alone (it is a stable API contract that
 * several screens filter on); what changes is that this label now says what it actually
 * knows, and takes a neutral informational colour rather than success green.
 */
fun skillGapStatusLabel(status: String): String = when (status) {
    "strong" -> "On Your Resume"
    "needs_improvement" -> "Needs Improvement"
    "missing" -> "Missing"
    "recommended" -> "Recommended"
    else -> status.replaceFirstChar { it.uppercase() }
}

@Composable
fun skillGapStatusColor(status: String): Color = when (status) {
    // Deliberately not successColor(): see skillGapStatusLabel -- a resume mention is not a
    // verified strength, and colouring it like one is the same overclaim in a second channel.
    "strong" -> MaterialTheme.colorScheme.primary
    "needs_improvement" -> warningColor()
    "missing" -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.primary
}

fun levelLabel(level: String): String = level.replaceFirstChar { it.uppercase() }

fun interviewCategoryLabel(category: String): String = when (category) {
    "technical" -> "Technical"
    "hr" -> "HR"
    "project" -> "Project-Based"
    "behavioral" -> "Behavioral"
    "role_specific" -> "Role-Specific"
    else -> category.replaceFirstChar { it.uppercase() }
}

/**
 * How much of the Job Readiness average was actually measured.
 *
 * `overall_percent` averages only the signals that have a reading, so 78% from two signals and
 * 78% from five are the same number standing on very different evidence. The breakdown below
 * the ring already marks the untouched ones "Not started yet", but the headline figure carried
 * no such mark, and the Career Path home card shows that figure with no breakdown at all.
 *
 * Says "measured from N of M signals", never "N% confident": the percentage is an average of
 * what was measured, not a confidence in the result, and conflating the two on a screen a
 * student uses to decide what to do next is the exact overclaim Phase 2F removed elsewhere.
 */
fun readinessCoverageLabel(contributingSignals: Int, totalSignals: Int): String = when {
    totalSignals <= 0 || contributingSignals <= 0 -> "No signals measured yet"
    contributingSignals >= totalSignals -> "Measured from all $totalSignals signals"
    else -> "Measured from $contributingSignals of $totalSignals signals"
}

fun generatedViaLabel(generatedVia: String): String? = when (generatedVia) {
    "ai_generated" -> "AI-assisted plan for this role"
    "fallback" -> "Starter plan -- customize as you learn more about this role"
    else -> null // "catalog" -- the common case, no disclosure needed
}
