package com.aiplacementos.app.data.repository

import com.aiplacementos.app.data.remote.ActionExecuteRequestDto
import com.aiplacementos.app.data.remote.ActionOutcomeDto
import com.aiplacementos.app.data.remote.ActionPreviewDto
import com.aiplacementos.app.data.remote.ActionRequestDto
import com.aiplacementos.app.data.remote.ActionsApi
import com.aiplacementos.app.domain.repository.ActionRepository

class ActionRepositoryImpl(private val actionsApi: ActionsApi) : ActionRepository {
    override suspend fun preview(capability: String, arguments: Map<String, Int>): ActionPreviewDto =
        actionsApi.preview(ActionRequestDto(capability = capability, arguments = arguments))

    override suspend fun execute(
        capability: String,
        arguments: Map<String, Int>,
        confirmationToken: String,
        idempotencyKey: String,
    ): ActionOutcomeDto = actionsApi.execute(
        ActionExecuteRequestDto(
            capability = capability,
            arguments = arguments,
            confirmationToken = confirmationToken,
        ),
        idempotencyKey,
    )
}
