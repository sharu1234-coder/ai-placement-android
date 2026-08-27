package com.aiplacementos.app.domain.repository

import com.aiplacementos.app.data.remote.AtsCheckListItemDto
import com.aiplacementos.app.data.remote.AtsCheckReportDto

interface AtsCheckRepository {
    suspend fun createAtsCheck(resumeId: Int, targetRole: String?): AtsCheckReportDto
    suspend fun listAtsChecks(): List<AtsCheckListItemDto>
    suspend fun getAtsCheck(checkId: Int): AtsCheckReportDto
}
