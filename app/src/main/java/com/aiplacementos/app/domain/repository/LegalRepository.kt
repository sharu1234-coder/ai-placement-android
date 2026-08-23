package com.aiplacementos.app.domain.repository

import com.aiplacementos.app.data.remote.LegalDocumentDto

interface LegalRepository {
    suspend fun getPrivacyPolicy(): LegalDocumentDto
}
