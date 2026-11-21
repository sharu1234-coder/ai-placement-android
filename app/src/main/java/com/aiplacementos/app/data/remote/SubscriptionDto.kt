package com.aiplacementos.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PlanDto(
    val plan: String,
    val label: String,
    @SerialName("amount_paise") val amountPaise: Int,
    @SerialName("duration_days") val durationDays: Int,
)

/** The one source of truth for premium access -- always re-fetched from the backend rather
 * than cached/trusted locally, per the "server-derived entitlement is authoritative" rule.
 * tier/status/plan/expiresAt are ALL server-computed; nothing here is ever set locally. */
@Serializable
data class SubscriptionStatusDto(
    val tier: String,
    val status: String,
    val plan: String? = null,
    @SerialName("expires_at") val expiresAt: String? = null,
    /** Which Razorpay mode the SERVER charges in -- "test", "live" or "unknown". A deployment
     * property rather than a per-profile one, and the only thing that licenses the purchase
     * flow to say no real money is charged; the client never infers it. Nullable with a null
     * default so a backend that predates the field decodes cleanly and reads as unknown, which
     * [com.aiplacementos.app.ui.subscriptions.paymentDisclosure] treats as "promise nothing". */
    @SerialName("payment_mode") val paymentMode: String? = null,
)

@Serializable
data class CheckoutRequestDto(val plan: String)

@Serializable
data class CheckoutResponseDto(
    @SerialName("razorpay_order_id") val razorpayOrderId: String,
    @SerialName("razorpay_key_id") val razorpayKeyId: String,
    @SerialName("amount_paise") val amountPaise: Int,
    val currency: String,
    val plan: String,
)

@Serializable
data class VerifyPaymentRequestDto(
    @SerialName("razorpay_order_id") val razorpayOrderId: String,
    @SerialName("razorpay_payment_id") val razorpayPaymentId: String,
    @SerialName("razorpay_signature") val razorpaySignature: String,
)
