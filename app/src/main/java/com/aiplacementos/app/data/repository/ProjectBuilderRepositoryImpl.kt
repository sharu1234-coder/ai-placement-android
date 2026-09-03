package com.aiplacementos.app.data.repository

import com.aiplacementos.app.data.remote.CheckGithubRequestDto
import com.aiplacementos.app.data.remote.CompleteStageRequestDto
import com.aiplacementos.app.data.remote.CreateProjectBuildRequestDto
import com.aiplacementos.app.data.remote.GitHubEvidenceDto
import com.aiplacementos.app.data.remote.ProjectBuildDto
import com.aiplacementos.app.data.remote.ProjectBuildListItemDto
import com.aiplacementos.app.data.remote.ProjectBuildStageDto
import com.aiplacementos.app.data.remote.ProjectBuilderApi
import com.aiplacementos.app.data.remote.ProjectProofDto
import com.aiplacementos.app.domain.repository.ProjectBuilderRepository

class ProjectBuilderRepositoryImpl(private val projectBuilderApi: ProjectBuilderApi) : ProjectBuilderRepository {
    override suspend fun createBuild(title: String, stackSummary: String?): ProjectBuildDto =
        projectBuilderApi.createBuild(CreateProjectBuildRequestDto(title, stackSummary))

    override suspend fun listBuilds(): List<ProjectBuildListItemDto> = projectBuilderApi.listBuilds()

    override suspend fun getBuild(buildId: Int): ProjectBuildDto = projectBuilderApi.getBuild(buildId)

    override suspend fun startStage(buildId: Int, stageKey: String): ProjectBuildStageDto =
        projectBuilderApi.startStage(buildId, stageKey)

    override suspend fun completeStage(buildId: Int, stageKey: String, notes: String?): ProjectBuildStageDto =
        projectBuilderApi.completeStage(buildId, stageKey, CompleteStageRequestDto(notes))

    override suspend fun getProof(buildId: Int): ProjectProofDto = projectBuilderApi.getProof(buildId)

    override suspend fun checkGithub(buildId: Int, repoUrl: String): GitHubEvidenceDto =
        projectBuilderApi.checkGithub(buildId, CheckGithubRequestDto(repoUrl))
}
