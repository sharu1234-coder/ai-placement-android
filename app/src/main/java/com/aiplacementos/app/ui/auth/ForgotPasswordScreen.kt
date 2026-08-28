package com.aiplacementos.app.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aiplacementos.app.ui.common.PrimaryButton
import com.aiplacementos.app.ui.common.UiState
import com.aiplacementos.app.ui.common.networkModule
import com.aiplacementos.app.ui.theme.Spacing

@Composable
fun ForgotPasswordScreen(onResetSuccess: () -> Unit, onBackToLogin: () -> Unit) {
    val network = networkModule()
    val viewModel: ForgotPasswordViewModel = viewModel(
        factory = viewModelFactory { initializer { ForgotPasswordViewModel(network.authRepository) } },
    )
    val step by viewModel.step.collectAsStateWithLifecycle()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    var email by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        when (step) {
            ForgotPasswordStep.REQUEST_CODE -> {
                Text(text = "Forgot password", style = MaterialTheme.typography.headlineSmall)
                Text(
                    text = "Enter your account email and we'll send you a reset code.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    singleLine = true,
                    isError = state is UiState.Error,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (state is UiState.Error) {
                    Text(
                        text = (state as UiState.Error).message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
                PrimaryButton(
                    text = if (state is UiState.Loading) "Sending..." else "Send reset code",
                    onClick = { viewModel.requestCode(email) },
                    enabled = state !is UiState.Loading && email.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            ForgotPasswordStep.RESET_PASSWORD -> {
                Text(text = "Enter reset code", style = MaterialTheme.typography.headlineSmall)
                Text(
                    text = "If an account exists for $email, a reset code was sent to it. " +
                        "Enter the code and a new password below.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text("Reset code") },
                    singleLine = true,
                    isError = state is UiState.Error,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    label = { Text("New password (min 8 characters)") },
                    singleLine = true,
                    isError = state is UiState.Error,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (state is UiState.Error) {
                    Text(
                        text = (state as UiState.Error).message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
                PrimaryButton(
                    text = if (state is UiState.Loading) "Resetting..." else "Reset password",
                    onClick = { viewModel.resetPassword(code, newPassword, onDone = onResetSuccess) },
                    enabled = state !is UiState.Loading && code.isNotBlank() && newPassword.length >= 8,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        TextButton(onClick = onBackToLogin) {
            Text("Back to log in")
        }
    }
}
