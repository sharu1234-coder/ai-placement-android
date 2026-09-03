package com.aiplacementos.app.push

import android.util.Log
import com.aiplacementos.app.AiPlacementApp
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private const val TAG = "AppFCM"

/** Receives push messages and token updates. Registered in AndroidManifest.xml. */
class AppFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Not sent anywhere yet -- the backend has no device-token-registration endpoint
        // (production readiness item 17 is Firebase wiring only; see README). A push can't
        // actually be *sent* to a specific user until that endpoint exists and something
        // server-side calls it -- this only makes the app able to *receive* one.
        // Deliberately never logs the token value itself -- it's a real per-device
        // credential (whoever holds it, combined with this project's separate Firebase
        // server key, could target push messages at this exact device), and Log.d output is
        // not stripped from release builds here (no such ProGuard rule exists), so anything
        // logged at this level persists in a shipped APK's logs.
        Log.d(TAG, "FCM token refreshed (not yet registered with the backend)")
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        // Supports both a "notification" payload (title/body set by whatever sends the
        // push) and a data-only payload using the same keys, since which one a future
        // backend integration uses isn't decided yet.
        val title = message.notification?.title ?: message.data["title"] ?: return
        val body = message.notification?.body ?: message.data["body"]
        // Muting (Settings > Preferences > Notifications) only suppresses the system banner/
        // sound -- the push is always recorded below regardless, so muting can never cause a
        // real notification to silently vanish from the in-app Notification Center.
        if (NotificationPreferences.isEnabled(applicationContext)) {
            LocalNotificationPoster.post(applicationContext, title, body)
        }

        // Persisted locally so it's browsable in the in-app Notification Center -- the same
        // table NotificationSyncSection's poll of GET /notifications/pending writes into, so
        // a real push and a server-detected event polled later both land in one feed.
        val receivedAt = message.sentTime.takeIf { it > 0 } ?: System.currentTimeMillis()
        val networkModule = (applicationContext as AiPlacementApp).networkModule
        CoroutineScope(Dispatchers.IO).launch {
            networkModule.notificationRepository.record(title, body, receivedAt)
        }
    }
}
