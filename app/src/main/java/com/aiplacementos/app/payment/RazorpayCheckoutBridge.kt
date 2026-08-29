package com.aiplacementos.app.payment

import android.app.Activity
import com.razorpay.Checkout
import com.razorpay.PaymentData
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.json.JSONObject

/**
 * Bridges Razorpay's classic Activity-callback Checkout SDK (PaymentResultWithDataListener,
 * implemented on MainActivity -- see its onPaymentSuccess/onPaymentError) into this app's
 * Compose + ViewModel architecture. There is exactly one Activity in this app, so exactly one
 * listener registration is ever needed; this object is that single hand-off point rather than
 * plumbing a listener reference through the nav graph.
 *
 * order_id/amount/currency all come from the backend's /subscriptions/checkout response --
 * this bridge never invents or overrides any of them. razorpay_key_id is the publishable
 * key id (safe client-side); the secret never leaves the backend.
 */
object RazorpayCheckoutBridge {
    sealed interface Result {
        data class Success(val razorpayOrderId: String, val razorpayPaymentId: String, val razorpaySignature: String) : Result
        data class Failure(val code: Int, val description: String?) : Result
        data object Cancelled : Result
    }

    // extraBufferCapacity = 1 with DROP_OLDEST-like replacement isn't needed here: at most
    // one checkout is ever in flight (Checkout.open blocks further opens itself), so a single
    // buffered slot is always enough for the one result that follows.
    private val _results = MutableSharedFlow<Result>(extraBufferCapacity = 1)
    val results: SharedFlow<Result> = _results.asSharedFlow()

    fun open(activity: Activity, razorpayKeyId: String, razorpayOrderId: String, amountPaise: Int, currency: String) {
        val checkout = Checkout()
        checkout.setKeyID(razorpayKeyId)
        val options = JSONObject().apply {
            put("key", razorpayKeyId)
            put("order_id", razorpayOrderId)
            put("amount", amountPaise)
            put("currency", currency)
            put("name", "AI Placement OS")
            put("description", "Premium Subscription")
        }
        checkout.open(activity, options)
    }

    /** Razorpay's own onPaymentSuccess signature gives (paymentId, PaymentData) -- orderId and
     * signature both come off that same PaymentData object, never invented locally. */
    internal fun emitSuccess(razorpayPaymentId: String?, data: PaymentData?) {
        val orderId = data?.orderId
        val signature = data?.signature
        if (razorpayPaymentId.isNullOrBlank() || orderId.isNullOrBlank() || signature.isNullOrBlank()) {
            // Razorpay reported success but didn't give us everything needed to verify server-
            // side -- treat this as a failure rather than silently skipping verification,
            // since an unverified "success" must never be trusted.
            _results.tryEmit(Result.Failure(code = -1, description = "Incomplete payment result from Razorpay."))
            return
        }
        _results.tryEmit(Result.Success(orderId, razorpayPaymentId, signature))
    }

    internal fun emitError(code: Int, description: String?) {
        // Razorpay's own convention: code 2 is Checkout.PAYMENT_CANCELED (user closed the
        // sheet without paying) -- worth distinguishing from a real failure so the UI can
        // show "cancelled" instead of an alarming error message for a normal dismiss.
        if (code == Checkout.PAYMENT_CANCELED) {
            _results.tryEmit(Result.Cancelled)
        } else {
            _results.tryEmit(Result.Failure(code, description))
        }
    }
}
