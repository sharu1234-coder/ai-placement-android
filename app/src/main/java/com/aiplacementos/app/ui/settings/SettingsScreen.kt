package com.aiplacementos.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.aiplacementos.app.ui.common.AppCard
import com.aiplacementos.app.ui.common.PrimaryButton
import com.aiplacementos.app.ui.common.SecondaryButton
import com.aiplacementos.app.ui.common.UiState
import com.aiplacementos.app.ui.common.networkModule
import com.aiplacementos.app.ui.theme.Spacing

@Composable
fun SettingsScreen(onOpenPrivacy: () -> Unit, onOpenTerms: () -> Unit, onLoggedOut: () -> Unit) {
    val network = networkModule()
    val viewModel: SettingsViewModel = viewModel(
        factory = viewModelFactory {
            initializer { SettingsViewModel(network.authRepository, network.profileRepository) }
        },
    )
    val deleteAccountState by viewModel.deleteAccountState.collectAsStateWithLifecycle()

    var showDeleteDialog by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(text = "Settings", style = MaterialTheme.typography.headlineSmall)

        AppCard(modifier = Modifier.fillMaxWidth()) {
            Text(text = "Privacy", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "See what data we collect and how it's used.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = Spacing.xs, bottom = Spacing.sm),
            )
            SecondaryButton(text = "View privacy policy", onClick = onOpenPrivacy, modifier = Modifier.fillMaxWidth())
        }

        AppCard(modifier = Modifier.fillMaxWidth()) {
            Text(text = "Terms of Service", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "What you're agreeing to by using the app.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = Spacing.xs, bottom = Spacing.sm),
            )
            SecondaryButton(text = "View terms of service", onClick = onOpenTerms, modifier = Modifier.fillMaxWidth())
        }

        PrimaryButton(
            text = "Log out",
            onClick = { viewModel.logout(onLoggedOut) },
            modifier = Modifier.fillMaxWidth(),
        )

        AppCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Delete account",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error,
            )
            Text(
                text = "Permanently deletes your profile, resumes, assessments, interviews, " +
                    "and roadmap. This can't be undone.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = Spacing.xs, bottom = Spacing.sm),
            )
            SecondaryButton(
                text = "Delete my account",
                onClick = {
                    password = ""
                    viewModel.resetDeleteAccountState()
                    showDeleteDialog = true
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (showDeleteDialog) {
        val isLoading = deleteAccountState is UiState.Loading
        AlertDialog(
            onDismissRequest = { if (!isLoading) showDeleteDialog = false },
            title = { Text("Delete account?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text("This permanently deletes all your data and can't be undone. Enter your password to confirm.")
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        enabled = !isLoading,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (deleteAccountState is UiState.Error) {
                        Text(
                            text = (deleteAccountState as UiState.Error).message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.deleteAccount(password, onDeleted = onLoggedOut) },
                    enabled = !isLoading && password.isNotBlank(),
                ) {
                    Text(if (isLoading) "Deleting..." else "Delete permanently")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }, enabled = !isLoading) {
                    Text("Cancel")
                }
            },
        )
    }
}
