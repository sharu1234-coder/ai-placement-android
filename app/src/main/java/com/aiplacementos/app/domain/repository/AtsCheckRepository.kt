package com.aiplacementos.app.domain.repository

import com.aiplacementos.app.data.remote.AtsCheckListItemDto
import com.aiplacementos.app.data.remote.AtsCheckReportDto

interface AtsCheckRepository {
    /** [idempotencyKey] names the logical operation, so a transport replay of it is free. */
    suspend fun createAtsCheck(
        resumeId: Int,
        targetRole: String?,
        idempotencyKey: String? = null,
    ): AtsCheckReportDto
    suspend fun listAtsChecks(): List<AtsCheckListItemDto>
    suspend fun getAtsCheck(checkId: Int): AtsCheckReportDto
}
