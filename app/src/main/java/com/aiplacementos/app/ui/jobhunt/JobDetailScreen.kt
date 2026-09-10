package com.aiplacementos.app.ui.jobhunt

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material.icons.filled.WorkHistory
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aiplacementos.app.data.remote.JobListingDto
import com.aiplacementos.app.data.remote.JobHuntMatchDto
import com.aiplacementos.app.ui.common.AppCard
import com.aiplacementos.app.ui.common.DetailTopBar
import com.aiplacementos.app.ui.common.ErrorState
import com.aiplacementos.app.ui.common.PrimaryButton
import com.aiplacementos.app.ui.common.SecondaryButton
import com.aiplacementos.app.ui.common.SkeletonList
import com.aiplacementos.app.ui.common.networkModule
import com.aiplacementos.app.ui.theme.Spacing
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobDetailScreen(
    listingId: Int,
    onBack: () -> Unit,
    /** Where a gap goes next. Defaulted to a no-op so a caller that has no roadmap to offer
     * simply does not show the button, rather than showing one that goes nowhere. */
    onOpenRoadmap: (() -> Unit)? = null,
    /** Opens the full per-skill breakdown. Null on a caller that has nowhere to send them. */
    onOpenSkillAnalysis: (() -> Unit)? = null,
) {
    val network = networkModule()
    val viewModel: JobDetailViewModel = viewModel(
        factory = viewModelFactory {
            initializer { JobDetailViewModel(network.jobHuntRepository, listingId) }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    val actionError = (state as? JobDetailUiState.Success)?.actionError
    LaunchedEffect(actionError) {
        if (actionError != null) {
            snackbarHostState.showSnackbar(actionError)
            viewModel.clearActionError()
        }
    }

    Scaffold(
        topBar = { DetailTopBar(title = "Job Details", onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when (val current = state) {
            is JobDetailUiState.Loading ->
                SkeletonList(count = 3, modifier = Modifier.fillMaxSize().padding(padding).padding(Spacing.md))

            is JobDetailUiState.Failed -> ErrorState(
                message = current.failure.message,
                icon = when {
                    current.failure is JobSearchFailure.Transient && current.failure.isNetworkError ->
                        Icons.Filled.WifiOff
                    else -> Icons.Filled.ErrorOutline
                },
                onRetry = if (current.failure.isRetryable) viewModel::load else null,
                modifier = Modifier.fillMaxSize().padding(padding),
            )

            is JobDetailUiState.Success -> JobDetailContent(
                listing = current.job.listing,
                match = current.job.match,
                isSaved = current.savedJobId != null,
                isSaving = current.isSaving,
                onToggleSave = viewModel::toggleSave,
                onOpenRoadmap = onOpenRoadmap,
                onOpenSkillAnalysis = onOpenSkillAnalysis,
                onApply = {
                    // Opens the provider's own listing in a browser. The app never hosts an
                    // application form, and never claims to have submitted anything on the
                    // student's behalf.
                    val intent = Intent(Intent.ACTION_VIEW, current.job.listing.url.toUri())
                    try {
                        context.startActivity(intent)
                        viewModel.markApplied()
                    } catch (_: ActivityNotFoundException) {
                        // No browser to handle it. Silently marking it applied would be a lie,
                        // so nothing is recorded -- but the tap has to say something, or the
                        // button just looks broken.
                        viewModel.reportNoBrowser()
                    }
                },
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun JobDetailContent(
    listing: JobListingDto,
    match: JobHuntMatchDto,
    isSaved: Boolean,
    isSaving: Boolean,
    onToggleSave: () -> Unit,
    onApply: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenRoadmap: (() -> Unit)? = null,
    onOpenSkillAnalysis: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Column {
            Text(
                text = listing.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() },
            )
            val subtitle = listOfNotNull(listing.company, listing.location).joinToString(" · ")
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        FactTiles(listing)

        if (match.matchedSkills.isNotEmpty() || match.missingSkills.isNotEmpty()) {
            Column {
                Text(
                    text = "Skills",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.semantics { heading() },
                )
                if (match.matchedSkills.isNotEmpty()) {
                    SkillChipRow(match.matchedSkills, modifier = Modifier.padding(top = Spacing.xs))
                }
            }
        }

        if (!listing.description.isNullOrBlank()) {
            Column {
                Text(
                    text = "Job description",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    // Rendered as plain text. Provider descriptions are untrusted third-party
                    // content, so no HTML or markup of theirs is ever interpreted here.
                    text = listing.description,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
        }

        WhyYouFitCard(match)

        // Answers "what can I actually prove?" -- the difference between a skill listed on a
        // resume and one demonstrated. Placed after the fit explanation and before the gaps
        // so the student reads their strongest ground first.
        SkillProvenanceCard(match.skillProvenance)

        if (onOpenSkillAnalysis != null) {
            // The card above is the summary; this opens the per-skill breakdown with the
            // recruiter's-eye badges and the route into the Roadmap.
            SecondaryButton(
                text = "See full skill match analysis",
                onClick = onOpenSkillAnalysis,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (match.missingSkills.isNotEmpty()) {
            GapsCard(match, onOpenRoadmap)
        }

        if (listing.isStale) {
            Text(
                text = "We last checked this listing a while ago — it may no longer be open.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            SecondaryButton(
                text = if (isSaved) "Saved" else "Save",
                onClick = onToggleSave,
                enabled = !isSaving,
                modifier = Modifier.weight(1f),
            )
            PrimaryButton(
                text = "Apply Now",
                onClick = onApply,
                modifier = Modifier.weight(1f),
            )
        }

        Text(
            text = "Applying opens the employer's listing in your browser.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Salary / experience / type tiles.
 *
 * Each tile appears only when the provider supplied that fact, and a predicted salary is
 * explicitly labelled an estimate -- presenting a modelled figure as an advertised one would
 * be exactly the fabricated content this feature forbids.
 */
@Composable
private fun FactTiles(listing: JobListingDto) {
    val salary = formatSalary(listing)
    val tiles = listOfNotNull(
        salary?.let { Triple(Icons.Filled.Payments, "Salary", it) },
        listing.seniority?.let { Triple(Icons.Filled.WorkHistory, "Experience", seniorityLabel(it)) },
        listing.employmentType?.let { Triple(Icons.Filled.Schedule, "Type", employmentTypeLabel(it)) },
    )
    if (tiles.isEmpty()) return

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        tiles.forEach { (icon, label, value) ->
            AppCard(modifier = Modifier.weight(1f)) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * "Why you're a great fit".
 *
 * Reasons come straight from the backend's scoring components, so this section can never claim
 * a strength the score did not actually count. The unknown factors are shown in the same card
 * rather than tucked away -- a student comparing a 90% with three unknowns against a 70% with
 * none deserves to see that difference at the point of decision.
 */
@Composable
private fun WhyYouFitCard(match: JobHuntMatchDto) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Why you're a fit",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = "${match.matchPercent()}% match",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (match.reasons.isEmpty()) {
            Text(
                text = match.rationale.ifBlank {
                    "There isn't enough information about this listing to explain a match."
                },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = Spacing.sm),
            )
        } else {
            match.reasons.forEach { reason ->
                Row(
                    modifier = Modifier.padding(top = Spacing.sm),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = reason,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = Spacing.sm),
                    )
                }
            }
        }

        if (match.unknownFactors.isNotEmpty()) {
            HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.sm))
            Text(
                text = "What we couldn't check",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            match.unknownFactors.forEach { factor ->
                Row(
                    modifier = Modifier.padding(top = Spacing.xs),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        imageVector = Icons.Filled.HelpOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = factor,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = Spacing.sm),
                    )
                }
            }
        }
    }
}

@Composable
private fun GapsCard(match: JobHuntMatchDto, onOpenRoadmap: (() -> Unit)? = null) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Your gaps",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = "Requirements this listing names that we can't yet evidence for you.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        match.missingSkills.forEach { skill ->
            Row(
                modifier = Modifier.padding(top = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = skill,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = Spacing.sm),
                )
            }
        }
        if (onOpenRoadmap != null) {
            // A gap that only names itself leaves the student with a problem and no next
            // step. The roadmap is where closing one is actually planned, so the card ends by
            // pointing there instead of stopping at the diagnosis.
            SecondaryButton(
                text = "Plan how to close these",
                onClick = onOpenRoadmap,
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
            )
        }
    }
}

fun seniorityLabel(seniority: String): String = when (seniority) {
    "fresher" -> "0-1 years"
    "intern" -> "Internship"
    "junior" -> "1-3 years"
    "mid" -> "3-6 years"
    "senior" -> "6+ years"
    "lead" -> "Lead"
    else -> seniority
}

/**
 * Returns null when the provider gave no usable salary, so the tile is omitted rather than
 * showing a zero or a range the employer never advertised.
 */
fun formatSalary(listing: JobListingDto): String? {
    val min = listing.salaryMin
    val max = listing.salaryMax
    if (min == null && max == null) return null

    val currency = when (listing.salaryCurrency) {
        "INR" -> "₹"
        "USD" -> "$"
        "GBP" -> "£"
        "EUR" -> "€"
        null -> ""
        else -> "${listing.salaryCurrency} "
    }
    val body = when {
        min != null && max != null -> "$currency${compact(min)}-${compact(max)}"
        min != null -> "$currency${compact(min)}+"
        else -> "Up to $currency${compact(max!!)}"
    }
    // The period belongs to the figure, so it goes on before "est." qualifies the whole thing:
    // "₹500/hr est.", not "₹500 est./hr".
    val figure = body + salaryPeriodSuffix(listing.salaryPeriod)
    // "est." is not decoration: it is the difference between a figure the employer published
    // and one the provider modelled.
    return if (listing.salaryIsPredicted) "$figure est." else figure
}

/**
 * "/hr", "/mo", "/wk", "/day" — or nothing at all for an annual figure.
 *
 * Careerjet advertises salaries by hour, day, week, month or year and sends the period
 * alongside the number. Dropping it made "₹500 an hour" and "₹500 a year" render identically
 * as "₹500" — the same ~2000x misstatement the backend adapter refuses to make when it
 * declines to bucket an unrecognised period into "year".
 *
 * A null period is left unsuffixed rather than labelled: it means the provider did not say,
 * and writing "per year" there would be the guess this exists to avoid. Adzuna, the other
 * provider, always sends "year", so unsuffixed is correct for everything it returns.
 */
fun salaryPeriodSuffix(period: String?): String = when (period) {
    "hour" -> "/hr"
    "day" -> "/day"
    "week" -> "/wk"
    "month" -> "/mo"
    // "year" and anything unrecognised read as the annual figure this market quotes by default.
    else -> ""
}

/** Indian-convention compaction: salaries here are quoted in lakhs per annum. */
private fun compact(value: Int): String = when {
    value >= 100_000 -> {
        val lakhs = value / 100_000.0
        // Locale.ROOT, not the device locale: String.format would otherwise render the
        // decimal separator as a comma on a French or German handset, so the same salary
        // would read "7,5L" here and "7.5L" everywhere the app builds the string itself.
        if (lakhs % 1.0 == 0.0) "${lakhs.toInt()}L" else String.format(Locale.ROOT, "%.1fL", lakhs)
    }
    value >= 1_000 -> "${value / 1_000}K"
    else -> value.toString()
}
