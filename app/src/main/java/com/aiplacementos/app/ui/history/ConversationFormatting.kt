package com.aiplacementos.app.ui.history

import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Turning the server's timestamps and vocabularies into something readable, without inventing
 * anything that was not sent.
 */

/**
 * Parse a conversation timestamp, whatever shape it arrives in.
 *
 * The history endpoints serialise from SQLAlchemy `DateTime` columns declared **without** a
 * timezone, so a value comes back as `2026-10-17T21:30:00` -- no offset, no `Z`.
 * `OffsetDateTime.parse` throws on exactly that, which would crash a history screen on a
 * perfectly valid response. The offset-bearing form is tried first because the server will send
 * one if those columns ever become timezone-aware, and the naive form is then read as UTC, which
 * is what the server wrote.
 *
 * Returns `null` on anything else. A caller must render nothing rather than a guessed date: a
 * wrong timestamp on a student's own record is worse than no timestamp.
 */
fun parseConversationTimestamp(raw: String?): OffsetDateTime? {
    if (raw.isNullOrBlank()) return null
    return runCatching { OffsetDateTime.parse(raw) }
        .recoverCatching { LocalDateTime.parse(raw).atOffset(ZoneOffset.UTC) }
        .getOrNull()
}

/** `17 Oct 2026, 21:30` in the reader's own zone, or `null` when the value was unusable. */
fun formatConversationTimestamp(raw: String?): String? =
    parseConversationTimestamp(raw)
        ?.atZoneSameInstant(ZoneId.systemDefault())
        ?.format(DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.getDefault()))

/** The product's name for a `kind`. Unknown values are passed through rather than guessed at. */
fun conversationKindLabel(kind: String): String = when (kind) {
    "career_coach" -> "Voice Career Coach"
    "mock_interview" -> "Mock Interview"
    else -> kind
}

/**
 * The product's name for a `status`.
 *
 * `interrupted` is deliberately not softened to something reassuring: a conversation that lost its
 * connection is not a finished one, and the student's own record should say so.
 */
fun conversationStatusLabel(status: String): String = when (status) {
    "active" -> "Still open"
    "completed" -> "Completed"
    "interrupted" -> "Interrupted"
    else -> status
}

/** Who said it. */
fun turnRoleLabel(role: String): String = when (role) {
    "user" -> "You"
    "assistant" -> "Coach"
    else -> role
}

/**
 * A title for a conversation that has none.
 *
 * The server sends `title` as nullable and nothing currently writes one, so the list would
 * otherwise show a column of blanks. This composes a label out of values the server *did* send --
 * the kind and the date -- rather than summarising a transcript the list endpoint never fetched.
 */
fun conversationDisplayTitle(kind: String, title: String?, createdAt: String?): String {
    val trimmed = title?.trim()
    if (!trimmed.isNullOrEmpty()) return trimmed
    val on = formatConversationTimestamp(createdAt)
    return if (on == null) conversationKindLabel(kind) else conversationKindLabel(kind) + " - " + on
}
