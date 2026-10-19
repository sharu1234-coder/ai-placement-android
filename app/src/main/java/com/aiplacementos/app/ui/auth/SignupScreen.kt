package com.aiplacementos.app.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aiplacementos.app.ui.common.BrandMark
import com.aiplacementos.app.ui.common.EmailField
import com.aiplacementos.app.ui.common.PasswordField
import com.aiplacementos.app.ui.common.PrimaryButton
import com.aiplacementos.app.ui.common.UiState
import com.aiplacementos.app.ui.common.networkModule
import com.aiplacementos.app.ui.theme.Spacing

@Composable
fun SignupScreen(onSignupSuccess: suspend () -> Unit, onNavigateToLogin: () -> Unit) {
    val network = networkModule()
    val viewModel: SignupViewModel = viewModel(
        factory = viewModelFactory { initializer { SignupViewModel(network.authRepository) } },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    LaunchedEffect(state) {
        if (state is UiState.Success<*>) onSignupSuccess()
    }

    val busy = state is UiState.Loading
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        BrandMark(modifier = Modifier.padding(bottom = Spacing.sm))
        Text(
            text = "Create your account",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() },
        )

        EmailField(
            value = email,
            onValueChange = { email = it },
            isError = state is UiState.Error,
            enabled = !busy,
        )
        PasswordField(
            value = password,
            onValueChange = { password = it },
            isError = state is UiState.Error,
            enabled = !busy,
            // Stated up front rather than only as an error after the attempt fails.
            supporting = "At least 8 characters.",
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
            text = "Sign up",
            loading = busy,
            onClick = { viewModel.signup(email, password) },
            enabled = email.isNotBlank() && password.length >= 8,
            modifier = Modifier.fillMaxWidth(),
        )

        TextButton(onClick = onNavigateToLogin) {
            Text("Already have an account? Log in")
        }
    }
}
