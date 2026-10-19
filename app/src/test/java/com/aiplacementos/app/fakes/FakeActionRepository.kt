package com.aiplacementos.app.fakes

import com.aiplacementos.app.data.remote.ActionOutcomeDto
import com.aiplacementos.app.data.remote.ActionPreviewDto
import com.aiplacementos.app.domain.repository.ActionRepository

/**
 * Records every call rather than only the last one.
 *
 * The properties under test are about how many times something happened and with what -- one
 * execute per confirmation, the same idempotency key on a deliberate retry, a different one
 * after a verified success -- and none of those is observable from a fake that keeps a single
 * slot. It reproduces none of the server's guards on purpose: a fake that refused a reused
 * token would make the client's own single-use rule untestable, because the fake would be the
 * thing refusing.
 */
class FakeActionRepository : ActionRepository {
    data class ExecuteCall(
        val capability: String,
        val arguments: Map<String, Int>,
        val confirmationToken: String,
        val idempotencyKey: String,
    )

    var previewResult: (suspend (String, Map<String, Int>) -> ActionPreviewDto)? = null
    var executeResult: (suspend (ExecuteCall) -> ActionOutcomeDto)? = null

    val previews = mutableListOf<Pair<String, Map<String, Int>>>()
    val executes = mutableListOf<ExecuteCall>()

    override suspend fun preview(capability: String, arguments: Map<String, Int>): ActionPreviewDto {
        previews += capability to arguments
        val fn = requireNotNull(previewResult) { "previewResult not configured" }
        return fn(capability, arguments)
    }

    override suspend fun execute(
        capability: String,
        arguments: Map<String, Int>,
        confirmationToken: String,
        idempotencyKey: String,
    ): ActionOutcomeDto {
        val call = ExecuteCall(capability, arguments, confirmationToken, idempotencyKey)
        executes += call
        val fn = requireNotNull(executeResult) { "executeResult not configured" }
        return fn(call)
    }
}
