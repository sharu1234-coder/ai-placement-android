package com.aiplacementos.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProjectBuildStageDto(
    @SerialName("stage_key") val stageKey: String,
    val title: String,
    @SerialName("order_index") val orderIndex: Int,
    val status: String, // not_started | in_progress | completed
    @SerialName("guidance_text") val guidanceText: String,
    val notes: String? = null,
    @SerialName("started_at") val startedAt: String? = null,
    @SerialName("completed_at") val completedAt: String? = null,
)

@Serializable
data class ProjectBuildListItemDto(
    val id: Int,
    val title: String,
    @SerialName("target_role_name") val targetRoleName: String? = null,
    val status: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("completed_at") val completedAt: String? = null,
)

@Serializable
data class GitHubEvidenceDto(
    @SerialName("repo_url") val repoUrl: String,
    val owner: String,
    @SerialName("repo_name") val repoName: String,
    @SerialName("repo_exists") val repoExists: Boolean,
    @SerialName("primary_language") val primaryLanguage: String? = null,
    @SerialName("has_readme") val hasReadme: Boolean = false,
    @SerialName("readme_snippet") val readmeSnippet: String? = null,
    val stars: Int = 0,
    @SerialName("last_pushed_at") val lastPushedAt: String? = null,
    @SerialName("matches_declared_stack") val matchesDeclaredStack: Boolean? = null,
    @SerialName("evidence_label") val evidenceLabel: String,
    @SerialName("checked_at") val checkedAt: String,
)

@Serializable
data class ProjectBuildDto(
    val id: Int,
    val title: String,
    @SerialName("target_role_name") val targetRoleName: String? = null,
    val status: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("completed_at") val completedAt: String? = null,
    @SerialName("stack_summary") val stackSummary: String? = null,
    val stages: List<ProjectBuildStageDto> = emptyList(),
    @SerialName("github_evidence") val githubEvidence: GitHubEvidenceDto? = null,
)

@Serializable
data class CreateProjectBuildRequestDto(val title: String, @SerialName("stack_summary") val stackSummary: String? = null)

@Serializable
data class CompleteStageRequestDto(val notes: String? = null)

@Serializable
data class CheckGithubRequestDto(@SerialName("repo_url") val repoUrl: String)

@Serializable
data class ProjectProofDto(
    @SerialName("resume_bullet") val resumeBullet: String,
    @SerialName("readme_markdown") val readmeMarkdown: String,
    @SerialName("explanation_60s") val explanation60s: String,
    @SerialName("explanation_5min") val explanation5min: String,
    @SerialName("defense_questions") val defenseQuestions: List<String>,
)
