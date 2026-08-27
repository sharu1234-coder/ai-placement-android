package com.aiplacementos.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aiplacementos.app.BuildConfig
import com.aiplacementos.app.push.NotificationPreferences
import com.aiplacementos.app.ui.common.AppCard
import com.aiplacementos.app.ui.common.ConfirmationDialog
import com.aiplacementos.app.ui.common.PrimaryButton
import com.aiplacementos.app.ui.common.SecondaryButton
import com.aiplacementos.app.ui.common.UiState
import com.aiplacementos.app.ui.common.networkModule
import com.aiplacementos.app.ui.theme.Spacing
import kotlinx.coroutines.delay

@Composable
fun SettingsScreen(
    onOpenEditProfile: () -> Unit,
    onOpenChangePassword: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenTerms: () -> Unit,
    onOpenSubscriptions: () -> Unit,
    onOpenHelpSupport: () -> Unit,
    onOpenAboutUs: () -> Unit,
    onLoggedOut: () -> Unit,
) {
    val context = LocalContext.current
    val network = networkModule()
    val viewModel: SettingsViewModel = viewModel(
        factory = viewModelFactory {
            initializer { SettingsViewModel(network.authRepository, network.profileRepository) }
        },
    )
    val deleteAccountState by viewModel.deleteAccountState.collectAsStateWithLifecycle()
    val cacheCleared by viewModel.cacheCleared.collectAsStateWithLifecycle()

    var showDeleteDialog by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var notificationsEnabled by remember { mutableStateOf(NotificationPreferences.isEnabled(context)) }

    LaunchedEffect(cacheCleared) {
        if (cacheCleared) {
            delay(2_000)
            viewModel.dismissCacheClearedMessage()
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item { Text(text = "Settings", style = MaterialTheme.typography.headlineSmall) }

        item {
            SettingsSection(title = "Account") {
                SettingsRow(label = "Edit Profile", buttonText = "Open", onClick = onOpenEditProfile)
                SettingsRow(label = "Change Password", buttonText = "Open", onClick = onOpenChangePassword)
            }
        }

        item {
            SettingsSection(title = "Preferences") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Notifications", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "Show a banner for incoming pushes.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = notificationsEnabled,
                        onCheckedChange = {
                            notificationsEnabled = it
                            viewModel.setNotificationsEnabled(context, it)
                        },
                    )
                }
                SettingsRow(
                    label = "Privacy",
                    buttonText = "Open",
                    onClick = onOpenPrivacy,
                    modifier = Modifier.padding(top = Spacing.sm),
                )
            }
        }

        item {
            SettingsSection(title = "Storage") {
                SettingsRow(
                    label = if (cacheCleared) "Cache cleared" else "Clear Cache",
                    buttonText = "Clear",
                    onClick = { viewModel.clearCache(context) },
                )
            }
        }

        item {
            SettingsSection(title = "Subscription") {
                SettingsRow(label = "Manage Subscription", buttonText = "Open", onClick = onOpenSubscriptions)
            }
        }

        item {
            SettingsSection(title = "Support") {
                SettingsRow(label = "Help & Support", buttonText = "Open", onClick = onOpenHelpSupport)
                SettingsRow(label = "About Us", buttonText = "Open", onClick = onOpenAboutUs, modifier = Modifier.padding(top = Spacing.sm))
                SettingsRow(label = "Terms of Service", buttonText = "Open", onClick = onOpenTerms, modifier = Modifier.padding(top = Spacing.sm))
            }
        }

        item {
            PrimaryButton(text = "Log out", onClick = { viewModel.logout(onLoggedOut) }, modifier = Modifier.fillMaxWidth())
        }

        item {
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Danger Zone",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold,
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

        item {
            Text(
                text = "Version ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (showDeleteDialog) {
        val isLoading = deleteAccountState is UiState.Loading
        ConfirmationDialog(
            title = "Delete account?",
            message = "This permanently deletes all your data and can't be undone. Enter your password to confirm.",
            confirmText = if (isLoading) "Deleting..." else "Delete permanently",
            onConfirm = { viewModel.deleteAccount(password, onDeleted = onLoggedOut) },
            onDismiss = { if (!isLoading) showDeleteDialog = false },
            isDestructive = true,
            confirmEnabled = !isLoading && password.isNotBlank(),
            dismissEnabled = !isLoading,
        ) {
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
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = Spacing.xs),
        )
        AppCard(modifier = Modifier.fillMaxWidth()) { content() }
    }
}

@Composable
private fun SettingsRow(label: String, buttonText: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = MaterialTheme.typography.titleMedium)
        SecondaryButton(text = buttonText, onClick = onClick)
    }
}
