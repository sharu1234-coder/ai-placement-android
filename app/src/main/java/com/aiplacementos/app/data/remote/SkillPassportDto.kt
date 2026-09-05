package com.aiplacementos.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class EvidenceItemDto(
    val source: String, // assessment/practice/project/interview/resume
    @SerialName("source_id") val sourceId: Int? = null,
    val label: String,
    val score: Float? = null,
    @SerialName("detected_at") val detectedAt: String? = null,
)

@Serializable
data class SkillPassportItemDto(
    val skill: String,
    val level: String? = null, // null when state == "claimed" -- no evidence to base a level on
    // Only ever one of five values (0/30/45/70/90), one per state -- use it for bar lengths
    // and ordering, never as displayed text. Show confidenceBand instead.
    @SerialName("confidence_percent") val confidencePercent: Int,
    // none/low/moderate/high/very_high -- the honest resolution of the value above.
    // Defaulted so an older backend that doesn't send it still deserializes.
    @SerialName("confidence_band") val confidenceBand: String = "none",
    val state: String, // claimed/supported/strong/verified/stale
    // A real verification date -- self-asserted evidence (resume keyword, typed-in project)
    // never sets this, so it can be null even when evidence exists.
    @SerialName("last_verified_at") val lastVerifiedAt: String? = null,
    val evidence: List<EvidenceItemDto> = emptyList(),
    @SerialName("next_action_hint") val nextActionHint: String,
)
