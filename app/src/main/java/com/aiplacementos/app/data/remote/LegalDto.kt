package com.aiplacementos.app.data.remote

import kotlinx.serialization.Serializable

@Serializable
data class LegalDocumentDto(val version: String, val content: String)
