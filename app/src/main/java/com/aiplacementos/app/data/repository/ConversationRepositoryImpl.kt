package com.aiplacementos.app.data.repository

import com.aiplacementos.app.data.remote.ConversationDetailDto
import com.aiplacementos.app.data.remote.ConversationDto
import com.aiplacementos.app.data.remote.ConversationsApi
import com.aiplacementos.app.domain.repository.ConversationRepository

class ConversationRepositoryImpl(
    private val conversationsApi: ConversationsApi,
) : ConversationRepository {

    override suspend fun listConversations(
        kind: String?,
        limit: Int,
        offset: Int,
    ): List<ConversationDto> = conversationsApi.listConversations(kind, limit, offset)

    override suspend fun getConversation(conversationId: Int): ConversationDetailDto =
        conversationsApi.getConversation(conversationId)

    override suspend fun deleteConversation(conversationId: Int) {
        conversationsApi.deleteConversation(conversationId)
    }
}
