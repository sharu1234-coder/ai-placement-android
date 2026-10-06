package com.aiplacementos.app.data.repository

import com.aiplacementos.app.data.remote.AtsCheckApi
import com.aiplacementos.app.data.remote.AtsCheckListItemDto
import com.aiplacementos.app.data.remote.AtsCheckReportDto
import com.aiplacementos.app.data.remote.CreateAtsCheckRequestDto
import com.aiplacementos.app.domain.repository.AtsCheckRepository

class AtsCheckRepositoryImpl(private val atsCheckApi: AtsCheckApi) : AtsCheckRepository {
    override suspend fun createAtsCheck(
        resumeId: Int,
        targetRole: String?,
        idempotencyKey: String?,
    ): AtsCheckReportDto =
        atsCheckApi.createAtsCheck(
            resumeId,
            CreateAtsCheckRequestDto(targetRole = targetRole),
            idempotencyKey,
        )

    override suspend fun listAtsChecks(): List<AtsCheckListItemDto> = atsCheckApi.listAtsChecks()

    override suspend fun getAtsCheck(checkId: Int): AtsCheckReportDto = atsCheckApi.getAtsCheck(checkId)
}
