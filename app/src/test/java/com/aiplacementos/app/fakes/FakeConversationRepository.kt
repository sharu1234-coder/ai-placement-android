package com.aiplacementos.app.fakes

import com.aiplacementos.app.data.remote.ConversationDetailDto
import com.aiplacementos.app.data.remote.ConversationDto
import com.aiplacementos.app.data.remote.ConversationTurnDto
import com.aiplacementos.app.domain.repository.ConversationRepository

/**
 * A conversation history that answers however a test needs it to.
 *
 * Records the arguments it was called with, because several of the behaviours under test are about
 * *what the client asked the server* -- that the `kind` filter is sent as the server's own wire
 * value, and that a next page is requested at the right offset.
 */
class FakeConversationRepository : ConversationRepository {

    /** Every (kind, limit, offset) the view model asked for, in order. */
    val listCalls = mutableListOf<ListCall>()
    val deletedIds = mutableListOf<Int>()

    data class ListCall(val kind: String?, val limit: Int, val offset: Int)

    /** Answers a list request. Throw from here to exercise a failure path. */
    var onList: (ListCall) -> List<ConversationDto> = { emptyList() }

    var onDetail: (Int) -> ConversationDetailDto = { id -> conversationDetail(id) }

    /** Suspending, so a test can hold a delete open and assert the in-flight guard. */
    var onDelete: suspend (Int) -> Unit = { }

    override suspend fun listConversations(
        kind: String?,
        limit: Int,
        offset: Int,
    ): List<ConversationDto> {
        val call = ListCall(kind, limit, offset)
        listCalls += call
        return onList(call)
    }

    override suspend fun getConversation(conversationId: Int): ConversationDetailDto =
        onDetail(conversationId)

    override suspend fun deleteConversation(conversationId: Int) {
        deletedIds += conversationId
        onDelete(conversationId)
    }
}

fun conversation(
    id: Int,
    kind: String = "career_coach",
    status: String = "completed",
    title: String? = null,
    interviewId: Int? = null,
    createdAt: String = "2026-10-17T21:30:00",
    updatedAt: String = "2026-10-17T21:35:00",
): ConversationDto = ConversationDto(
    id = id,
    kind = kind,
    status = status,
    mode = null,
    title = title,
    interviewId = interviewId,
    createdAt = createdAt,
    updatedAt = updatedAt,
    endedAt = null,
)

fun turn(
    seq: Int,
    role: String,
    text: String,
    createdAt: String = "2026-10-17T21:30:00",
): ConversationTurnDto = ConversationTurnDto(
    seq = seq,
    role = role,
    text = text,
    interviewQuestionId = null,
    createdAt = createdAt,
)

fun conversationDetail(
    id: Int = 1,
    kind: String = "career_coach",
    status: String = "completed",
    interviewId: Int? = null,
    turns: List<ConversationTurnDto> = listOf(
        turn(1, "user", "How do I prepare?"),
        turn(2, "assistant", "Start with SQL."),
    ),
): ConversationDetailDto = ConversationDetailDto(
    id = id,
    kind = kind,
    status = status,
    mode = null,
    title = null,
    interviewId = interviewId,
    createdAt = "2026-10-17T21:30:00",
    updatedAt = "2026-10-17T21:35:00",
    endedAt = null,
    turns = turns,
)
