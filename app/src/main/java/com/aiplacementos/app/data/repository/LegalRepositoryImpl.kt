package com.aiplacementos.app.data.repository

import com.aiplacementos.app.data.remote.LegalApi
import com.aiplacementos.app.data.remote.LegalDocumentDto
import com.aiplacementos.app.domain.repository.LegalRepository

class LegalRepositoryImpl(private val legalApi: LegalApi) : LegalRepository {
    override suspend fun getPrivacyPolicy(): LegalDocumentDto = legalApi.getPrivacyPolicy()
}
