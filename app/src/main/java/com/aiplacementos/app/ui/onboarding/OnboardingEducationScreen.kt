package com.aiplacementos.app.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aiplacementos.app.data.remote.ProfileDto
import com.aiplacementos.app.ui.common.ErrorState
import com.aiplacementos.app.ui.common.LoadingState
import com.aiplacementos.app.ui.common.PrimaryButton
import com.aiplacementos.app.ui.common.StepProgressIndicator
import com.aiplacementos.app.ui.common.UiState
import com.aiplacementos.app.ui.common.networkModule
import com.aiplacementos.app.ui.theme.Spacing

private val DEGREES = listOf("B.Tech", "B.E.", "M.Tech", "MCA", "BCA", "MBA", "B.Sc", "M.Sc", "Diploma", "Other")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingEducationScreen(onNext: () -> Unit) {
    val network = networkModule()
    val viewModel: OnboardingEducationViewModel = viewModel(
        factory = viewModelFactory { initializer { OnboardingEducationViewModel(network.profileRepository) } },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val saving by viewModel.saving.collectAsStateWithLifecycle()

    var college by rememberSaveable { mutableStateOf("") }
    var degree by rememberSaveable { mutableStateOf("") }
    var branch by rememberSaveable { mutableStateOf("") }
    var graduationYear by rememberSaveable { mutableStateOf("") }
    var degreeMenuExpanded by remember { mutableStateOf(false) }
    var prefilled by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state) {
        val profile = (state as? UiState.Success<ProfileDto>)?.data
        if (profile != null && !prefilled) {
            college = profile.college.orEmpty()
            degree = profile.degree.orEmpty()
            branch = profile.branch.orEmpty()
            graduationYear = profile.graduationYear?.toString().orEmpty()
            prefilled = true
        }
    }

    when (val current = state) {
        is UiState.Loading -> LoadingState(modifier = Modifier.fillMaxSize())
        is UiState.Error -> ErrorState(
            message = current.message,
            onRetry = viewModel::load,
            modifier = Modifier.fillMaxSize(),
        )
        is UiState.Empty -> LoadingState(modifier = Modifier.fillMaxSize())
        is UiState.Success<ProfileDto> -> Column(
            // Without this the soft keyboard -- or simply a raised system font scale --
            // covers the Next button with no way to reach it, which strands a student
            // mid-signup.
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            OnboardingHeader(
                step = 2,
                total = 7,
                title = "Tell us about your education",
                subtitle = "Your college, degree and graduation year. All of it stays editable in your profile.",
            )
            OutlinedTextField(
                value = college,
                onValueChange = { college = it },
                label = { Text("College / University") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            ExposedDropdownMenuBox(
                expanded = degreeMenuExpanded,
                onExpandedChange = { degreeMenuExpanded = it },
            ) {
                OutlinedTextField(
                    value = degree,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Degree") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = degreeMenuExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                )
                ExposedDropdownMenu(
                    expanded = degreeMenuExpanded,
                    onDismissRequest = { degreeMenuExpanded = false },
                ) {
                    DEGREES.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option) },
                            onClick = {
                                degree = option
                                degreeMenuExpanded = false
                            },
                        )
                    }
                }
            }
            OutlinedTextField(
                value = branch,
                onValueChange = { branch = it },
                label = { Text("Branch / Department") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = graduationYear,
                onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) graduationYear = it },
                label = { Text("Graduation year") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            PrimaryButton(
                text = "Next",
                loading = saving,
                onClick = {
                    viewModel.save(college, degree, branch, graduationYear.toIntOrNull(), onSaved = onNext)
                },
                enabled = college.isNotBlank() && degree.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
