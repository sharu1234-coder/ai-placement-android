package com.aiplacementos.app.data.local

import com.aiplacementos.app.data.remote.CareerPathDto

/**
 * A Career Path generation this device sent and has not yet seen the end of.
 *
 * F12-02: POST /career-path/generate runs for one to two minutes on the server (measured 76-132s
 * on the local model), and the connection does not survive a student switching apps in the
 * meantime -- Android cut it within seconds of the app leaving the foreground on every live
 * reproduction. The server finishes anyway: it commits, and the requested role becomes the
 * student's current path. The screen used to report that as "Couldn't reach the server" and
 * invite a retry, and the retry overlapped the still-running original (F12-03).
 *
 * This record is what lets the app say "still finishing" instead, and recognise the result when
 * it can read it again. It holds exactly what that takes: no token, no resume, no prompt and no
 * response body.
 */
data class PendingCareerPathGeneration(
    /** This attempt on this device, so a late finish never clears a newer attempt's record. */
    val operationId: String,
    /** Exactly the custom_role this app sent -- the server stores it on the path it builds. */
    val requestedRole: String,
    /** Wall-clock send time. Persisted because the deadline has to survive process death. */
    val startedAtMillis: Long,
    /** What "new" is measured against. */
    val before: CurrentPathBefore,
)

/** GET /career-path/current as it stood immediately before the request was sent. */
sealed interface CurrentPathBefore {
    /** The student had no current path. */
    data object None : CurrentPathBefore

    /** The path that was current. select_role() moves updated_at on a row it re-selects, and a
     * row it creates has a new id -- so its result can never look like this, unchanged. */
    data class Path(val id: Int, val updatedAt: String) : CurrentPathBefore

    /** The pre-send read failed for a reason other than connectivity. The request still goes
     * out -- a server that cannot report the current path may well still build a new one -- but
     * "new" cannot be proven, so completion falls back to the requested role alone. */
    data object Unknown : CurrentPathBefore
}

/**
 * Whether [current] -- the body of GET /career-path/current -- is this generation's result.
 *
 * select_role() does all of its writing in one transaction that commits only when the request
 * finishes: the row for the requested role becomes primary with custom_role set to the text that
 * was sent (SelectCareerRoleRequest strips it; so does this), and its updated_at moves. Until that
 * commit /current keeps answering with whatever was current before. So "a path exists" is not
 * success: a student who had Data Analyst and asked for Data Scientist still reads Data Analyst
 * for the whole two minutes, and one re-selecting the role that is already current reads that
 * same role throughout -- only updated_at tells the finished request from the untouched row.
 */
fun PendingCareerPathGeneration.isCompletedBy(current: CareerPathDto?): Boolean {
    if (current == null || !current.isPrimary) return false
    if (current.customRole?.trim() != requestedRole.trim()) return false
    return when (val prior = before) {
        CurrentPathBefore.None, CurrentPathBefore.Unknown -> true
        is CurrentPathBefore.Path -> current.id != prior.id || current.updatedAt != prior.updatedAt
    }
}
