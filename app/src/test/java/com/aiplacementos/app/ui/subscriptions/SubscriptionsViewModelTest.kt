package com.aiplacementos.app.ui.subscriptions

import android.app.Activity
import androidx.lifecycle.SavedStateHandle
import com.aiplacementos.app.MainDispatcherRule
import com.aiplacementos.app.data.remote.SubscriptionStatusDto
import com.aiplacementos.app.fakes.FakeSubscriptionRepository
import com.aiplacementos.app.payment.RazorpayCheckoutBridge
import com.aiplacementos.app.ui.common.UiState
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Never calls any real framework method on it -- passed through as an opaque reference and
 * compared by identity only, so a bare subclass (no Robolectric) is enough; there's no other
 * Activity test-double already in this codebase to reuse. */
private class TestActivity : Activity()

class SubscriptionsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    // --- operation-key identity (Phase 24-D) ---

    @Test
    fun `switching plan after an uncertain checkout gets a new key`() {
        // The most expensive version of this defect: the server fingerprints the plan, so
        // Monthly's key sent with an Annual body came back 409 and the student could not buy
        // anything until it aged out.
        val repository = FakeSubscriptionRepository().apply {
            checkoutResult = Result.failure(java.net.UnknownHostException("reply lost"))
        }
        val (viewModel, _, _) = viewModel(repository)

        viewModel.startCheckout(fakeActivity, com.aiplacementos.app.data.remote.PlanDto("1_month", "1 Month", 19_900, 30))
        viewModel.startCheckout(fakeActivity, com.aiplacementos.app.data.remote.PlanDto("1_month", "1 Month", 19_900, 30))
        viewModel.startCheckout(fakeActivity, com.aiplacementos.app.data.remote.PlanDto("12_month", "12 Months", 149_900, 365))

        assertEquals(3, repository.keys.size)
        assertEquals("retrying Monthly is the same checkout", repository.keys[0], repository.keys[1])
        assertNotEquals("Annual is a different checkout", repository.keys[0], repository.keys[2])
    }

    @Test
    fun `an unresolved checkout key survives the ViewModel being recreated`() {
        // Razorpay's Checkout is a separate Activity, so this ViewModel can die mid-purchase.
        // A fresh key on return would be a second live order.
        val handle = SavedStateHandle()
        val repository = FakeSubscriptionRepository().apply {
            checkoutResult = Result.failure(java.net.UnknownHostException("reply lost"))
        }
        SubscriptionsViewModel(
            repository = repository,
            checkoutResults = MutableSharedFlow(extraBufferCapacity = 1),
            openCheckout = { _, _, _, _, _ -> },
            savedStateHandle = handle,
        ).startCheckout(fakeActivity, com.aiplacementos.app.data.remote.PlanDto("1_month", "1 Month", 19_900, 30))
        val first = repository.keys.single()

        SubscriptionsViewModel(
            repository = repository,
            checkoutResults = MutableSharedFlow(extraBufferCapacity = 1),
            openCheckout = { _, _, _, _, _ -> },
            savedStateHandle = handle,
        ).startCheckout(fakeActivity, com.aiplacementos.app.data.remote.PlanDto("1_month", "1 Month", 19_900, 30))

        assertEquals("the recreated screen asked about the same checkout", first, repository.keys[1])
    }

    @Test
    fun `a completed checkout consumes only that plan's key`() {
        // startCheckout refuses a second call while Razorpay's Activity is open, so the proof
        // that the key was consumed is read from where it is held rather than by calling again.
        val handle = SavedStateHandle()
        val repository = FakeSubscriptionRepository()
        SubscriptionsViewModel(
            repository = repository,
            checkoutResults = MutableSharedFlow(extraBufferCapacity = 1),
            openCheckout = { _, _, _, _, _ -> },
            savedStateHandle = handle,
        ).startCheckout(fakeActivity, com.aiplacementos.app.data.remote.PlanDto("1_month", "1 Month", 19_900, 30))

        assertEquals("the order was placed", 1, repository.keys.size)
        val stored = handle.get<HashMap<String, String>>("checkoutOperationKeys").orEmpty()
        assertFalse("the completed plan's key is gone", stored.containsKey("1_month"))
    }

    private val fakeActivity: Activity = TestActivity()

    private fun viewModel(
        repository: FakeSubscriptionRepository = FakeSubscriptionRepository(),
        results: MutableSharedFlow<RazorpayCheckoutBridge.Result> = MutableSharedFlow(extraBufferCapacity = 1),
        openCheckout: (Activity, String, String, Int, String) -> Unit = { _, _, _, _, _ -> },
    ) = Triple(SubscriptionsViewModel(repository, results, openCheckout), repository, results)

    @Test
    fun `loads status and plans successfully`() {
        val (viewModel, _, _) = viewModel()

        val state = viewModel.uiState.value
        assertTrue(state is UiState.Success)
        val data = (state as UiState.Success<SubscriptionsData>).data
        assertEquals("free", data.status.tier)
        assertEquals(4, data.plans.size)
    }

    @Test
    fun `a load failure surfaces an error state`() {
        val repository = FakeSubscriptionRepository().apply { statusResult = Result.failure(java.io.IOException("boom")) }
        val (viewModel, _, _) = viewModel(repository)

        assertTrue(viewModel.uiState.value is UiState.Error)
    }

    @Test
    fun `starting checkout calls the backend with the chosen plan and opens the SDK with server-issued values`() {
        var openedWith: List<Any>? = null
        val (viewModel, repository, _) = viewModel(
            openCheckout = { activity, keyId, orderId, amount, currency -> openedWith = listOf(activity, keyId, orderId, amount, currency) },
        )
        val plan = com.aiplacementos.app.data.remote.PlanDto("3_months", "3 Months", 49_900, 90)

        viewModel.startCheckout(fakeActivity, plan)

        assertEquals("3_months", repository.lastCheckoutPlan)
        assertEquals(listOf(fakeActivity, "rzp_test_fake", "order_fake", 19_900, "INR"), openedWith)
        assertTrue(viewModel.purchaseState.value is PurchaseState.Processing)
    }

    @Test
    fun `a successful bridge result verifies server-side and reports Success only after that`() {
        val results = MutableSharedFlow<RazorpayCheckoutBridge.Result>(extraBufferCapacity = 1)
        val repository = FakeSubscriptionRepository().apply {
            checkoutResult = Result.success(
                com.aiplacementos.app.data.remote.CheckoutResponseDto("order_1", "rzp_test_fake", 19_900, "INR", "1_month"),
            )
            verifyResult = Result.success(SubscriptionStatusDto(tier = "premium", status = "active", plan = "1_month", expiresAt = null))
        }
        val (viewModel, _, _) = viewModel(repository, results)
        val plan = com.aiplacementos.app.data.remote.PlanDto("1_month", "1 Month", 19_900, 30)
        viewModel.startCheckout(fakeActivity, plan)

        results.tryEmit(RazorpayCheckoutBridge.Result.Success("order_1", "pay_1", "sig_1"))

        val purchaseState = viewModel.purchaseState.value
        assertTrue(purchaseState is PurchaseState.Success)
        assertEquals("premium", (purchaseState as PurchaseState.Success).status.tier)
        assertEquals("order_1", repository.lastVerifyArgs?.orderId)
        assertEquals("pay_1", repository.lastVerifyArgs?.paymentId)
        assertEquals("sig_1", repository.lastVerifyArgs?.signature)
    }

    @Test
    fun `a bridge result for a different order id than the one in flight is ignored`() {
        val results = MutableSharedFlow<RazorpayCheckoutBridge.Result>(extraBufferCapacity = 1)
        val repository = FakeSubscriptionRepository().apply {
            checkoutResult = Result.success(
                com.aiplacementos.app.data.remote.CheckoutResponseDto("order_real", "rzp_test_fake", 19_900, "INR", "1_month"),
            )
        }
        val (viewModel, _, _) = viewModel(repository, results)
        val plan = com.aiplacementos.app.data.remote.PlanDto("1_month", "1 Month", 19_900, 30)
        viewModel.startCheckout(fakeActivity, plan)

        // A stale result for some other order this ViewModel never started -- must not be
        // processed as if it belonged to the in-flight checkout.
        results.tryEmit(RazorpayCheckoutBridge.Result.Success("order_stale", "pay_x", "sig_x"))

        assertNull(repository.lastVerifyArgs)
        assertTrue(viewModel.purchaseState.value is PurchaseState.Processing)
    }

    @Test
    fun `a failed bridge result reports Failed without calling verify`() {
        val results = MutableSharedFlow<RazorpayCheckoutBridge.Result>(extraBufferCapacity = 1)
        val repository = FakeSubscriptionRepository().apply {
            checkoutResult = Result.success(
                com.aiplacementos.app.data.remote.CheckoutResponseDto("order_1", "rzp_test_fake", 19_900, "INR", "1_month"),
            )
        }
        val (viewModel, _, _) = viewModel(repository, results)
        val plan = com.aiplacementos.app.data.remote.PlanDto("1_month", "1 Month", 19_900, 30)
        viewModel.startCheckout(fakeActivity, plan)

        results.tryEmit(RazorpayCheckoutBridge.Result.Failure(code = 1, description = "card declined"))

        assertNull(repository.lastVerifyArgs)
        val purchaseState = viewModel.purchaseState.value
        assertTrue(purchaseState is PurchaseState.Failed)
        assertEquals("card declined", (purchaseState as PurchaseState.Failed).message)
    }

    @Test
    fun `a cancelled bridge result reports Cancelled without calling verify`() {
        val results = MutableSharedFlow<RazorpayCheckoutBridge.Result>(extraBufferCapacity = 1)
        val repository = FakeSubscriptionRepository().apply {
            checkoutResult = Result.success(
                com.aiplacementos.app.data.remote.CheckoutResponseDto("order_1", "rzp_test_fake", 19_900, "INR", "1_month"),
            )
        }
        val (viewModel, _, _) = viewModel(repository, results)
        val plan = com.aiplacementos.app.data.remote.PlanDto("1_month", "1 Month", 19_900, 30)
        viewModel.startCheckout(fakeActivity, plan)

        results.tryEmit(RazorpayCheckoutBridge.Result.Cancelled)

        assertNull(repository.lastVerifyArgs)
        assertEquals(PurchaseState.Cancelled, viewModel.purchaseState.value)
    }

    /**
     * Regression, and the most expensive bug this class can have: Razorpay's Checkout runs in
     * its own Activity, so this app's process can be killed for memory while the student is
     * paying. pendingOrderId used to be a plain var, so the recreated ViewModel came back with
     * no memory of the order it was waiting on -- the real Success result for an
     * already-charged payment failed the identity check below and was dropped in silence: no
     * verify() call, no error, no message, money taken and premium never granted. Persisting it
     * in SavedStateHandle is what makes the recreated instance still recognise its own order.
     */
    @Test
    fun `a checkout that survives process death is still verified after recreation`() {
        val results = MutableSharedFlow<RazorpayCheckoutBridge.Result>(replay = 1, extraBufferCapacity = 1)
        val repository = FakeSubscriptionRepository().apply {
            verifyResult = Result.success(
                SubscriptionStatusDto(tier = "premium", status = "active", plan = "1_month", expiresAt = null),
            )
        }
        // What SavedStateHandle hands back to the recreated ViewModel: the order id the
        // pre-death instance had written before launching Razorpay's Activity.
        val restoredState = SavedStateHandle(mapOf("pendingOrderId" to "order_survivor"))
        val recreated = SubscriptionsViewModel(
            repository = repository,
            checkoutResults = results,
            openCheckout = { _, _, _, _, _ -> },
            savedStateHandle = restoredState,
        )

        // Razorpay's callback lands on the *recreated* MainActivity, after the fact.
        results.tryEmit(RazorpayCheckoutBridge.Result.Success("order_survivor", "pay_s", "sig_s"))

        assertEquals("order_survivor", repository.lastVerifyArgs?.orderId)
        assertEquals("pay_s", repository.lastVerifyArgs?.paymentId)
        val purchaseState = recreated.purchaseState.value
        assertTrue(purchaseState is PurchaseState.Success)
        assertEquals("premium", (purchaseState as PurchaseState.Success).status.tier)
    }

    /** The order id is written to SavedStateHandle at the moment checkout starts -- not only
     * when it completes -- since process death can happen at any point after that. */
    @Test
    fun `starting checkout persists the order id for a later recreation`() {
        val handle = SavedStateHandle()
        val repository = FakeSubscriptionRepository().apply {
            checkoutResult = Result.success(
                com.aiplacementos.app.data.remote.CheckoutResponseDto("order_persisted", "rzp_test_fake", 19_900, "INR", "1_month"),
            )
        }
        val viewModel = SubscriptionsViewModel(
            repository = repository,
            checkoutResults = MutableSharedFlow(extraBufferCapacity = 1),
            openCheckout = { _, _, _, _, _ -> },
            savedStateHandle = handle,
        )

        viewModel.startCheckout(fakeActivity, com.aiplacementos.app.data.remote.PlanDto("1_month", "1 Month", 19_900, 30))

        assertEquals("order_persisted", handle.get<String>("pendingOrderId"))
    }

    /** The flip side of replay = 1 on the bridge: a normal later visit to Subscriptions (a
     * brand-new ViewModel with nothing persisted) must discard a replayed old result rather
     * than re-verifying someone's previous purchase. */
    @Test
    fun `a replayed result reaching a fresh ViewModel with no pending order is ignored`() {
        val results = MutableSharedFlow<RazorpayCheckoutBridge.Result>(replay = 1, extraBufferCapacity = 1)
        results.tryEmit(RazorpayCheckoutBridge.Result.Success("order_old", "pay_old", "sig_old"))
        val repository = FakeSubscriptionRepository()

        val fresh = SubscriptionsViewModel(
            repository = repository,
            checkoutResults = results,
            openCheckout = { _, _, _, _, _ -> },
            savedStateHandle = SavedStateHandle(),
        )

        assertNull(repository.lastVerifyArgs)
        assertEquals(PurchaseState.Idle, fresh.purchaseState.value)
    }

    @Test
    fun `starting a second checkout while one is already processing is ignored`() {
        var callCount = 0
        val (viewModel, repository, _) = viewModel(openCheckout = { _, _, _, _, _ -> callCount++ })
        val plan = com.aiplacementos.app.data.remote.PlanDto("1_month", "1 Month", 19_900, 30)

        viewModel.startCheckout(fakeActivity, plan)
        viewModel.startCheckout(fakeActivity, plan)

        assertEquals(1, callCount)
    }
}
