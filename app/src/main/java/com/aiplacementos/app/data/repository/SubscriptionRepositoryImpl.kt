package com.aiplacementos.app.data.repository

import com.aiplacementos.app.data.remote.CheckoutRequestDto
import com.aiplacementos.app.data.remote.CheckoutResponseDto
import com.aiplacementos.app.data.remote.PlanDto
import com.aiplacementos.app.data.remote.SubscriptionStatusDto
import com.aiplacementos.app.data.remote.SubscriptionsApi
import com.aiplacementos.app.data.remote.VerifyPaymentRequestDto
import com.aiplacementos.app.domain.repository.SubscriptionRepository

class SubscriptionRepositoryImpl(private val subscriptionsApi: SubscriptionsApi) : SubscriptionRepository {
    override suspend fun getPlans(): List<PlanDto> = subscriptionsApi.getPlans()

    override suspend fun getStatus(): SubscriptionStatusDto = subscriptionsApi.getStatus()

    override suspend fun checkout(plan: String, idempotencyKey: String?): CheckoutResponseDto =
        subscriptionsApi.checkout(CheckoutRequestDto(plan), idempotencyKey)

    override suspend fun verify(
        razorpayOrderId: String,
        razorpayPaymentId: String,
        razorpaySignature: String,
    ): SubscriptionStatusDto =
        subscriptionsApi.verify(VerifyPaymentRequestDto(razorpayOrderId, razorpayPaymentId, razorpaySignature))
}
