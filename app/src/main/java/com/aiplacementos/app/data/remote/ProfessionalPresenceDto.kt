package com.aiplacementos.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Professional Presence wire types.
 *
 * There is no token field anywhere in this file, and that absence mirrors the server schemas
 * deliberately: the backend's response models have nowhere to put a credential, and neither do
 * these, so a future server change that started returning one could not land here silently.
 */

/** One provider's connection state, as the server reports it. */
@Serializable
data class ConnectionStatusDto(
    val provider: String,
    val connected: Boolean,
    val username: String? = null,
    val status: String? = null,
    @SerialName("sync_status") val syncStatus: String? = null,
    @SerialName("last_synced_at") val lastSyncedAt: String? = null,
    /** A category from a fixed server-side vocabulary, never a provider's own message. */
    @SerialName("last_error_kind") val lastErrorKind: String? = null,
    @SerialName("connected_at") val connectedAt: String? = null,
)

/** What the app needs to open the system browser, and nothing else. */
@Serializable
data class AuthorizationStartDto(
    @SerialName("authorization_url") val authorizationUrl: String,
    @SerialName("expires_in_seconds") val expiresInSeconds: Int,
)

/** Counts describing what one sync changed. Never the repositories themselves. */
@Serializable
data class SyncResultDto(
    val provider: String,
    @SerialName("evidence_added") val evidenceAdded: Int,
    @SerialName("evidence_removed") val evidenceRemoved: Int,
    @SerialName("repositories_seen") val repositoriesSeen: Int,
    @SerialName("snapshot_written") val snapshotWritten: Boolean,
    /** False when the server's repository listing hit its page cap, so counts are a floor. */
    val complete: Boolean,
)

/**
 * One audit finding.
 *
 * [evidence] is assembled server-side from stored data and is what makes a recommendation
 * traceable; [whyItMatters] is the generated half and is absent rather than invented when the
 * model produced nothing usable for this finding.
 */
@Serializable
data class AuditFindingDto(
    val code: String,
    /** strength | gap | opportunity */
    val kind: String,
    val subject: String? = null,
    val evidence: String,
    @SerialName("why_it_matters") val whyItMatters: String? = null,
    @SerialName("recommended_action") val recommendedAction: String,
    @SerialName("is_template") val isTemplate: Boolean,
)

/**
 * The whole Professional Presence payload for one provider.
 *
 * Connection state travels with the findings rather than beside them, which is what lets the
 * screen tell "GitHub could not be reached" apart from "you have no evidence" -- [syncStatus]
 * and [lastErrorKind] are the difference, and rendering an empty findings list without them
 * would state something false about the student.
 */
@Serializable
data class PresenceAuditDto(
    val provider: String,
    val connected: Boolean,
    val username: String? = null,
    val status: String? = null,
    @SerialName("sync_status") val syncStatus: String? = null,
    @SerialName("last_synced_at") val lastSyncedAt: String? = null,
    @SerialName("last_error_kind") val lastErrorKind: String? = null,
    /** never | fresh | aging | stale -- decided server-side so every client agrees. */
    val freshness: String,
    @SerialName("snapshot_captured_at") val snapshotCapturedAt: String? = null,
    @SerialName("listing_complete") val listingComplete: Boolean = true,
    val findings: List<AuditFindingDto> = emptyList(),
    @SerialName("narrative_prompt_version") val narrativePromptVersion: String? = null,
)

/** One past state of the account, for answering "what changed?". */
@Serializable
data class PresenceHistoryItemDto(
    val sequence: Int,
    @SerialName("captured_at") val capturedAt: String,
    @SerialName("public_repositories") val publicRepositories: Int,
    @SerialName("described_repositories") val describedRepositories: Int,
    val languages: List<String> = emptyList(),
)
