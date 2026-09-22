package com.aiplacementos.app.ui.careerpath

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.aiplacementos.app.ui.common.SkillBar

/**
 * One row of the Job Readiness breakdown, for a signal that may not have been measured yet.
 *
 * Three of the five readiness signals (resume, practice, interview) are null until the student
 * has actually done the thing -- that is the backend's deliberate "no data yet" vs. "measured
 * zero" distinction. Rendering a null through [SkillBar] would need a number, and the only
 * number available would be 0, which reads as "you scored nothing" rather than "you haven't
 * started". Those are different sentences and only one of them is true.
 *
 * So: a real value renders as the ordinary [SkillBar] every other row uses, unchanged. A
 * missing one renders the label with "Not started yet" where the percentage would sit, and no
 * bar at all -- an empty track is still a bar claiming a reading of zero. The wording matches
 * what CareerPathInterviewPrepScreen already says for the same situation.
 *
 * Deliberately a thin wrapper in this package rather than a change to SkillBar: the design
 * system component is used in a dozen places that always have a number, and widening it to
 * take a nullable percent would push this decision onto every one of them.
 */
@Composable
internal fun ReadinessBar(label: String, percent: Int?, modifier: Modifier = Modifier) {
    if (percent != null) {
        SkillBar(label = label, percent = percent, modifier = modifier)
        return
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = "Not started yet",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
