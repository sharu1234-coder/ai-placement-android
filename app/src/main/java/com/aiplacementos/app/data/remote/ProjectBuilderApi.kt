package com.aiplacementos.app.data.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface ProjectBuilderApi {
    @POST("project-builder")
    suspend fun createBuild(@Body request: CreateProjectBuildRequestDto): ProjectBuildDto

    @GET("project-builder")
    suspend fun listBuilds(): List<ProjectBuildListItemDto>

    @GET("project-builder/{buildId}")
    suspend fun getBuild(@Path("buildId") buildId: Int): ProjectBuildDto

    @POST("project-builder/{buildId}/stages/{stageKey}/start")
    suspend fun startStage(@Path("buildId") buildId: Int, @Path("stageKey") stageKey: String): ProjectBuildStageDto

    @POST("project-builder/{buildId}/stages/{stageKey}/complete")
    suspend fun completeStage(
        @Path("buildId") buildId: Int,
        @Path("stageKey") stageKey: String,
        @Body request: CompleteStageRequestDto,
    ): ProjectBuildStageDto

    @GET("project-builder/{buildId}/proof")
    suspend fun getProof(@Path("buildId") buildId: Int): ProjectProofDto

    @POST("project-builder/{buildId}/github-check")
    suspend fun checkGithub(@Path("buildId") buildId: Int, @Body request: CheckGithubRequestDto): GitHubEvidenceDto
}
