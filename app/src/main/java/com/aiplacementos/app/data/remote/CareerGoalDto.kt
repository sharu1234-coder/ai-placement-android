package com.aiplacementos.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Career Goal -- "prepare me for THIS job".
 *
 * Two conventions carry through this whole file and neither is cosmetic.
 *
 * Nullable means absent, never zero. `evidenceCoveragePercent` is null when there is nothing to
 * measure, and rendering it as 0% would tell the student their evidence covers none of the job
 * when in truth nothing has been measured at all. Same rule as the ungraded interview
 * evaluation and the not-started Career Path readiness bar.
 *
 * Verdicts describe findings, never people. `verdictLabel` is written server-side precisely so
 * that the client shows what was found rather than composing a judgement of its own.
 */

@Serializable
data class CareerGoalListItemDto(
    val id: Int,
    val title: String,
    val company: String,
    val location: String? = null,
    val status: String, // active | paused | completed | archived
    @SerialName("is_analyzed") val isAnalyzed: Boolean,
    // not_analyzed | not_started | in_progress | ready_for_review
    @SerialName("preparation_state") val preparationState: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
)

@Serializable
data class CareerGoalCreateRequest(
    val title: String,
    val company: String,
    val location: String? = null,
    @SerialName("job_description") val jobDescription: String,
)

/** Every field optional: a PATCH that only moves the status must not have to echo back the
 * whole job description. Omitted means "leave alone". */
@Serializable
data class CareerGoalUpdateRequest(
    val title: String? = null,
    val company: String? = null,
    val location: String? = null,
    @SerialName("job_description") val jobDescription: String? = null,
    val status: String? = null,
)

@Serializable
data class GoalRequirementDto(
    val category: String, // skill | tool | responsibility | experience | education
    val value: String,
    val importance: String, // required | preferred
    // false means the parser inferred this rather than finding it in the posting. Shown, because
    // presenting an inference as a stated requirement is the fabrication this feature avoids.
    @SerialName("is_explicit") val isExplicit: Boolean,
)

@Serializable
data class GoalSkillEvidenceDto(
    val skill: String,
    val importance: String,
    @SerialName("is_explicit") val isExplicit: Boolean,
    // claimed | supported | strong | verified | stale -- the Evidence Engine's own vocabulary,
    // identical to the Skill Passport's, so the two screens can never disagree about a skill.
    @SerialName("evidence_state") val evidenceState: String,
    @SerialName("evidence_band") val evidenceBand: String,
    val verdict: String, // evidence_found | limited_evidence | no_evidence
    @SerialName("verdict_label") val verdictLabel: String,
    @SerialName("evidence_count") val evidenceCount: Int,
    val sources: List<String> = emptyList(),
    // A real verification date. Self-asserted evidence never sets it, so it can be null even
    // when evidence exists.
    @SerialName("last_verified_at") val lastVerifiedAt: String? = null,
)

@Serializable
data class GoalInterviewRiskDto(
    @SerialName("skill_tag") val skillTag: String,
    val label: String,
    val severity: String, // medium | high
    val sources: List<String> = emptyList(),
    // From Interview Memory. Null means no cross-interview record exists -- NOT zero
    // occurrences, which would claim the dimension had been looked at and found clean.
    val occurrences: Int? = null,
    @SerialName("memory_status") val memoryStatus: String? = null,
)

@Serializable
data class GoalPreparationItemDto(
    val key: String,
    val title: String,
    val detail: String,
    // career_strategy's existing destination vocabulary, resolved by one table in NavGraph.
    val destination: String,
    val skill: String? = null,
)

@Serializable
data class GoalProgressDto(
    // Null, never 0, when there is nothing to measure. See the file header.
    @SerialName("evidence_coverage_percent") val evidenceCoveragePercent: Int? = null,
    @SerialName("skills_total") val skillsTotal: Int = 0,
    @SerialName("skills_with_evidence") val skillsWithEvidence: Int = 0,
    // A count, so 0 here is a real measurement rather than an absence.
    @SerialName("interviews_since_created") val interviewsSinceCreated: Int = 0,
    @SerialName("open_risks") val openRisks: Int = 0,
    val state: String,
)

@Serializable
data class CareerGoalDetailDto(
    val id: Int,
    val title: String,
    val company: String,
    val location: String? = null,
    @SerialName("job_description") val jobDescription: String,
    val status: String,
    @SerialName("is_analyzed") val isAnalyzed: Boolean,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("analyzed_at") val analyzedAt: String? = null,
    @SerialName("detected_role_title") val detectedRoleTitle: String? = null,
    val requirements: List<GoalRequirementDto> = emptyList(),
    val aligned: List<GoalSkillEvidenceDto> = emptyList(),
    @SerialName("needs_attention") val needsAttention: List<GoalSkillEvidenceDto> = emptyList(),
    val unknown: List<GoalSkillEvidenceDto> = emptyList(),
    @SerialName("interview_risks") val interviewRisks: List<GoalInterviewRiskDto> = emptyList(),
    @SerialName("preparation_plan") val preparationPlan: List<GoalPreparationItemDto> = emptyList(),
    val progress: GoalProgressDto,
    // The existing profile-level recommendation, reused rather than re-ranked per job.
    // Reuses the Next Best Action DTO rather than declaring a parallel one -- it is
    // literally the same payload from the same engine, and a second type could drift.
    @SerialName("next_best_action") val nextBestAction: RecommendedActionDto? = null,
    // Sources that could not be looked at at all, named so the UI can say so. "No evidence for
    // this skill" and "we never looked at the one place your evidence lives" are different
    // statements, and only the first is a finding.
    @SerialName("unavailable_evidence_sources") val unavailableEvidenceSources: List<String> = emptyList(),
)

@Serializable
data class CareerGoalAnalyzeResponseDto(
    val id: Int,
    @SerialName("is_analyzed") val isAnalyzed: Boolean,
    @SerialName("detected_role_title") val detectedRoleTitle: String? = null,
    @SerialName("requirements_found") val requirementsFound: Int = 0,
    @SerialName("skills_found") val skillsFound: Int = 0,
)
