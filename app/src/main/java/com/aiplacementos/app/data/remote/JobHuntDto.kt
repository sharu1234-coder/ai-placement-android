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
    // best_match | newest | salary. Re-orders the same scored set; never re-scores it.
    val sort: String = "best_match",
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
    // Which screen the tap came from. Only the client knows, and it is a display label rather
    // than an entitlement or an ownership claim, so it is safe for the client to assert.
    // recommended | saved_search | job_alert | manual
    val source: String = "manual",
    @SerialName("source_saved_search_id") val sourceSavedSearchId: Int? = null,
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
    // How this job reached the student, recorded at save time and never recomputed.
    val source: String = "manual",
    @SerialName("source_saved_search_id") val sourceSavedSearchId: Int? = null,
    // Null when that saved search has since been deleted. The row keeps its provenance; the
    // UI falls back to the generic label rather than claiming the job was saved manually.
    @SerialName("source_saved_search_name") val sourceSavedSearchName: String? = null,
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
    @SerialName("last_success_at") val lastSuccessAt: String? = null,
    // Set when a run failed. Distinct from lastRunAt so the UI can tell a search that went
    // quiet because the market is quiet from one that went quiet because we could not ask.
    @SerialName("last_error_at") val lastErrorAt: String? = null,
    // null means "never run", which the UI must not render as zero results.
    @SerialName("last_result_count") val lastResultCount: Int? = null,
    @SerialName("has_alert") val hasAlert: Boolean = false,
    @SerialName("created_at") val createdAt: String,
)

// -- Skill match analysis --------------------------------------------------------------------

/**
 * One row of the Skill Match Analysis screen.
 *
 * Both fields arrive from the server and neither is derived from the other on the client.
 * `status` says where the claim comes from; `strength` says how it reads to a recruiter. They
 * are related but not identical -- a resume-only skill is a real match on a CV screen and only
 * a claim to us -- and deriving one from the other here would put that rule in two places.
 */
@Serializable
data class SkillMatchRowDto(
    val skill: String,
    // verified_evidence | on_resume | missing | unclear
    val status: String,
    // strong_match | match | skill_gap | unclear
    val strength: String,
    @SerialName("evidence_state") val evidenceState: String? = null,
    val detail: String? = null,
)

@Serializable
data class SkillMatchAnalysisDto(
    @SerialName("total_score") val totalScore: Float,
    @SerialName("score_version") val scoreVersion: String = "",
    val rows: List<SkillMatchRowDto> = emptyList(),
    @SerialName("verified_skill_count") val verifiedSkillCount: Int = 0,
    // Null when there is nothing actionable. Never a filler value to keep the card populated.
    @SerialName("biggest_gap_skill") val biggestGapSkill: String? = null,
) {
    fun matchPercent(): Int = (totalScore * 100).toInt().coerceIn(0, 100)
}

// -- Application readiness -------------------------------------------------------------------

@Serializable
data class ReadinessDimensionDto(
    val key: String,
    val label: String,
    // Null when this dimension could not be judged. The UI must render null as "not checked",
    // never as an empty bar -- a 0% bar says the student failed something they never attempted.
    val score: Float? = null,
    val detail: String,
)

@Serializable
data class ReadinessGapDto(
    val kind: String,
    val skill: String? = null,
    val title: String,
    val detail: String,
    // roadmap | skill_passport | resume | interview | education
    val action: String,
)

@Serializable
data class ApplicationReadinessDto(
    @SerialName("listing_id") val listingId: Int,
    val overall: Float? = null,
    val band: String? = null,
    val dimensions: List<ReadinessDimensionDto> = emptyList(),
    @SerialName("unknown_dimensions") val unknownDimensions: List<String> = emptyList(),
    @SerialName("biggest_gap") val biggestGap: ReadinessGapDto? = null,
    val tips: List<String> = emptyList(),
) {
    /** Null rather than 0 when nothing could be judged, so the ring can show its own empty
     * state instead of a confident-looking zero. */
    fun overallPercent(): Int? = overall?.let { (it * 100).toInt().coerceIn(0, 100) }
}

// -- Home ------------------------------------------------------------------------------------

@Serializable
data class JobHuntHomeDto(
    // Counts over this student's own rows. Never a claim about the job market as a whole.
    @SerialName("new_jobs") val newJobs: Int = 0,
    @SerialName("best_matches") val bestMatches: Int = 0,
    val applied: Int = 0,
    val saved: Int = 0,
    @SerialName("target_role_title") val targetRoleTitle: String? = null,
    @SerialName("is_ready_to_search") val isReadyToSearch: Boolean = false,
    val recommended: List<JobResultDto> = emptyList(),
)

// -- Job alerts --------------------------------------------------------------------------------

@Serializable
data class JobAlertDto(
    val id: Int,
    val name: String,
    @SerialName("saved_search_id") val savedSearchId: Int? = null,
    @SerialName("saved_search_name") val savedSearchName: String? = null,
    val filters: JobSearchRequestDto = JobSearchRequestDto(),
    // daily | weekly
    val frequency: String = "daily",
    // 0 = Monday. Only meaningful for a weekly alert.
    @SerialName("day_of_week") val dayOfWeek: Int? = null,
    @SerialName("hour_local") val hourLocal: Int = 9,
    @SerialName("quiet_hours_start") val quietHoursStart: Int? = null,
    @SerialName("quiet_hours_end") val quietHoursEnd: Int? = null,
    // The consent itself. Defaults to off on the server too.
    @SerialName("is_enabled") val isEnabled: Boolean = false,
    @SerialName("last_run_at") val lastRunAt: String? = null,
    @SerialName("last_success_at") val lastSuccessAt: String? = null,
    @SerialName("last_error_at") val lastErrorAt: String? = null,
    // success | no_new_jobs | provider_error | not_configured | skipped_quiet_hours
    @SerialName("last_run_status") val lastRunStatus: String? = null,
    // Null means never run. Never zero, which would read as "we looked and found nothing".
    @SerialName("last_new_job_count") val lastNewJobCount: Int? = null,
    @SerialName("created_at") val createdAt: String = "",
)

@Serializable
data class JobAlertCreateDto(
    val name: String,
    @SerialName("saved_search_id") val savedSearchId: Int? = null,
    val filters: JobSearchRequestDto? = null,
    val frequency: String = "daily",
    @SerialName("day_of_week") val dayOfWeek: Int? = null,
    @SerialName("hour_local") val hourLocal: Int = 9,
    @SerialName("quiet_hours_start") val quietHoursStart: Int? = null,
    @SerialName("quiet_hours_end") val quietHoursEnd: Int? = null,
    @SerialName("is_enabled") val isEnabled: Boolean = false,
)

@Serializable
data class JobAlertUpdateDto(
    val name: String? = null,
    val frequency: String? = null,
    @SerialName("day_of_week") val dayOfWeek: Int? = null,
    @SerialName("hour_local") val hourLocal: Int? = null,
    @SerialName("quiet_hours_start") val quietHoursStart: Int? = null,
    @SerialName("quiet_hours_end") val quietHoursEnd: Int? = null,
    @SerialName("is_enabled") val isEnabled: Boolean? = null,
    // Explicit, because null means "unchanged" on the two fields above and there would
    // otherwise be no way to remove a quiet window once set.
    @SerialName("clear_quiet_hours") val clearQuietHours: Boolean = false,
)

@Serializable
data class JobAlertHistoryDto(
    val id: Int,
    @SerialName("alert_id") val alertId: Int,
    @SerialName("alert_name") val alertName: String? = null,
    val listing: JobListingDto,
    @SerialName("notified_at") val notifiedAt: String,
)

@Serializable
data class JobAlertQuotaDto(
    val used: Int = 0,
    val limit: Int = 1,
    @SerialName("is_premium") val isPremium: Boolean = false,
) {
    val hasRoom: Boolean get() = used < limit
}
