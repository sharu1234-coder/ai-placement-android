package com.aiplacementos.app.ui.subscriptions

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiplacementos.app.data.remote.PlanDto
import com.aiplacementos.app.data.remote.SubscriptionStatusDto
import com.aiplacementos.app.domain.repository.SubscriptionRepository
import com.aiplacementos.app.payment.RazorpayCheckoutBridge
import com.aiplacementos.app.ui.common.UiState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SubscriptionsData(val status: SubscriptionStatusDto, val plans: List<PlanDto>)

/** Transient feedback for one purchase attempt -- deliberately separate from [uiState], which
 * always reflects the server's real entitlement. This never itself grants premium: Success
 * only appears after repository.verify() -- the backend's signature check -- returns a
 * "premium/active" status, and [uiState] is reloaded from that same server response right
 * after, so the two can never disagree about whether the student actually has premium. */
sealed interface PurchaseState {
    data object Idle : PurchaseState
    data class Processing(val plan: String) : PurchaseState
    data class Success(val status: SubscriptionStatusDto) : PurchaseState
    data class Failed(val message: String) : PurchaseState
    data object Cancelled : PurchaseState
}

class SubscriptionsViewModel(
    private val repository: SubscriptionRepository,
    // Defaults to the real singleton for production wiring; tests inject their own Flow so
    // they never depend on RazorpayCheckoutBridge's global, cross-test-shared state.
    checkoutResults: Flow<RazorpayCheckoutBridge.Result> = RazorpayCheckoutBridge.results,
    // Defaults to the real SDK call; tests inject a no-op so they never touch
    // Razorpay's Activity-based Checkout SDK, which needs a real Android runtime.
    private val openCheckout: (Activity, String, String, Int, String) -> Unit = RazorpayCheckoutBridge::open,
) : ViewModel() {
    private val _uiState = MutableStateFlow<UiState<SubscriptionsData>>(UiState.Loading)
    val uiState: StateFlow<UiState<SubscriptionsData>> = _uiState.asStateFlow()

    private val _purchaseState = MutableStateFlow<PurchaseState>(PurchaseState.Idle)
    val purchaseState: StateFlow<PurchaseState> = _purchaseState.asStateFlow()

    // The order id this purchase attempt is waiting on -- a bridge result for any other order
    // id (e.g. a stale result surviving process/activity recreation) is ignored rather than
    // processed, since it can't correspond to what's currently in flight.
    private var pendingOrderId: String? = null

    init {
        load()
        viewModelScope.launch {
            checkoutResults.collect { result -> handleBridgeResult(result) }
        }
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                val status = repository.getStatus()
                val plans = repository.getPlans()
                _uiState.value = UiState.Success(SubscriptionsData(status, plans))
            } catch (e: Exception) {
                _uiState.value = UiState.Error("Couldn't load subscription plans. Check your connection.")
            }
        }
    }

    /** [activity] is required by Razorpay's Checkout SDK itself (Checkout.open needs an
     * Activity, not just a Context) -- the caller passes `LocalContext.current as Activity`,
     * safe in this single-Activity app. */
    fun startCheckout(activity: Activity, plan: PlanDto) {
        if (_purchaseState.value is PurchaseState.Processing) return // double-submit guard
        _purchaseState.value = PurchaseState.Processing(plan.plan)
        viewModelScope.launch {
            try {
                val checkout = repository.checkout(plan.plan)
                pendingOrderId = checkout.razorpayOrderId
                openCheckout(activity, checkout.razorpayKeyId, checkout.razorpayOrderId, checkout.amountPaise, checkout.currency)
            } catch (e: Exception) {
                pendingOrderId = null
                _purchaseState.value = PurchaseState.Failed("Couldn't start checkout. Check your connection and try again.")
            }
        }
    }

    private fun handleBridgeResult(result: RazorpayCheckoutBridge.Result) {
        when (result) {
            is RazorpayCheckoutBridge.Result.Success -> {
                if (result.razorpayOrderId != pendingOrderId) return
                viewModelScope.launch {
                    try {
                        // The ONLY place premium is ever granted: the backend's HMAC
                        // verification against this exact order/payment/signature triple.
                        val status = repository.verify(
                            result.razorpayOrderId, result.razorpayPaymentId, result.razorpaySignature,
                        )
                        pendingOrderId = null
                        _purchaseState.value = PurchaseState.Success(status)
                        load()
                    } catch (e: Exception) {
                        _purchaseState.value = PurchaseState.Failed(
                            "Payment completed but couldn't be verified. If this persists, contact support before trying again.",
                        )
                    }
                }
            }
            is RazorpayCheckoutBridge.Result.Failure -> {
                if (pendingOrderId == null) return // no checkout in flight -- stale/duplicate result
                pendingOrderId = null
                _purchaseState.value = PurchaseState.Failed(result.description ?: "Payment failed. Please try again.")
            }
            RazorpayCheckoutBridge.Result.Cancelled -> {
                if (pendingOrderId == null) return
                pendingOrderId = null
                _purchaseState.value = PurchaseState.Cancelled
            }
        }
    }

    fun dismissPurchaseState() {
        _purchaseState.value = PurchaseState.Idle
    }
}
