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
    @SerialName("created_at") val createdAt: String,
)
