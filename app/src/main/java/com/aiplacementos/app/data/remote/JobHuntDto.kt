package com.aiplacementos.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Job Hunt wire types.
 *
 * Deliberately separate from JobDto.kt, which belongs to the Job Description Analyzer: that
 * feature scores a student against one JD they pasted in themselves, this one against real
 * adverts pulled from a job provider. Sharing types would have forced every field to be
 * nullable for one of the two callers.
 *
 * Nullability here is meaningful, not defensive. A null field means the provider did not supply
 * that fact, and the UI must omit it rather than substitute a placeholder -- showing "Posted
 * today" for an advert with no posted date, or "On-site" for one that never said, would be
 * fabricated content.
 */

@Serializable
data class JobSearchProfileDto(
    @SerialName("resume_id") val resumeId: Int? = null,
    @SerialName("target_role_id") val targetRoleId: Int? = null,
    @SerialName("target_role_title") val targetRoleTitle: String? = null,
    @SerialName("declared_skills") val declaredSkills: List<String> = emptyList(),
    @SerialName("normalized_skills") val normalizedSkills: List<String> = emptyList(),
    @SerialName("preferred_location") val preferredLocation: String? = null,
    @SerialName("work_mode") val workMode: String? = null,
    @SerialName("experience_level") val experienceLevel: String? = null,
    @SerialName("employment_type") val employmentType: String? = null,
    @SerialName("salary_min_expected") val salaryMinExpected: Int? = null,
    @SerialName("salary_currency") val salaryCurrency: String? = null,
    @SerialName("preferred_companies") val preferredCompanies: List<String> = emptyList(),
    @SerialName("open_to_relocation") val openToRelocation: Boolean? = null,
    // Per-field origin: explicit | resume | evidence | inferred. Shown beside each value so a
    // student can see what was assumed about them, and correct it.
    @SerialName("signal_provenance") val signalProvenance: Map<String, String> = emptyMap(),
    // Drives the setup screen. False means "we have nothing to search on" -- distinct from a
    // search that ran and found nothing.
    @SerialName("is_ready_to_search") val isReadyToSearch: Boolean = false,
    @SerialName("updated_at") val updatedAt: String? = null,
)

/**
 * Every field null means "leave unchanged", so a partial edit never silently clears the rest.
 * Clearing is therefore explicit via [clearResume]/[clearRole] rather than expressed as null.
 */
@Serializable
data class JobSearchProfileUpdateDto(
    @SerialName("resume_id") val resumeId: Int? = null,
    @SerialName("target_role_id") val targetRoleId: Int? = null,
    @SerialName("target_role_title") val targetRoleTitle: String? = null,
    @SerialName("declared_skills") val declaredSkills: List<String>? = null,
    @SerialName("preferred_location") val preferredLocation: String? = null,
    @SerialName("work_mode") val workMode: String? = null,
    @SerialName("experience_level") val experienceLevel: String? = null,
    @SerialName("employment_type") val employmentType: String? = null,
    @SerialName("salary_min_expected") val salaryMinExpected: Int? = null,
    @SerialName("preferred_companies") val preferredCompanies: List<String>? = null,
    @SerialName("open_to_relocation") val openToRelocation: Boolean? = null,
    @SerialName("clear_resume") val clearResume: Boolean = false,
    @SerialName("clear_role") val clearRole: Boolean = false,
)

@Serializable
data class RoleSuggestionsDto(
    @SerialName("suggested_role_families") val suggestedRoleFamilies: List<String> = emptyList(),
)

@Serializable
data class JobListingDto(
    val id: Int,
    val provider: String,
    val url: String,
    val title: String,
    val company: String? = null,
    val location: String? = null,
    @SerialName("work_mode") val workMode: String? = null,
    @SerialName("employment_type") val employmentType: String? = null,
    val seniority: String? = null,
    @SerialName("salary_min") val salaryMin: Int? = null,
    @SerialName("salary_max") val salaryMax: Int? = null,
    @SerialName("salary_currency") val salaryCurrency: String? = null,
    @SerialName("salary_period") val salaryPeriod: String? = null,
    // When true the figure is modelled by the provider, not advertised by the employer. Must
    // be labelled as an estimate wherever it is shown.
    @SerialName("salary_is_predicted") val salaryIsPredicted: Boolean = false,
    val description: String? = null,
    @SerialName("posted_at") val postedAt: String? = null,
    @SerialName("fetched_at") val fetchedAt: String,
    // The cached copy has outlived its provider's retention window. Surfaced as a "last
    // checked" caveat rather than hidden.
    @SerialName("is_stale") val isStale: Boolean = false,
)

// One skill an advert asks for, and how well the student can actually back it.
//
// The backend derives `status` from the same Evidence Engine that powers Career Twin and the
// Skill Passport, so "verified" means the same thing across the whole app. The client must
// never re-derive it -- treating a resume keyword as proof is exactly what this field exists
// to prevent.
@Serializable
data class SkillProvenanceDto(
    val skill: String,
    // verified_evidence | on_resume | missing | unclear
    val status: String,
    // claimed | supported | strong | verified | stale, or null for a skill the student lacks.
    @SerialName("evidence_state") val evidenceState: String? = null,
    val detail: String? = null,
)

// Named JobHuntMatchDto, not JobMatchDto: JobDto.kt already defines a JobMatchDto for the
// Job Description Analyzer, and the two are genuinely different shapes -- that one scores a
// student against a pasted JD, this one against a provider advert with component-level
// breakdown, unknown factors and a score version.
@Serializable
data class JobHuntMatchDto(
    @SerialName("total_score") val totalScore: Float,
    @SerialName("score_version") val scoreVersion: String,
    @SerialName("component_scores") val componentScores: Map<String, Float> = emptyMap(),
    @SerialName("matched_skills") val matchedSkills: List<String> = emptyList(),
    @SerialName("missing_skills") val missingSkills: List<String> = emptyList(),
    // Factors the provider gave nothing to judge on. Rendering these is what keeps a mid-range
    // score honest -- without them a 55% reads as "you are a weak candidate" rather than "we
    // could not tell".
    @SerialName("unknown_factors") val unknownFactors: List<String> = emptyList(),
    val rationale: String = "",
    val reasons: List<String> = emptyList(),
    // null when there is no real evidence to judge readiness on.
    @SerialName("apply_readiness") val applyReadiness: String? = null,
    // Every skill the advert asks for, bucketed into verified evidence / on your resume /
    // missing / unclear. Empty on the tracker list, which deliberately does not recompute it.
    @SerialName("skill_provenance") val skillProvenance: List<SkillProvenanceDto> = emptyList(),
    @SerialName("verified_skill_count") val verifiedSkillCount: Int = 0,
    // best_match | strong_fit | stretch_opportunity | skill_build_opportunity, or null when
    // nothing about the advert was evaluable. Describes measured fit, never an outcome.
    @SerialName("ranking_label") val rankingLabel: String? = null,
) {
    /** 0-100 for display. The backend keeps the score as a 0..1 float. */
    fun matchPercent(): Int = (totalScore * 100).toInt().coerceIn(0, 100)
}

@Serializable
data class JobResultDto(
    val listing: JobListingDto,
    val match: JobHuntMatchDto,
    @SerialName("saved_job_id") val savedJobId: Int? = null,
    @SerialName("saved_status") val savedStatus: String? = null,
)

@Serializable
data class JobSearchRequestDto(
    val keywords: String? = null,
    val location: String? = null,
    @SerialName("work_mode") val workMode: String? = null,
    @SerialName("employment_type") val employmentType: String? = null,
    @SerialName("experience_level") val experienceLevel: String? = null,
    @SerialName("salary_min") val salaryMin: Int? = null,
    @SerialName("max_days_old") val maxDaysOld: Int? = null,
    val page: Int = 1,
    @SerialName("page_size") val pageSize: Int = 20,
)

@Serializable
data class JobSearchResponseDto(
    val results: List<JobResultDto> = emptyList(),
    // null when the provider does not report a total -- never assume results.size is the whole
    // result set.
    @SerialName("total_available") val totalAvailable: Int? = null,
    val page: Int = 1,
    @SerialName("page_size") val pageSize: Int = 20,
    val provider: String = "",
    // Several providers make this a contractual condition of displaying their listings, so it
    // travels with the results rather than being hardcoded client-side where it could drift
    // from the provider actually in use.
    @SerialName("attribution_text") val attributionText: String = "",
    @SerialName("attribution_url") val attributionUrl: String? = null,
    @SerialName("fetched_at") val fetchedAt: String,
    @SerialName("duplicates_removed") val duplicatesRemoved: Int = 0,
    @SerialName("score_version") val scoreVersion: String = "",
)

@Serializable
data class SaveJobRequestDto(
    @SerialName("listing_id") val listingId: Int,
    val status: String = "saved",
)

@Serializable
data class UpdateSavedJobRequestDto(
    val status: String? = null,
    val notes: String? = null,
    @SerialName("follow_up_date") val followUpDate: String? = null,
)

@Serializable
data class SavedJobDto(
    val id: Int,
    val status: String,
    val notes: String? = null,
    @SerialName("follow_up_date") val followUpDate: String? = null,
    @SerialName("applied_at") val appliedAt: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    val listing: JobListingDto,
    // null when this student has no computed match for the listing yet.
    val match: JobHuntMatchDto? = null,
)

@Serializable
data class SavedSearchCreateDto(
    val name: String,
    val filters: JobSearchRequestDto,
    @SerialName("notify_enabled") val notifyEnabled: Boolean = false,
)

@Serializable
data class SavedSearchDto(
    val id: Int,
    val name: String,
    // Typed so a saved search can be re-run by replaying it straight back into the search
    // endpoint, rather than being a name the app can only display.
    val filters: JobSearchRequestDto = JobSearchRequestDto(),
    @SerialName("notify_enabled") val notifyEnabled: Boolean = false,
    @SerialName("last_run_at") val lastRunAt: String? = null,
    // null means "never run", which the UI must not render as zero results.
    @SerialName("last_result_count") val lastResultCount: Int? = null,
    @SerialName("created_at") val createdAt: String,
)
