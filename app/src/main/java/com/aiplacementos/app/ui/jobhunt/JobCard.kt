package com.aiplacementos.app.ui.jobhunt

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aiplacementos.app.data.remote.JobListingDto
import com.aiplacementos.app.data.remote.JobHuntMatchDto
import com.aiplacementos.app.ui.common.AppCard
import com.aiplacementos.app.ui.theme.Spacing

/**
 * One job in a list.
 *
 * Every element here is conditional on the provider having actually supplied the underlying
 * fact. Location, work mode, posted date and salary each disappear entirely when null rather
 * than rendering a placeholder -- a card that always shows "Posted recently" would be inventing
 * content, which is the one thing this feature must never do. That is why the row below is
 * assembled from a filtered list instead of a fixed template.
 */
@Composable
fun JobCard(
    listing: JobListingDto,
    match: JobHuntMatchDto,
    isSaved: Boolean,
    onOpen: () -> Unit,
    onToggleSave: () -> Unit,
    modifier: Modifier = Modifier,
    isSaving: Boolean = false,
    /** Null on surfaces where hiding a job makes no sense -- the saved list and the tracker,
     * where the student already chose to keep it. */
    onDismiss: (() -> Unit)? = null,
) {
    AppCard(modifier = modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Row(verticalAlignment = Alignment.Top) {
            CompanyAvatar(listing.company)
            Column(modifier = Modifier.weight(1f).padding(start = Spacing.sm)) {
                Text(
                    text = listing.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                if (listing.company != null) {
                    Text(
                        text = listing.company,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            MatchBadge(match)
            if (onDismiss != null) {
                IconButton(onClick = onDismiss, enabled = !isSaving) {
                    Icon(
                        Icons.Filled.Close,
                        // Says what happens, not just what the glyph is.
                        contentDescription = "Hide this job",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        val facts = listOfNotNull(
            listing.location?.let { Icons.Filled.LocationOn to it },
            listing.workMode?.let { Icons.Filled.Business to workModeLabel(it) },
            listing.employmentType?.let { Icons.Filled.Schedule to employmentTypeLabel(it) },
        )
        if (facts.isNotEmpty()) {
            Row(
                modifier = Modifier.padding(top = Spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                facts.forEach { (icon, label) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = Spacing.xs),
                        )
                    }
                }
            }
        }

        val fitLabel = rankingLabelText(match.rankingLabel)
        if (fitLabel != null) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(Spacing.sm),
                modifier = Modifier.padding(top = Spacing.sm),
            ) {
                Text(
                    text = fitLabel,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                )
            }
        }

        if (match.matchedSkills.isNotEmpty()) {
            SkillChipRow(
                skills = match.matchedSkills.take(3),
                modifier = Modifier.padding(top = Spacing.sm),
            )
        }
        if (match.verifiedSkillCount > 0) {
            // Only stated when there is something to state. "0 verified" would read as a
            // judgement on the student rather than on how much evidence we hold.
            Text(
                text = "${match.verifiedSkillCount} of these are backed by verified evidence",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                // Only rendered when the provider gave a real posted date.
                val posted = listing.postedAt?.let { relativePostedLabel(it) }
                if (posted != null) {
                    Text(
                        text = posted,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (listing.isStale) {
                    // Honest rather than hidden: the cached copy has outlived its provider's
                    // retention window, so it may no longer be open.
                    Text(
                        text = "May be out of date",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            IconButton(onClick = onToggleSave, enabled = !isSaving) {
                Icon(
                    imageVector = if (isSaved) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                    contentDescription = if (isSaved) {
                        "Remove ${listing.title} from saved jobs"
                    } else {
                        "Save ${listing.title}"
                    },
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/**
 * The match percentage.
 *
 * The visible text is a bare "95% Match", but a screen reader gets the full sentence including
 * what the number is actually based on -- a percentage alone is close to meaningless read out
 * of context, and the rationale is the part that helps a student decide.
 */
@Composable
private fun MatchBadge(match: JobHuntMatchDto) {
    val percent = match.matchPercent()
    val spoken = buildString {
        append("$percent percent match")
        if (match.rationale.isNotBlank()) append(". ${match.rationale}")
        if (match.unknownFactors.isNotEmpty()) {
            append(". ${match.unknownFactors.size} factors could not be assessed")
        }
    }
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(Spacing.sm),
        modifier = Modifier.semantics { contentDescription = spoken },
    ) {
        Text(
            text = "$percent% Match",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier
                .padding(horizontal = Spacing.sm, vertical = Spacing.xs)
                // The badge already carries the full spoken description above; without this the
                // reader would announce "95% Match" a second time.
                .clearAndSetSemantics {},
        )
    }
}

/**
 * The card's fit label, or null when the backend had no basis for one.
 *
 * Deliberately not "95% likely to get hired" or anything of that shape: each of these
 * describes how well the advert matched the student's stated target and evidence, which is
 * the only thing actually measured. "Best match" means top of this result set — the backend
 * only ever sets it on rank 0 of a ranked search, never on a job opened on its own.
 */
fun rankingLabelText(label: String?): String? = when (label) {
    "best_match" -> "Best match"
    "strong_fit" -> "Strong fit"
    "stretch_opportunity" -> "Stretch opportunity"
    "skill_build_opportunity" -> "Skill-build opportunity"
    // An unrecognised value from a newer backend is dropped rather than shown raw: a chip
    // reading "great_fit_v2" is worse than no chip.
    else -> null
}

@Composable
fun SkillChipRow(skills: List<String>, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(Spacing.chipGap)) {
        skills.forEach { skill ->
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(Spacing.sm),
            ) {
                Text(
                    text = skill,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                )
            }
        }
    }
}

/** No logo-fetching exists (and fetching remote images would need a network permission story
 * plus a CDN allowlist), so a deterministic initial is a real, always-correct stand-in rather
 * than a broken image placeholder. */
@Composable
private fun CompanyAvatar(company: String?) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(Spacing.sm),
        modifier = Modifier.size(40.dp),
    ) {
        Column(
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val initial = company?.trim()?.firstOrNull()?.uppercaseChar()
            if (initial != null) {
                Text(
                    text = initial.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.Business,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}

fun workModeLabel(workMode: String): String = when (workMode) {
    "remote" -> "Remote"
    "hybrid" -> "Hybrid"
    "onsite" -> "On-site"
    else -> workMode
}

fun employmentTypeLabel(type: String): String = when (type) {
    "full_time" -> "Full-time"
    "part_time" -> "Part-time"
    "contract" -> "Contract"
    "internship" -> "Internship"
    "temporary" -> "Temporary"
    else -> type
}

/**
 * "2 days ago" from an ISO-8601 timestamp, or null if it cannot be parsed.
 *
 * Returning null on an unparseable value (rather than falling back to "recently") keeps the
 * card silent about freshness it cannot actually establish. Parsed by hand rather than with
 * java.time so this stays usable on the app's minSdk without desugaring assumptions.
 */
fun relativePostedLabel(
    postedAt: String,
    nowEpochDay: Long = System.currentTimeMillis() / 86_400_000L,
): String? {
    val datePart = postedAt.substringBefore('T').split('-')
    if (datePart.size != 3) return null
    val year = datePart[0].toIntOrNull() ?: return null
    val month = datePart[1].toIntOrNull() ?: return null
    val day = datePart[2].toIntOrNull() ?: return null

    // Days since epoch via a civil-date conversion (Howard Hinnant's days_from_civil), which
    // avoids pulling in a date library for one label.
    val y = if (month <= 2) year - 1 else year
    val era = (if (y >= 0) y else y - 399) / 400
    val yoe = y - era * 400
    val doy = (153 * (if (month > 2) month - 3 else month + 9) + 2) / 5 + day - 1
    val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
    val postedEpochDay = era * 146_097L + doe - 719_468L

    return when (val days = nowEpochDay - postedEpochDay) {
        in Long.MIN_VALUE..-1L -> null // a future date tells us nothing useful
        0L -> "Today"
        1L -> "1 day ago"
        in 2L..30L -> "$days days ago"
        else -> "Over a month ago"
    }
}
