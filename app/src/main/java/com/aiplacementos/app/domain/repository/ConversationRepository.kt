package com.aiplacementos.app.domain.repository

import com.aiplacementos.app.data.remote.ConversationDetailDto
import com.aiplacementos.app.data.remote.ConversationDto

/**
 * The unified voice conversation history.
 *
 * `kind` is a nullable filter rather than two methods, because the product's own framing is one
 * history with a filter on it -- a coaching list and an interview list would be two features.
 */
interface ConversationRepository {
    suspend fun listConversations(
        kind: String? = null,
        limit: Int = 20,
        offset: Int = 0,
    ): List<ConversationDto>

    suspend fun getConversation(conversationId: Int): ConversationDetailDto

    suspend fun deleteConversation(conversationId: Int)
}
