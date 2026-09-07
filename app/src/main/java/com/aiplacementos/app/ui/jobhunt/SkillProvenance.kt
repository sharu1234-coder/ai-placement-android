package com.aiplacementos.app.ui.jobhunt

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aiplacementos.app.data.remote.SkillProvenanceDto
import com.aiplacementos.app.ui.common.AppCard
import com.aiplacementos.app.ui.theme.Spacing

/**
 * "What can I actually prove?" — the four buckets, rendered.
 *
 * The distinction this screen exists to make is between a skill the student *listed* and one
 * they have *demonstrated*. Both used to render as an identical green chip, which quietly told
 * a student that typing "Kubernetes" on a resume was the same as having passed an assessment on
 * it. The backend now says which is which (via the same Evidence Engine that powers Career Twin
 * and the Skill Passport) and this file is careful never to collapse the two back together:
 * different icon, different colour, different heading, and the "on your resume" group carries
 * the word "listed" in its own subtitle so the difference survives a quick skim.
 *
 * Status strings are never re-derived here. The client receives a verdict; it does not compute
 * one.
 */

/** The four buckets in the order they are shown: strongest ground first, then what's unknown. */
private val PROVENANCE_ORDER = listOf("verified_evidence", "on_resume", "missing", "unclear")

/** Group heading for each bucket. Pure, so it is unit-testable without a Compose runtime. */
fun provenanceGroupTitle(status: String): String = when (status) {
    "verified_evidence" -> "Verified evidence"
    "on_resume" -> "On your resume"
    "missing" -> "Missing for this role"
    "unclear" -> "We can't tell yet"
    else -> status
}

/**
 * One line under each heading saying what the group means.
 *
 * "Unclear" gets the most careful wording of the four: it appears when we hold no skills for
 * the student at all, and the honest statement is that we have not asked — not that they lack
 * anything.
 */
fun provenanceGroupSubtitle(status: String): String = when (status) {
    "verified_evidence" -> "You've demonstrated these in assessments, projects or interviews."
    "on_resume" -> "You've listed these. Nothing has confirmed them yet."
    "missing" -> "This job asks for these and they aren't on your profile."
    "unclear" -> "Add a resume or your skills and we can check these against the job."
    else -> ""
}

/**
 * Groups provenance items into display order, dropping empty buckets.
 *
 * Separated from the composable so the ordering rule is testable on its own — the order is
 * load-bearing, not cosmetic: leading with gaps would make a well-matched job read as a
 * rejection.
 */
fun groupProvenance(items: List<SkillProvenanceDto>): List<Pair<String, List<SkillProvenanceDto>>> =
    PROVENANCE_ORDER.mapNotNull { status ->
        items.filter { it.status == status }.takeIf { it.isNotEmpty() }?.let { status to it }
    }

@Composable
fun SkillProvenanceCard(items: List<SkillProvenanceDto>, modifier: Modifier = Modifier) {
    val groups = groupProvenance(items)
    if (groups.isEmpty()) return

    AppCard(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "What you can prove",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.semantics { heading() },
        )
        groups.forEach { (status, group) ->
            Column(modifier = Modifier.padding(top = Spacing.md)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = provenanceIcon(status),
                        // The heading text immediately beside this already says which group
                        // it is, so announcing the icon too would repeat every heading twice.
                        contentDescription = null,
                        tint = provenanceTint(status),
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = provenanceGroupTitle(status),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .padding(start = Spacing.sm)
                            .semantics { heading() },
                    )
                }
                Text(
                    text = provenanceGroupSubtitle(status),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
                Row(
                    modifier = Modifier.padding(top = Spacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.chipGap),
                ) {
                    group.take(6).forEach { item ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(Spacing.sm),
                        ) {
                            Text(
                                text = item.skill,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(
                                    horizontal = Spacing.sm,
                                    vertical = Spacing.xs,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Status is never conveyed by colour alone — each bucket has a distinct icon as well as a
 * distinct tint, so the four groups stay distinguishable to a colour-blind reader and in
 * greyscale.
 */
@Composable
private fun provenanceIcon(status: String): ImageVector = when (status) {
    "verified_evidence" -> Icons.Filled.Verified
    "on_resume" -> Icons.Outlined.Description
    "missing" -> Icons.Filled.RadioButtonUnchecked
    else -> Icons.Filled.HelpOutline
}

@Composable
private fun provenanceTint(status: String): Color = when (status) {
    "verified_evidence" -> MaterialTheme.colorScheme.primary
    "missing" -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}
