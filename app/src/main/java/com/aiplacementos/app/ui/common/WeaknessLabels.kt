package com.aiplacementos.app.ui.common

import java.util.Locale

/**
 * Turns a raw weakness tag from the backend's weakness_service into something a student can
 * read.
 *
 * Those tags are namespaced machine keys -- "interview:clarity", "test:data_structures" -- and
 * were being rendered verbatim in student-facing copy ("You need more practice in
 * interview:clarity."). The backend already does exactly this cleanup for its own
 * next_best_action copy (next_best_action_service strips the "interview:" prefix and swaps
 * underscores for spaces); this is the client-side counterpart for the surfaces that read the
 * tags directly instead of going through that endpoint.
 *
 * Deliberately conservative: it strips a leading namespace, converts separators to spaces and
 * title-cases the result. It never maps a tag to a hand-written display name, so a tag the
 * backend adds later degrades to a readable phrase rather than to a wrong label or a blank.
 */
fun humanizeWeaknessTag(tag: String): String {
    val withoutNamespace = tag.substringAfter(':', tag).trim()
    val words = withoutNamespace
        .replace('_', ' ')
        .replace('-', ' ')
        .split(' ')
        .filter { it.isNotBlank() }
    if (words.isEmpty()) return tag.trim()
    return words.joinToString(" ") { word ->
        // Leave already-capitalised acronyms alone (SQL, HR, DSA) rather than mangling them
        // into "Sql"; only lowercase words get title-cased.
        if (word.length > 1 && word == word.uppercase(Locale.ROOT)) {
            word
        } else {
            word.replaceFirstChar { it.uppercaseChar() }
        }
    }
}
