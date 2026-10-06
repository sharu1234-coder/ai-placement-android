package com.aiplacementos.app.data.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

interface SubscriptionsApi {
    @GET("subscriptions/plans")
    suspend fun getPlans(): List<PlanDto>

    @GET("subscriptions/status")
    suspend fun getStatus(): SubscriptionStatusDto

    @POST("subscriptions/checkout")
    suspend fun checkout(
        @Body request: CheckoutRequestDto,
        @Header("Idempotency-Key") idempotencyKey: String? = null,
    ): CheckoutResponseDto

    @POST("subscriptions/verify")
    suspend fun verify(@Body request: VerifyPaymentRequestDto): SubscriptionStatusDto
}
