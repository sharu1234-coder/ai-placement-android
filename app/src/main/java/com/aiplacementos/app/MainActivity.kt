package com.aiplacementos.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.compose.rememberNavController
import com.aiplacementos.app.navigation.NavGraph
import com.aiplacementos.app.payment.RazorpayCheckoutBridge
import com.aiplacementos.app.ui.theme.AiPlacementOsTheme
import com.razorpay.ExternalWalletListener
import com.razorpay.PaymentData
import com.razorpay.PaymentResultWithDataListener

class MainActivity :
    ComponentActivity(),
    PaymentResultWithDataListener,
    ExternalWalletListener {
    // Fire-and-forget: unlike VoiceInterviewScreen's mic permission (which blocks that one
    // screen until granted), nothing in the app is gated on notification permission -- a
    // push just won't display if it's denied.
    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()
        enableEdgeToEdge()
        setContent {
            AiPlacementOsTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()
                    NavGraph(navController = navController)
                }
            }
        }
    }

    // POST_NOTIFICATIONS is only a runtime permission from API 33 (Tiramisu) onward --
    // notifications work without asking on every version below that.
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    // Razorpay's Checkout SDK requires the hosting Activity itself to implement these
    // callbacks (there is exactly one Activity in this app) -- both just forward to
    // RazorpayCheckoutBridge, which is what SubscriptionsViewModel actually observes. Neither
    // callback does anything else: no premium is granted here or anywhere client-side: that
    // only ever happens after the backend verifies the signature (see subscription_service.py).
    override fun onPaymentSuccess(razorpayPaymentId: String?, data: PaymentData?) {
        RazorpayCheckoutBridge.emitSuccess(razorpayPaymentId, data)
    }

    override fun onPaymentError(code: Int, response: String?, data: PaymentData?) {
        RazorpayCheckoutBridge.emitError(code, response)
    }

    override fun onExternalWalletSelected(walletName: String?, data: PaymentData?) {
        // This app doesn't special-case external wallets -- Checkout still resolves the
        // payment through the normal onPaymentSuccess/onPaymentError callbacks afterward, so
        // there is nothing to do here beyond satisfying the interface.
    }
}
