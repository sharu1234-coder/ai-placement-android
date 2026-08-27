package com.aiplacementos.app.fakes

import com.aiplacementos.app.data.remote.SubscriptionStatusDto
import com.aiplacementos.app.domain.repository.SubscriptionRepository

class FakeSubscriptionRepository : SubscriptionRepository {
    var statusResult: Result<SubscriptionStatusDto> = Result.success(SubscriptionStatusDto(tier = "free", status = "coming_soon"))

    override suspend fun getStatus(): SubscriptionStatusDto = statusResult.getOrThrow()
}
