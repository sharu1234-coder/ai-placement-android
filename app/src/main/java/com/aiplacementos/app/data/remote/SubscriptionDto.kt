package com.aiplacementos.app.data.remote

import kotlinx.serialization.Serializable

@Serializable
data class SubscriptionStatusDto(
    val tier: String,
    val status: String,
)
