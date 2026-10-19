package com.aiplacementos.app.data.remote

import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * The confirmed-action endpoints (VA-7).
 *
 * `preview` writes nothing and mints a single-use confirmation bound to what it described.
 * `execute` spends that confirmation, runs the mutation behind the server's operation-claim
 * guard and reports a state read back from the database.
 *
 * The `Idempotency-Key` is required here rather than optional, unlike the other mutating calls
 * in this app. The server refuses an action without one, on purpose: a key minted server-side
 * would be unique to that request, so the claim row it created could never match the duplicate
 * it exists to collapse. [OperationKeyHolder] is where the key comes from -- one per logical
 * intent, held across retries, not one per network attempt.
 */
interface ActionsApi {
    @POST("actions/preview")
    suspend fun preview(@Body body: ActionRequestDto): ActionPreviewDto

    @POST("actions/execute")
    suspend fun execute(
        @Body body: ActionExecuteRequestDto,
        @Header("Idempotency-Key") idempotencyKey: String,
    ): ActionOutcomeDto
}
