package com.aiplacementos.app.ui.notifications

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aiplacementos.app.push.LocalNotificationPoster
import com.aiplacementos.app.push.NotificationPreferences
import com.aiplacementos.app.ui.common.networkModule

/**
 * Invisible -- renders no UI of its own. Embedded on Home (the screen every session reliably
 * passes through) so a server-detected event is never left waiting past the next time the
 * student opens the app, the same "next time the app polls/opens" delivery model the local-
 * only Phase 14 scoping decision settled on in place of real server push.
 */
@Composable
fun NotificationSyncSection() {
    val network = networkModule()
    val context = LocalContext.current
    val viewModel: NotificationSyncViewModel = viewModel(
        factory = viewModelFactory {
            initializer { NotificationSyncViewModel(network.notificationSyncRepository, network.notificationRepository) }
        },
    )
    val newlySynced by viewModel.newlySynced.collectAsStateWithLifecycle()

    LifecycleResumeEffect(Unit) {
        viewModel.sync()
        onPauseOrDispose {}
    }

    LaunchedEffect(newlySynced) {
        if (newlySynced.isEmpty()) return@LaunchedEffect
        // Muting only suppresses the banner/sound -- record() already ran regardless (see
        // NotificationSyncViewModel.sync), same rule AppFirebaseMessagingService follows.
        if (NotificationPreferences.isEnabled(context)) {
            newlySynced.forEach { LocalNotificationPoster.post(context, it.title, it.body) }
        }
        viewModel.consumed()
    }
}
