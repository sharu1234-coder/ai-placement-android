package com.aiplacementos.app.domain.repository

import com.aiplacementos.app.data.remote.ActionOutcomeDto
import com.aiplacementos.app.data.remote.ActionPreviewDto

/**
 * The confirmed-action path (VA-7).
 *
 * Deliberately two calls rather than one convenience method. A single `saveJob()` that previewed
 * and executed in one go would have no point at which the student could be shown what is about
 * to happen -- and a confirmation nobody saw is not a confirmation.
 */
interface ActionRepository {
    suspend fun preview(capability: String, arguments: Map<String, Int>): ActionPreviewDto

    suspend fun execute(
        capability: String,
        arguments: Map<String, Int>,
        confirmationToken: String,
        idempotencyKey: String,
    ): ActionOutcomeDto
}
