package com.aiplacementos.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ParseJdRequestDto(@SerialName("raw_text") val rawText: String)

@Serializable
data class RequirementDto(
    val id: Int,
    val category: String,
    val value: String,
    @SerialName("is_explicit") val isExplicit: Boolean,
    val importance: String,
)

@Serializable
data class JobDescriptionParseResponseDto(
    val id: Int,
    @SerialName("role_title") val roleTitle: String,
    @SerialName("detected_role_id") val detectedRoleId: Int?,
    @SerialName("parsing_confidence") val parsingConfidence: Double,
    val requirements: List<RequirementDto>,
)

@Serializable
data class SkillGapDto(
    val id: Int,
    @SerialName("skill_name") val skillName: String,
    @SerialName("gap_severity") val gapSeverity: String,
    val source: String,
)

@Serializable
data class JobMatchDto(
    val id: Int,
    @SerialName("matching_skills") val matchingSkills: List<String>,
    @SerialName("missing_skills") val missingSkills: List<String>,
    @SerialName("match_score") val matchScore: Double,
    @SerialName("skill_gaps") val skillGaps: List<SkillGapDto>,
)

@Serializable
data class JobDescriptionListItemDto(
    val id: Int,
    @SerialName("role_title") val roleTitle: String,
    val location: String?,
    @SerialName("created_at") val createdAt: String,
    @SerialName("has_preparation_plan") val hasPreparationPlan: Boolean,
    @SerialName("preparation_plan_company_name") val preparationPlanCompanyName: String?,
    @SerialName("preparation_plan_job_title") val preparationPlanJobTitle: String?,
)

@Serializable
data class GeneratePreparationPlanRequestDto(
    @SerialName("company_name") val companyName: String? = null,
    @SerialName("job_title") val jobTitle: String? = null,
)

@Serializable
data class JobSummaryDto(
    @SerialName("company_name") val companyName: String?,
    @SerialName("job_title") val jobTitle: String,
    @SerialName("experience_expectation") val experienceExpectation: String?,
    @SerialName("education_eligibility") val educationEligibility: String?,
    val location: String?,
    @SerialName("employment_type") val employmentType: String?,
)

@Serializable
data class RequiredSkillDto(val category: String, val value: String, val importance: String)

@Serializable
data class SkillGapStatusDto(val skill: String, val status: String)

@Serializable
data class PreparationPlanCategoryDto(val category: String, val reason: String)

@Serializable
data class InterviewPrepQuestionDto(val bucket: String, val question: String)

/** Grounded guidance for one skill gap. `skill`, `severity` and `isExplicitRequirement` are
 * decided server-side by deterministic matching, never by the AI -- the screen can present them
 * as facts rather than suggestions. */
@Serializable
data class GapGuidanceDto(
    val skill: String,
    val severity: String,
    @SerialName("is_explicit_requirement") val isExplicitRequirement: Boolean,
    @SerialName("requirement_importance") val requirementImportance: String,
    @SerialName("why_it_matters") val whyItMatters: String,
    @SerialName("recommended_action") val recommendedAction: String,
    @SerialName("practice_direction") val practiceDirection: String,
)

/** One suggested resume change. `currentFact` is what the resume says today and `suggestion` is
 * a re-wording of it -- the app only ever displays these; nothing here edits the uploaded PDF. */
@Serializable
data class ResumeRecommendationItemDto(
    val area: String,
    val requirement: String? = null,
    @SerialName("current_fact") val currentFact: String,
    val suggestion: String,
    val rationale: String,
)

/** `status` carries why the list may be empty, so the screen never has to guess between
 * "your resume already covers this job" and "there is no resume to look at". */
@Serializable
data class ResumeRecommendationsDto(
    val status: String,
    @SerialName("missing_keywords") val missingKeywords: List<String>,
    val items: List<ResumeRecommendationItemDto>,
)

/** A JD requirement tied to a project the student genuinely has. The backend only emits these
 * for projects it matched deterministically against the student's own records. */
@Serializable
data class ProjectConnectionDto(
    val requirement: String,
    @SerialName("project_title") val projectTitle: String,
    @SerialName("project_source") val projectSource: String,
    @SerialName("why_relevant") val whyRelevant: String,
    @SerialName("likely_questions") val likelyQuestions: List<String>,
)

@Serializable
data class JobPreparationPlanDto(
    val id: Int,
    @SerialName("jd_id") val jdId: Int,
    @SerialName("company_name") val companyName: String?,
    @SerialName("job_title") val jobTitle: String?,
    @SerialName("job_summary") val jobSummary: JobSummaryDto,
    @SerialName("required_skills") val requiredSkills: List<RequiredSkillDto>,
    @SerialName("preparation_gaps") val preparationGaps: List<SkillGapStatusDto>,
    @SerialName("preparation_plan") val preparationPlan: List<PreparationPlanCategoryDto>,
    @SerialName("interview_prep") val interviewPrep: List<InterviewPrepQuestionDto>,
    // Nullable and defaulted: plans saved before these sections existed come back without
    // them, and the screen shows what it has rather than failing to parse the whole plan.
    @SerialName("gap_guidance") val gapGuidance: List<GapGuidanceDto>? = null,
    @SerialName("resume_recommendations") val resumeRecommendations: ResumeRecommendationsDto? = null,
    @SerialName("project_connections") val projectConnections: List<ProjectConnectionDto>? = null,
    @SerialName("created_at") val createdAt: String,
)
