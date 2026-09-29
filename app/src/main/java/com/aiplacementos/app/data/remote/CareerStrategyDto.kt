package com.aiplacementos.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Career Strategy wire types.
 *
 * Two things to know before editing these.
 *
 * **The premium split is the server's, not this client's.** A free student's response simply
 * does not contain the queue, the evidence, the contradictions or the change history -- those
 * fields arrive empty because they were never serialised, not because something hid them. So
 * the UI must never treat "empty" as "hide it": `lockedSections` is what says whether an empty
 * list means "nothing to show" or "upgrade to see this", and the two look completely different
 * on screen.
 *
 * **Nullable means unknown, and unknown is never zero.** `evidenceCoveragePercent`, `impact`, `effort`
 * and every score here are null when the backend could not judge them. Rendering a null as 0%
 * would tell a student they failed at something they simply have not attempted yet. Every one
 * of these is defaulted so an older backend that does not send a field still deserializes
 * rather than crashing the screen.
 *
 * There is no hiring-probability field, and there is nowhere to add one.
 */
@Serializable
data class StrategyBottleneckDto(
    val id: String,
    val category: String,
    val subject: String,
    @SerialName("subject_kind") val subjectKind: String,
    val title: String,
    val destination: String,
    @SerialName("recommended_action") val recommendedAction: String,
    // Everything below here is Premium and arrives null/empty for a free student.
    val description: String? = null,
    val severity: String? = null,
    val impact: String? = null,
    val effort: String? = null,
    // A band (low/moderate/high), never a percentage -- the engine has no such resolution.
    val confidence: String? = null,
    val evidence: List<String> = emptyList(),
    @SerialName("affected_roles") val affectedRoles: List<String> = emptyList(),
    @SerialName("affected_job_ids") val affectedJobIds: List<Int> = emptyList(),
    @SerialName("reason_code") val reasonCode: String? = null,
    @SerialName("created_from_signals") val createdFromSignals: List<String> = emptyList(),
    @SerialName("decision_signals") val decisionSignals: List<String> = emptyList(),
    @SerialName("strategy_version") val strategyVersion: Int = 0,
)

@Serializable
data class StrategyQueueItemDto(
    val id: String,
    val category: String,
    val subject: String,
    val title: String,
    val impact: String,
    val effort: String,
    val destination: String,
    val reason: String,
    val status: String,
)

@Serializable
data class StrategyDeferredItemDto(
    val id: String,
    // Null when this is a strength rather than a gap. A skill already mastered produces no gap
    // at all, which is exactly why "you don't need more of this" is worth saying.
    val category: String? = null,
    val subject: String,
    val title: String,
    val reason: String,
    val instead: String? = null,
)

@Serializable
data class StrategyContradictionDto(
    val kind: String,
    // Null for contradictions about the profile as a whole rather than one skill.
    val subject: String? = null,
    @SerialName("left_statement") val leftStatement: String,
    @SerialName("left_signal_keys") val leftSignalKeys: List<String> = emptyList(),
    @SerialName("right_statement") val rightStatement: String,
    @SerialName("right_signal_keys") val rightSignalKeys: List<String> = emptyList(),
    val explanation: String,
    @SerialName("reclassify_to") val reclassifyTo: String? = null,
)

@Serializable
data class StrategySeriesDto(
    val subject: String,
    val verdict: String,
    @SerialName("observation_count") val observationCount: Int,
    @SerialName("is_sufficient") val isSufficient: Boolean,
    val mean: Float? = null,
    val variance: Float? = null,
    val trend: Float? = null,
    val latest: Float? = null,
    val reason: String,
)

@Serializable
data class StrategyChangedItemDto(
    val id: String,
    val category: String,
    val subject: String,
    val title: String,
)

@Serializable
data class StrategyChangeDto(
    val changed: Boolean,
    @SerialName("from_sequence") val fromSequence: Int? = null,
    @SerialName("to_sequence") val toSequence: Int? = null,
    @SerialName("from_created_at") val fromCreatedAt: String? = null,
    @SerialName("to_created_at") val toCreatedAt: String? = null,
    @SerialName("state_from") val stateFrom: String? = null,
    @SerialName("state_to") val stateTo: String? = null,
    @SerialName("state_changed") val stateChanged: Boolean = false,
    @SerialName("bottleneck_changed") val bottleneckChanged: Boolean = false,
    @SerialName("bottleneck_from") val bottleneckFrom: StrategyChangedItemDto? = null,
    @SerialName("bottleneck_to") val bottleneckTo: StrategyChangedItemDto? = null,
    val cleared: List<StrategyChangedItemDto> = emptyList(),
    val displaced: List<StrategyChangedItemDto> = emptyList(),
    val added: List<StrategyChangedItemDto> = emptyList(),
    @SerialName("still_open") val stillOpen: List<StrategyChangedItemDto> = emptyList(),
    @SerialName("spans_version_change") val spansVersionChange: Boolean = false,
    @SerialName("version_from") val versionFrom: Int? = null,
    @SerialName("version_to") val versionTo: Int? = null,
    val summary: List<String> = emptyList(),
)

@Serializable
data class StrategySnapshotDto(
    val id: Int,
    val sequence: Int,
    val state: String,
    @SerialName("strategy_version") val strategyVersion: Int,
    @SerialName("primary_bottleneck_id") val primaryBottleneckId: String? = null,
    @SerialName("primary_category") val primaryCategory: String? = null,
    @SerialName("primary_subject") val primarySubject: String? = null,
    @SerialName("created_at") val createdAt: String,
    // Each entry explains itself, so the timeline renders from one request rather than a
    // /compare call per row.
    @SerialName("change_summary") val changeSummary: List<String> = emptyList(),
)

@Serializable
data class StrategyHistoryDto(
    val snapshots: List<StrategySnapshotDto> = emptyList(),
)

@Serializable
data class StrategyComparisonDto(
    val snapshot: StrategySnapshotDto,
    @SerialName("compared_to") val comparedTo: StrategySnapshotDto? = null,
    val change: StrategyChangeDto,
)

@Serializable
data class CareerStrategyDto(
    @SerialName("is_premium") val isPremium: Boolean,
    // Names the sections a free student cannot see. An empty list here with an empty queue
    // means "nothing to do"; a populated list means "upgrade to see this". The screen must
    // read this rather than inferring from emptiness.
    @SerialName("locked_sections") val lockedSections: List<String> = emptyList(),
    val state: String,
    @SerialName("state_reason") val stateReason: String = "",
    @SerialName("strategy_version") val strategyVersion: Int = 0,
    @SerialName("has_role") val hasRole: Boolean = false,
    @SerialName("role_family_key") val roleFamilyKey: String? = null,
    @SerialName("display_role_name") val displayRoleName: String? = null,
    @SerialName("role_source") val roleSource: String = "none",
    @SerialName("role_is_estimate") val roleIsEstimate: Boolean = false,
    // Career Twin's EVIDENCE COVERAGE number, carried through so the two screens cannot
    // disagree. Renamed in Phase 2G with its two companions below -- all three describe the
    // same metric, and splitting their names would leave the screen reading "evidence
    // coverage" out of a field called readiness.
    @SerialName("evidence_coverage_percent") val evidenceCoveragePercent: Int? = null,
    @SerialName("evidence_coverage_is_estimate") val evidenceCoverageIsEstimate: Boolean = false,
    // Career Twin's own working, shown beside its number. Free: the /career-twin endpoint this
    // comes from is free, so gating it here would hide from one screen what the same student
    // reads on another.
    @SerialName("evidence_coverage_reasons") val evidenceCoverageReasons: List<String> = emptyList(),
    @SerialName("signal_count") val signalCount: Int = 0,
    val bottleneck: StrategyBottleneckDto? = null,
    val queue: List<StrategyQueueItemDto> = emptyList(),
    @SerialName("do_not_do_yet") val doNotDoYet: List<StrategyDeferredItemDto> = emptyList(),
    val contradictions: List<StrategyContradictionDto> = emptyList(),
    @SerialName("diminishing_returns") val diminishingReturns: List<StrategySeriesDto> = emptyList(),
    @SerialName("latest_change") val latestChange: StrategyChangeDto? = null,
    @SerialName("snapshot_id") val snapshotId: Int? = null,
    @SerialName("snapshot_sequence") val snapshotSequence: Int? = null,
    @SerialName("snapshot_created_at") val snapshotCreatedAt: String? = null,
    @SerialName("snapshot_is_new") val snapshotIsNew: Boolean = false,
)

@Serializable
data class JobBlockerDto(
    val kind: String,
    val subject: String,
    val title: String,
    val detail: String,
    val destination: String,
)

/**
 * The advert's own terms.
 *
 * Salary arrives as four fields rather than one formatted string, and that is deliberate: the
 * period travels with the amount, so no formatting code can turn 500/hour into 500/year. A null
 * period means the provider never stated one -- rendered as undisclosed, never defaulted.
 */
@Serializable
data class JobConditionsDto(
    val location: String? = null,
    @SerialName("work_mode") val workMode: String? = null,
    @SerialName("employment_type") val employmentType: String? = null,
    @SerialName("salary_min") val salaryMin: Int? = null,
    @SerialName("salary_max") val salaryMax: Int? = null,
    @SerialName("salary_currency") val salaryCurrency: String? = null,
    @SerialName("salary_period") val salaryPeriod: String? = null,
    // True when a provider estimated the figure rather than the employer publishing it.
    @SerialName("salary_is_predicted") val salaryIsPredicted: Boolean = false,
)

/** job_hunt/readiness's own "one thing most worth doing before applying", not a second opinion. */
@Serializable
data class ReadinessBiggestGapDto(
    val kind: String,
    // Null for a dimension-level gap, which has no single skill behind it.
    val skill: String? = null,
    val title: String,
    val detail: String,
    val action: String,
)

/**
 * What the student has already done about this advert.
 *
 * `appliedAt` is authoritative for "did they apply" -- a rejected application is still an
 * application. `status` is rendered as-is and never interpreted: the backend does not document
 * what values like "closed" mean, so neither does this client.
 */
@Serializable
data class JobApplicationStateDto(
    @SerialName("is_saved") val isSaved: Boolean = false,
    @SerialName("applied_at") val appliedAt: String? = null,
    val status: String? = null,
)

@Serializable
data class JobStrategyDto(
    @SerialName("listing_id") val listingId: Int,
    @SerialName("job_title") val jobTitle: String,
    val company: String? = null,
    // APPLY_NOW | FIX_BEFORE_APPLYING | NOT_A_CURRENT_PRIORITY | INSUFFICIENT_INFORMATION.
    // Never a probability, and never advice against applying.
    val decision: String,
    val headline: String,
    val reasons: List<String> = emptyList(),
    @SerialName("match_score") val matchScore: Float? = null,
    @SerialName("score_version") val scoreVersion: String? = null,
    // The match engine's own breakdown and its one-sentence explanation, computed server-side.
    // Never recomputed here -- a second opinion on the client is how two screens start
    // disagreeing about one job.
    @SerialName("component_scores") val componentScores: Map<String, Float> = emptyMap(),
    @SerialName("match_rationale") val matchRationale: String? = null,
    // What the provider gave the match engine nothing to judge on. Shown as unknown, never
    // counted against the student.
    @SerialName("unknown_factors") val unknownFactors: List<String> = emptyList(),
    val conditions: JobConditionsDto = JobConditionsDto(),
    // The match components that decided this, already written as sentences by the server.
    // Rendered in the order given; the client does not rank or reword them.
    @SerialName("deciding_factors") val decidingFactors: List<String> = emptyList(),
    val application: JobApplicationStateDto = JobApplicationStateDto(),
    @SerialName("readiness_percent") val readinessPercent: Int? = null,
    @SerialName("readiness_band") val readinessBand: String? = null,
    @SerialName("judged_dimensions") val judgedDimensions: Int = 0,
    @SerialName("unknown_dimensions") val unknownDimensions: List<String> = emptyList(),
    @SerialName("verified_skill_count") val verifiedSkillCount: Int = 0,
    @SerialName("missing_skills") val missingSkills: List<String> = emptyList(),
    @SerialName("unproven_skills") val unprovenSkills: List<String> = emptyList(),
    // Skills the backend could not judge at all. Deliberately separate from missingSkills:
    // "we don't know" is not "you don't have it", and the screen must not merge them.
    @SerialName("unclear_skills") val unclearSkills: List<String> = emptyList(),
    val blockers: List<JobBlockerDto> = emptyList(),
    @SerialName("prep_from_career_strategy") val prepFromCareerStrategy: List<String> = emptyList(),
    @SerialName("biggest_gap") val biggestGap: ReadinessBiggestGapDto? = null,
)

@Serializable
data class TargetStrategyDto(
    // Null for a Career Path target, which has no catalogue Role row behind it. Never a
    // sentinel id -- an absent id is absent, not -1.
    @SerialName("role_id") val roleId: Int? = null,
    @SerialName("display_role_name") val displayRoleName: String,
    @SerialName("is_current_target") val isCurrentTarget: Boolean = false,
    @SerialName("evidence_coverage_percent") val evidenceCoveragePercent: Int? = null,
    val classification: String? = null,
    @SerialName("estimated_hours_to_strengthen") val estimatedHoursToStrengthen: Float? = null,
    @SerialName("is_estimate") val isEstimate: Boolean = true,
    val state: String,
    @SerialName("bottleneck_title") val bottleneckTitle: String? = null,
    @SerialName("bottleneck_category") val bottleneckCategory: String? = null,
    @SerialName("top_action") val topAction: String? = null,
    @SerialName("top_action_destination") val topActionDestination: String? = null,
    val impact: String? = null,
    val effort: String? = null,
    @SerialName("unique_gaps") val uniqueGaps: List<String> = emptyList(),
    @SerialName("shared_gaps") val sharedGaps: List<String> = emptyList(),
)

@Serializable
data class TargetComparisonDto(
    @SerialName("is_premium") val isPremium: Boolean,
    val locked: Boolean = false,
    val targets: List<TargetStrategyDto> = emptyList(),
    @SerialName("shared_skills") val sharedSkills: List<String> = emptyList(),
    val tradeoffs: List<String> = emptyList(),
    // career_decision_service's own assumptions. An hours estimate must never be shown
    // without them, or it reads as a measurement.
    val assumptions: List<String> = emptyList(),
    @SerialName("max_targets") val maxTargets: Int = 3,
)

@Serializable
data class SetStrategyTargetsRequest(
    @SerialName("role_ids") val roleIds: List<Int>,
)
