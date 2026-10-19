package com.aiplacementos.app.data.remote

import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * The unified conversation history endpoints.
 *
 * Read and delete only. There is no write method because transcripts are authored by the voice
 * gateway as a conversation happens -- a client-authored turn would be a record of something the
 * recogniser never produced.
 *
 * Ownership is enforced server-side from the access token, and a conversation belonging to another
 * student answers **404, not 403**, so possessing an id proves nothing. The client must treat a
 * 404 as "not available to you" rather than retrying or assuming it will appear later.
 */
interface ConversationsApi {

    /**
     * @param kind `career_coach` or `mock_interview`; omit for both. The server rejects any other
     *   value with 422 rather than silently returning everything.
     * @param limit 1..100. The server caps the page and rejects a larger request.
     */
    @GET("conversations")
    suspend fun listConversations(
        @Query("kind") kind: String? = null,
        @Query("limit") limit: Int = 20,
        @Query("offset") offset: Int = 0,
    ): List<ConversationDto>

    @GET("conversations/{conversationId}")
    suspend fun getConversation(
        @Path("conversationId") conversationId: Int,
    ): ConversationDetailDto

    /** 204 on success, 404 when it is not there or not this student's. */
    @DELETE("conversations/{conversationId}")
    suspend fun deleteConversation(
        @Path("conversationId") conversationId: Int,
    )
}
