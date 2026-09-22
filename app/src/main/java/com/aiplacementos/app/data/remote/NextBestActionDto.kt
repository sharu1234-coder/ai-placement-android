package com.aiplacementos.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The section name the backend puts in [RecommendedActionDto.lockedSections] when a student's
 * plan has more in it than their tier can see. Matched rather than assumed, so a future locked
 * section cannot silently turn into an upgrade prompt for the wrong thing. */
const val LOCKED_SECTION_ADDITIONAL_ACTIONS = "additional_actions"

@Serializable
data class RecommendedActionDto(
    @SerialName("has_recommendation") val hasRecommendation: Boolean,
    val tier: String? = null,
    val title: String,
    @SerialName("action_type") val actionType: String, // learn/practice/project/resume/roadmap/maintenance
    @SerialName("estimated_minutes") val estimatedMinutes: Int? = null,
    val reasons: List<String> = emptyList(),
    val skill: String? = null,
    // low/moderate/high -- how far this conclusion can be leaned on, and deliberately not a
    // restatement of priority: the top action can be the least certain one, because a role gap
    // is inferred from evidence we do not have. Defaulted so an older backend still parses.
    val confidence: String? = null,
    // A key from the backend's shared destination vocabulary; NavGraph's own table is what turns
    // it into a route. Null when there is nowhere meaningful to send the student yet.
    val destination: String? = null,
    // The rest of the ranked plan. Premium only -- a free student gets the top action above and
    // an empty list here, never a partial list that looks like the whole plan.
    @SerialName("additional_actions") val additionalActions: List<RecommendedActionDto> = emptyList(),
    @SerialName("locked_sections") val lockedSections: List<String> = emptyList(),
)
