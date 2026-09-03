package com.aiplacementos.app.fakes

import com.aiplacementos.app.data.remote.GitHubEvidenceDto
import com.aiplacementos.app.data.remote.ProjectBuildDto
import com.aiplacementos.app.data.remote.ProjectBuildListItemDto
import com.aiplacementos.app.data.remote.ProjectBuildStageDto
import com.aiplacementos.app.data.remote.ProjectProofDto
import com.aiplacementos.app.domain.repository.ProjectBuilderRepository

class FakeProjectBuilderRepository : ProjectBuilderRepository {
    var createResult: (suspend (String, String?) -> ProjectBuildDto)? = null
    var listResult: (suspend () -> List<ProjectBuildListItemDto>)? = null
    var getBuildResult: (suspend (Int) -> ProjectBuildDto)? = null
    var startStageResult: (suspend (Int, String) -> ProjectBuildStageDto)? = null
    var completeStageResult: (suspend (Int, String, String?) -> ProjectBuildStageDto)? = null
    var proofResult: (suspend (Int) -> ProjectProofDto)? = null
    var checkGithubResult: (suspend (Int, String) -> GitHubEvidenceDto)? = null

    override suspend fun createBuild(title: String, stackSummary: String?): ProjectBuildDto {
        val fn = requireNotNull(createResult) { "createResult not configured" }
        return fn(title, stackSummary)
    }

    override suspend fun listBuilds(): List<ProjectBuildListItemDto> {
        val fn = requireNotNull(listResult) { "listResult not configured" }
        return fn()
    }

    override suspend fun getBuild(buildId: Int): ProjectBuildDto {
        val fn = requireNotNull(getBuildResult) { "getBuildResult not configured" }
        return fn(buildId)
    }

    override suspend fun startStage(buildId: Int, stageKey: String): ProjectBuildStageDto {
        val fn = requireNotNull(startStageResult) { "startStageResult not configured" }
        return fn(buildId, stageKey)
    }

    override suspend fun completeStage(buildId: Int, stageKey: String, notes: String?): ProjectBuildStageDto {
        val fn = requireNotNull(completeStageResult) { "completeStageResult not configured" }
        return fn(buildId, stageKey, notes)
    }

    override suspend fun getProof(buildId: Int): ProjectProofDto {
        val fn = requireNotNull(proofResult) { "proofResult not configured" }
        return fn(buildId)
    }

    override suspend fun checkGithub(buildId: Int, repoUrl: String): GitHubEvidenceDto {
        val fn = requireNotNull(checkGithubResult) { "checkGithubResult not configured" }
        return fn(buildId, repoUrl)
    }
}
