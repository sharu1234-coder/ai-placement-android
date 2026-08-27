package com.aiplacementos.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SelectCareerRoleRequestDto(
    @SerialName("role_id") val roleId: Int? = null,
    @SerialName("custom_role") val customRole: String? = null,
)

@Serializable
data class UpdateSkillItemRequestDto(
    val status: String,
)

@Serializable
data class UpdateProjectRequestDto(
    val status: String,
)

@Serializable
data class RoleCatalogEntryDto(
    @SerialName("family_key") val familyKey: String,
    @SerialName("display_name") val displayName: String,
    val category: String,
    @SerialName("short_explanation") val shortExplanation: String,
)

@Serializable
data class RoleOverviewDto(
    @SerialName("display_name") val displayName: String,
    val category: String,
    @SerialName("short_explanation") val shortExplanation: String,
    val responsibilities: List<String>,
    @SerialName("core_skills") val coreSkills: List<String>,
    val tools: List<String>,
    val difficulty: String,
    @SerialName("entry_level_expectations") val entryLevelExpectations: String,
    @SerialName("interview_focus") val interviewFocus: List<String>,
    @SerialName("portfolio_expectations") val portfolioExpectations: String,
)

@Serializable
data class SkillItemDto(
    val id: Int,
    @SerialName("phase_key") val phaseKey: String,
    @SerialName("skill_key") val skillKey: String,
    val title: String,
    val level: String, // beginner/intermediate/advanced
    @SerialName("why_it_matters") val whyItMatters: String? = null,
    @SerialName("order_index") val orderIndex: Int,
    val status: String, // not_started/in_progress/completed
    @SerialName("completed_via") val completedVia: String? = null,
    @SerialName("has_practice") val hasPractice: Boolean,
)

@Serializable
data class CareerPathProjectDto(
    val id: Int,
    @SerialName("project_key") val projectKey: String,
    val title: String,
    val difficulty: String,
    @SerialName("skills_demonstrated") val skillsDemonstrated: List<String>,
    val technologies: List<String>,
    @SerialName("problem_statement") val problemStatement: String? = null,
    @SerialName("expected_outcome") val expectedOutcome: String? = null,
    @SerialName("what_should_be_included") val whatShouldBeIncluded: String? = null,
    @SerialName("why_recruiters_value") val whyRecruitersValue: String? = null,
    @SerialName("resume_bullet_suggestion") val resumeBulletSuggestion: String? = null,
    @SerialName("interview_questions") val interviewQuestions: List<String>,
    @SerialName("order_index") val orderIndex: Int,
    val status: String,
)

@Serializable
data class CareerPathPhaseDto(
    @SerialName("phase_key") val phaseKey: String,
    val title: String,
    val objective: String? = null,
    @SerialName("order_index") val orderIndex: Int,
    val status: String, // not_started/in_progress/completed
    @SerialName("skill_items") val skillItems: List<SkillItemDto>,
    val projects: List<CareerPathProjectDto>,
)

@Serializable
data class SkillGapItemDto(
    val skill: String,
    val status: String, // strong/needs_improvement/missing/recommended
    val evidence: String? = null,
)

@Serializable
data class ResumeConnectionDto(
    @SerialName("skills_demonstrated") val skillsDemonstrated: Int,
    @SerialName("skills_total") val skillsTotal: Int,
    @SerialName("projects_demonstrated") val projectsDemonstrated: Int,
    @SerialName("projects_total") val projectsTotal: Int,
    @SerialName("role_keyword_coverage_percent") val roleKeywordCoveragePercent: Int,
    @SerialName("missing_evidence") val missingEvidence: List<String>,
)

@Serializable
data class InterviewPrepCategorySummaryDto(
    val category: String,
    @SerialName("questions_completed") val questionsCompleted: Int,
    @SerialName("accuracy_percent") val accuracyPercent: Int? = null,
    @SerialName("weak_areas") val weakAreas: List<String>,
)

@Serializable
data class JobReadinessDto(
    @SerialName("overall_percent") val overallPercent: Int,
    @SerialName("skills_percent") val skillsPercent: Int,
    @SerialName("projects_percent") val projectsPercent: Int,
    @SerialName("resume_percent") val resumePercent: Int,
    @SerialName("practice_percent") val practicePercent: Int,
    @SerialName("interview_percent") val interviewPercent: Int,
    @SerialName("top_focus_areas") val topFocusAreas: List<String>,
    @SerialName("is_estimate") val isEstimate: Boolean = false,
)

@Serializable
data class CareerPathSummaryDto(
    val id: Int,
    @SerialName("role_family_key") val roleFamilyKey: String,
    @SerialName("display_role_name") val displayRoleName: String,
    @SerialName("is_primary") val isPrimary: Boolean,
    @SerialName("readiness_percent") val readinessPercent: Int,
    @SerialName("completed_items") val completedItems: Int,
    @SerialName("total_items") val totalItems: Int,
    @SerialName("generated_via") val generatedVia: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
)

@Serializable
data class CareerPathDto(
    val id: Int,
    @SerialName("role_id") val roleId: Int? = null,
    @SerialName("custom_role") val customRole: String? = null,
    @SerialName("role_family_key") val roleFamilyKey: String,
    @SerialName("display_role_name") val displayRoleName: String,
    @SerialName("is_primary") val isPrimary: Boolean,
    @SerialName("generated_via") val generatedVia: String,
    @SerialName("role_overview") val roleOverview: RoleOverviewDto,
    val phases: List<CareerPathPhaseDto>,
    @SerialName("skill_gaps") val skillGaps: List<SkillGapItemDto>,
    @SerialName("resume_connection") val resumeConnection: ResumeConnectionDto? = null,
    @SerialName("interview_prep") val interviewPrep: List<InterviewPrepCategorySummaryDto>,
    @SerialName("job_readiness") val jobReadiness: JobReadinessDto,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
)
