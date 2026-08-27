package com.aiplacementos.app.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.aiplacementos.app.AiPlacementApp
import com.aiplacementos.app.MainActivity
import com.aiplacementos.app.R
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private const val TAG = "AppFCM"
private const val CHANNEL_ID = "general"

/** Receives push messages and token updates. Registered in AndroidManifest.xml. */
class AppFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Not sent anywhere yet -- the backend has no device-token-registration endpoint
        // (production readiness item 17 is Firebase wiring only; see README). A push can't
        // actually be *sent* to a specific user until that endpoint exists and something
        // server-side calls it -- this only makes the app able to *receive* one.
        Log.d(TAG, "FCM token refreshed (not yet registered with the backend): $token")
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
            showNotification(title, body)
        }

        // Persisted locally so it's browsable in the in-app Notification Center -- there is
        // no backend notification feed to sync against, so this on-device copy is the only
        // record of it.
        val receivedAt = message.sentTime.takeIf { it > 0 } ?: System.currentTimeMillis()
        val networkModule = (applicationContext as AiPlacementApp).networkModule
        CoroutineScope(Dispatchers.IO).launch {
            networkModule.notificationRepository.record(title, body, receivedAt)
        }
    }

    private fun showNotification(title: String, body: String?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        ensureChannel()

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE,
        )

        // TODO: R.drawable.ic_launcher_foreground is the app's adaptive-icon foreground
        // layer, not a dedicated monochrome status-bar icon -- it will render, but a
        // purpose-made small icon would look better in the status bar. Not blocking.
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(this).notify(System.currentTimeMillis().toInt(), notification)
    }

    private fun ensureChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "General", NotificationManager.IMPORTANCE_DEFAULT
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}
