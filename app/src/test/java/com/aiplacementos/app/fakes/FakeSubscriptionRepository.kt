package com.aiplacementos.app.fakes

import com.aiplacementos.app.data.remote.CheckoutResponseDto
import com.aiplacementos.app.data.remote.PlanDto
import com.aiplacementos.app.data.remote.SubscriptionStatusDto
import com.aiplacementos.app.domain.repository.SubscriptionRepository

class FakeSubscriptionRepository : SubscriptionRepository {
    var statusResult: Result<SubscriptionStatusDto> = Result.success(
        SubscriptionStatusDto(tier = "free", status = "never_subscribed"),
    )
    var plansResult: Result<List<PlanDto>> = Result.success(
        listOf(
            PlanDto(plan = "1_month", label = "1 Month", amountPaise = 19_900, durationDays = 30),
            PlanDto(plan = "3_months", label = "3 Months", amountPaise = 49_900, durationDays = 90),
            PlanDto(plan = "6_months", label = "6 Months", amountPaise = 79_900, durationDays = 180),
            PlanDto(plan = "1_year", label = "1 Year", amountPaise = 99_900, durationDays = 365),
        ),
    )
    var checkoutResult: Result<CheckoutResponseDto>? = null
    var verifyResult: Result<SubscriptionStatusDto>? = null

    var lastCheckoutPlan: String? = null
    data class VerifyArgs(val orderId: String, val paymentId: String, val signature: String)
    var lastVerifyArgs: VerifyArgs? = null

    override suspend fun getPlans(): List<PlanDto> = plansResult.getOrThrow()

    override suspend fun getStatus(): SubscriptionStatusDto = statusResult.getOrThrow()

    override suspend fun checkout(plan: String): CheckoutResponseDto {
        lastCheckoutPlan = plan
        return (checkoutResult ?: Result.success(
            CheckoutResponseDto(
                razorpayOrderId = "order_fake",
                razorpayKeyId = "rzp_test_fake",
                amountPaise = 19_900,
                currency = "INR",
                plan = plan,
            ),
        )).getOrThrow()
    }

    override suspend fun verify(razorpayOrderId: String, razorpayPaymentId: String, razorpaySignature: String): SubscriptionStatusDto {
        lastVerifyArgs = VerifyArgs(razorpayOrderId, razorpayPaymentId, razorpaySignature)
        return (verifyResult ?: Result.success(
            SubscriptionStatusDto(tier = "premium", status = "active", plan = "1_month", expiresAt = "2027-01-01T00:00:00Z"),
        )).getOrThrow()
    }
}
