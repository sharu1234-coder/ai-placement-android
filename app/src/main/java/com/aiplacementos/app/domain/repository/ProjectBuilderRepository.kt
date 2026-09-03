package com.aiplacementos.app.domain.repository

import com.aiplacementos.app.data.remote.GitHubEvidenceDto
import com.aiplacementos.app.data.remote.ProjectBuildDto
import com.aiplacementos.app.data.remote.ProjectBuildListItemDto
import com.aiplacementos.app.data.remote.ProjectBuildStageDto
import com.aiplacementos.app.data.remote.ProjectProofDto

interface ProjectBuilderRepository {
    suspend fun createBuild(title: String, stackSummary: String?): ProjectBuildDto
    suspend fun listBuilds(): List<ProjectBuildListItemDto>
    suspend fun getBuild(buildId: Int): ProjectBuildDto
    suspend fun startStage(buildId: Int, stageKey: String): ProjectBuildStageDto
    suspend fun completeStage(buildId: Int, stageKey: String, notes: String?): ProjectBuildStageDto
    suspend fun getProof(buildId: Int): ProjectProofDto
    suspend fun checkGithub(buildId: Int, repoUrl: String): GitHubEvidenceDto
}
