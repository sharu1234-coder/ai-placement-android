package com.aiplacementos.app.ui.careergoal

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Every word this feature shows for a backend state, in one place.
 *
 * The wording rule is the whole point of the file: these labels report what was found, never
 * what it means about the person. "No evidence found" is a statement about this app's records.
 * "You don't have this skill" would be a statement about the student, and nothing here is
 * entitled to make one.
 *
 * The backend also sends a ready-made `verdictLabel` for each skill and that is what the detail
 * screen renders; these are for the places the client has to name a state itself.
 */

/** Preparation state -> what the student sees on a card. */
fun preparationStateLabel(state: String): String = when (state) {
    "not_analyzed" -> "Not analyzed yet"
    "not_started" -> "Not started yet"
    "in_progress" -> "In progress"
    "ready_for_review" -> "Ready for review"
    // An unknown state from a newer backend reads as the honest thing rather than crashing or
    // guessing something reassuring.
    else -> "In progress"
}

fun goalStatusLabel(status: String): String = when (status) {
    "active" -> "Active"
    "paused" -> "Paused"
    "completed" -> "Completed"
    "archived" -> "Archived"
    else -> status.replaceFirstChar { it.uppercase() }
}

/** Section headings for the three evidence buckets. */
const val ALIGNED_HEADING = "Evidence found"
const val NEEDS_ATTENTION_HEADING = "Limited evidence"
const val UNKNOWN_HEADING = "Nothing on file yet"

/**
 * What the empty/unknown bucket says underneath its heading.
 *
 * Deliberately about our records, not about the student: they may well have the skill and
 * simply not have shown it here yet, and the app cannot tell those apart.
 */
const val UNKNOWN_EXPLANATION =
    "This app hasn't seen anything for these yet. That isn't a judgement about you -- it just " +
        "means there's nothing here to point at in an interview."

/** Evidence source key -> a human name. Unknown sources fall back to the key itself rather than
 * being dropped, so a source added server-side still appears. */
fun evidenceSourceLabel(source: String): String = when (source) {
    "assessment" -> "Test result"
    "practice" -> "Practice"
    "project" -> "Project"
    "interview" -> "Interview"
    "recruiter_attack" -> "Recruiter Attack"
    "resume" -> "Resume"
    "external" -> "GitHub"
    else -> source.replace('_', ' ').replaceFirstChar { it.uppercase() }
}

fun unavailableSourceLabel(source: String): String = when (source) {
    "external" -> "GitHub isn't connected, so any public code you have isn't being counted yet."
    "resume" -> "No resume has been analyzed yet, so nothing from it is being counted."
    else -> "One evidence source couldn't be checked."
}

/** Severity is conveyed by a word as well as a colour -- colour alone is not an accessible
 * status signal, and this app's existing weakness labels already follow that rule. */
fun riskSeverityLabel(severity: String): String = when (severity) {
    "high" -> "Worth focusing on"
    else -> "Worth a look"
}

@Composable
fun riskSeverityColor(severity: String): Color = when (severity) {
    "high" -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}
