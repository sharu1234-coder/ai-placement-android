package com.aiplacementos.app.domain.repository

import com.aiplacementos.app.data.remote.CheckoutResponseDto
import com.aiplacementos.app.data.remote.PlanDto
import com.aiplacementos.app.data.remote.SubscriptionStatusDto

interface SubscriptionRepository {
    suspend fun getPlans(): List<PlanDto>
    suspend fun getStatus(): SubscriptionStatusDto
    suspend fun checkout(plan: String): CheckoutResponseDto
    suspend fun verify(razorpayOrderId: String, razorpayPaymentId: String, razorpaySignature: String): SubscriptionStatusDto
}
