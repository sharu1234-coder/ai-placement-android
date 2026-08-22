package com.aiplacementos.app.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.aiplacementos.app.ui.common.SecondaryButton
import com.aiplacementos.app.ui.common.UiState
import com.aiplacementos.app.ui.common.networkModule
import com.aiplacementos.app.ui.theme.Spacing

@Composable
fun ProfileScreen(onSelectRole: () -> Unit) {
    val network = networkModule()
    val viewModel: ProfileViewModel = viewModel(
        factory = viewModelFactory { initializer { ProfileViewModel(network.profileRepository) } },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    when (val current = state) {
        is UiState.Loading -> LoadingState(modifier = Modifier.fillMaxSize())
        is UiState.Empty -> LoadingState(modifier = Modifier.fillMaxSize())
        is UiState.Error -> ErrorState(message = current.message, modifier = Modifier.fillMaxSize())
        is UiState.Success<ProfileDto> -> ProfileForm(
            profile = current.data,
            onSelectRole = onSelectRole,
            onSave = viewModel::save,
        )
    }
}

@Composable
private fun ProfileForm(
    profile: ProfileDto,
    onSelectRole: () -> Unit,
    onSave: (String?, String?, Int?) -> Unit,
) {
    var fullName by remember(profile.id) { mutableStateOf(profile.fullName.orEmpty()) }
    var college by remember(profile.id) { mutableStateOf(profile.college.orEmpty()) }
    var graduationYear by remember(profile.id) {
        mutableStateOf(profile.graduationYear?.toString().orEmpty())
    }

    LaunchedEffect(profile) {
        fullName = profile.fullName.orEmpty()
        college = profile.college.orEmpty()
        graduationYear = profile.graduationYear?.toString().orEmpty()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(text = "Profile", style = MaterialTheme.typography.headlineSmall)

        OutlinedTextField(
            value = fullName,
            onValueChange = { fullName = it },
            label = { Text("Full name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = college,
            onValueChange = { college = it },
            label = { Text("College") },
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

        Text(
            text = "Target role: ${profile.targetRoleId?.let { "selected" } ?: "not selected"}",
            style = MaterialTheme.typography.bodyMedium,
        )
        SecondaryButton(
            text = "Select target role",
            onClick = onSelectRole,
            modifier = Modifier.fillMaxWidth(),
        )

        PrimaryButton(
            text = "Save",
            onClick = {
                onSave(
                    fullName.ifBlank { null },
                    college.ifBlank { null },
                    graduationYear.toIntOrNull(),
                )
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
