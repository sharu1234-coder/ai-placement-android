package com.aiplacementos.app.ui.jobhunt

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import com.aiplacementos.app.ui.common.PrimaryButton
import com.aiplacementos.app.ui.common.SecondaryButton
import com.aiplacementos.app.ui.theme.Spacing

/**
 * Advanced search filters.
 *
 * Four of these -- employment type, experience level, salary and date posted -- are Premium on
 * the server. They are shown to everyone and labelled, rather than hidden or disabled, for two
 * reasons. The server is the only authority on entitlement, so a disabled control here would
 * be decoration rather than enforcement; and a student deciding whether to pay is better
 * served by seeing what they would get than by finding a blank space where it used to be.
 * Applying them without Premium returns the server's own upgrade response, which the results
 * screen already renders as a paywall rather than an error.
 *
 * Nothing here claims a provider supports a filter it does not. Salary and date-posted are
 * pushed down to the provider where it accepts them and applied by the matching engine where
 * it does not -- the backend decides, and this sheet never asserts either way.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobFiltersSheet(
    initial: JobFilters,
    onApply: (JobFilters) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // rememberSaveable so a rotation mid-edit does not silently discard what was typed.
    var keywords by rememberSaveable { mutableStateOf(initial.keywords.orEmpty()) }
    var location by rememberSaveable { mutableStateOf(initial.location.orEmpty()) }
    var salaryText by rememberSaveable { mutableStateOf(initial.salaryMin?.toString().orEmpty()) }
    var workMode by rememberSaveable { mutableStateOf(initial.workMode) }
    var employmentType by rememberSaveable { mutableStateOf(initial.employmentType) }
    var experienceLevel by rememberSaveable { mutableStateOf(initial.experienceLevel) }
    var maxDaysOld by rememberSaveable { mutableStateOf(initial.maxDaysOld) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.md)
                .padding(bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(
                text = "Filters",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.semantics { heading() },
            )

            OutlinedTextField(
                value = keywords,
                onValueChange = { keywords = it.take(200) },
                label = { Text("Role or keywords") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = location,
                onValueChange = { location = it.take(200) },
                label = { Text("Location") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            FilterGroup(
                title = "Work mode",
                options = listOf("remote" to "Remote", "hybrid" to "Hybrid", "onsite" to "On-site"),
                selected = workMode,
                onSelect = { workMode = it },
            )
            FilterGroup(
                title = "Employment type",
                isPremium = true,
                options = listOf(
                    "full_time" to "Full-time",
                    "internship" to "Internship",
                    "contract" to "Contract",
                ),
                selected = employmentType,
                onSelect = { employmentType = it },
            )
            FilterGroup(
                title = "Experience level",
                isPremium = true,
                options = listOf(
                    "fresher" to "0-1 years",
                    "junior" to "1-3 years",
                    "mid" to "3-6 years",
                    "senior" to "6+ years",
                ),
                selected = experienceLevel,
                onSelect = { experienceLevel = it },
            )
            FilterGroup(
                title = "Date posted",
                isPremium = true,
                options = listOf("1" to "Today", "7" to "This week", "30" to "This month"),
                selected = maxDaysOld?.toString(),
                onSelect = { maxDaysOld = it?.toIntOrNull() },
            )

            Column {
                PremiumLabelledTitle("Minimum salary", isPremium = true)
                OutlinedTextField(
                    value = salaryText,
                    // Digits only, and capped, so a pasted essay or a minus sign cannot reach a
                    // backend field validated as a non-negative integer.
                    onValueChange = { input -> salaryText = input.filter { it.isDigit() }.take(9) },
                    label = { Text("Per year, e.g. 600000") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                SecondaryButton(
                    text = "Clear all",
                    onClick = onClear,
                    modifier = Modifier.weight(1f),
                )
                PrimaryButton(
                    text = "Apply",
                    onClick = {
                        onApply(
                            JobFilters(
                                keywords = keywords.trim().ifBlank { null },
                                location = location.trim().ifBlank { null },
                                workMode = workMode,
                                employmentType = employmentType,
                                experienceLevel = experienceLevel,
                                salaryMin = salaryText.toIntOrNull(),
                                maxDaysOld = maxDaysOld,
                            )
                        )
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * A single-choice chip group where tapping the selected chip clears it -- a filter you cannot
 * un-set is a trap, and there is no "Any" chip to spend a row on.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterGroup(
    title: String,
    options: List<Pair<String, String>>,
    selected: String?,
    onSelect: (String?) -> Unit,
    isPremium: Boolean = false,
) {
    Column {
        PremiumLabelledTitle(title, isPremium)
        Row(
            modifier = Modifier.padding(top = Spacing.xs),
            // chipGap (not sm) between these -- see Spacing.chipGap's own doc comment.
            horizontalArrangement = Arrangement.spacedBy(Spacing.chipGap),
        ) {
            options.forEach { (value, label) ->
                val isSelected = selected == value
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelect(if (isSelected) null else value) },
                    label = { Text(label) },
                    // Material3 draws a tick in the leading slot for a selected FilterChip, so
                    // selection is already carried by shape as well as by colour.
                )
            }
        }
    }
}

@Composable
private fun PremiumLabelledTitle(title: String, isPremium: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.semantics { heading() },
        )
        if (isPremium) {
            Text(
                text = "Premium",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = Spacing.sm),
            )
        }
    }
}
