package com.aiplacementos.app.ui.common

import java.io.IOException
import java.net.SocketTimeoutException
import kotlin.coroutines.cancellation.CancellationException
import retrofit2.HttpException

/** True for connectivity failures (no signal, DNS failure, timed-out connect) -- covers
 * UnknownHostException/ConnectException/SocketTimeoutException, all IOException subtypes.
 * False for a reached-but-unhappy server (HttpException) or any other failure, so callers
 * can show "No internet connection" only when that's actually what happened. */
fun Throwable.isNetworkError(): Boolean = this is IOException

/** True specifically for a connect/read timeout, distinct from "no connection at all" --
 * SocketTimeoutException is also an IOException (isNetworkError() is true for it too), but a
 * request that reached the network and simply took too long deserves its own message ("the
 * server is taking too long") rather than "check your connection". */
fun Throwable.isTimeoutError(): Boolean = this is SocketTimeoutException

/**
 * What actually failed, as one of the few things this app can tell apart.
 *
 * The distinction that matters is where the answer comes from. If the server replied, its
 * status code is the authority and the transport is irrelevant -- a 500 arrived over a working
 * connection, so it is never a connectivity problem. Only when nothing replied at all does the
 * exception type get to decide, and even then a timeout is not the same as a dead network: one
 * reached the server and waited, the other never got there.
 *
 * [UNKNOWN] is deliberately a real outcome rather than a dumping ground that guesses. Claiming
 * the network is down when we do not know is the failure this taxonomy exists to prevent.
 */
enum class ErrorKind {
    /** Nothing reached the server: no route, DNS failure, connection refused. */
    CONNECTIVITY,

    /** The request got out and ran out of time waiting. */
    TIMEOUT,

    /** The caller went away. Never an error, and never shown. */
    CANCELLED,

    /** 401. TokenAuthenticator already tried a refresh, so this is a genuinely expired session. */
    AUTH,

    /** 403 -- authenticated, but not allowed. */
    FORBIDDEN,

    /** 404 -- no such thing, or it is not this student's. */
    NOT_FOUND,

    /** 409 -- the state moved under the request. */
    CONFLICT,

    /** 422 -- the request itself was not acceptable. */
    VALIDATION,

    /** 429 -- too many requests. */
    RATE_LIMITED,

    /** 5xx -- reached us, and we failed. */
    SERVER,

    /** Genuinely unclassified. Says so instead of blaming the network. */
    UNKNOWN,
}

/**
 * Whether trying the identical request again could plausibly succeed.
 *
 * Retry is about whether the *cause* can pass, not about whether the user is allowed to tap a
 * button. A 403 or a 422 will be refused identically forever, so offering a retry there turns a
 * settled answer into a loop; a 401 needs a new session rather than another attempt at the same
 * one. Cancellation is never retried, because nobody asked for the work any more.
 *
 * UNKNOWN is retryable: the copy for it already says "please try again", and a failure we could
 * not identify is not a failure we can rule out as transient.
 */
val ErrorKind.isRetryable: Boolean
    get() = when (this) {
        ErrorKind.CONNECTIVITY, ErrorKind.TIMEOUT, ErrorKind.RATE_LIMITED,
        ErrorKind.SERVER, ErrorKind.UNKNOWN -> true
        ErrorKind.CANCELLED, ErrorKind.AUTH, ErrorKind.FORBIDDEN,
        ErrorKind.NOT_FOUND, ErrorKind.CONFLICT, ErrorKind.VALIDATION -> false
    }

/**
 * The single place a failure is turned into a category.
 *
 * Order is the whole design. Cancellation is checked before anything else because a cancelled
 * coroutine is not a failure at all. An [HttpException] is checked before the transport checks
 * because a reply -- any reply -- proves the network worked, and its status is a better answer
 * than any guess made from the exception class. Only after that do the IO checks run, and
 * timeout is tested before connectivity because SocketTimeoutException is itself an IOException
 * and would otherwise be reported as "no connection" when the connection plainly worked.
 */
fun Throwable.classify(): ErrorKind = when {
    this is CancellationException -> ErrorKind.CANCELLED
    this is HttpException -> when (code()) {
        401 -> ErrorKind.AUTH
        403 -> ErrorKind.FORBIDDEN
        404 -> ErrorKind.NOT_FOUND
        409 -> ErrorKind.CONFLICT
        422 -> ErrorKind.VALIDATION
        429 -> ErrorKind.RATE_LIMITED
        in 500..599 -> ErrorKind.SERVER
        else -> ErrorKind.UNKNOWN
    }
    isTimeoutError() -> ErrorKind.TIMEOUT
    isNetworkError() -> ErrorKind.CONNECTIVITY
    else -> ErrorKind.UNKNOWN
}

/**
 * A short, user-facing message for a caught Throwable, chosen from [classify]'s answer rather
 * than from the exception's own text.
 *
 * `genericAction` completes the sentence for the cases where the app has nothing more specific
 * to say -- pass the thing the student was trying to do, lower case and without punctuation
 * ("load your profile", "save your changes").
 *
 * **This function throws on cancellation, on purpose.** A cancelled coroutine has no user to
 * show anything to, and swallowing CancellationException inside a `catch (e: Exception)` is the
 * bug that leaves a screen stuck on a dead request's error. Rethrowing here fixes every caller
 * at once: a `catch (e: Exception)` block that ends in this call stays cancellation-correct
 * without each of them needing its own rethrow. Callers that genuinely need a category without
 * that behaviour should use [classify] instead.
 *
 * The returned text never contains the exception's own message. `HttpException.message` is
 * "HTTP 500 Internal Server Error" and an OkHttp IOException's is a host and port -- server
 * detail and internal addresses, written for a log and not for a student.
 */
fun Throwable.toUserMessage(genericAction: String): String {
    if (this is CancellationException) throw this
    return when (classify()) {
        ErrorKind.AUTH -> "Your session has expired. Please sign in again."
        ErrorKind.FORBIDDEN -> "You don't have permission to do that."
        ErrorKind.NOT_FOUND -> "We couldn't find that. It may have been removed."
        ErrorKind.CONFLICT -> "That changed while you were working. Please refresh and try again."
        ErrorKind.VALIDATION -> "Some of that information isn't valid. Please check it and try again."
        ErrorKind.RATE_LIMITED -> "Too many requests. Please wait a moment and try again."
        ErrorKind.SERVER -> "Something went wrong on our server. Please try again."
        ErrorKind.TIMEOUT -> "The server is taking too long to respond. Please try again."
        ErrorKind.CONNECTIVITY -> "Couldn't reach the server. Check your connection and try again."
        // A status the taxonomy has no opinion about (402 and 418 both reach here) still tells
        // the student which request failed, and shows the code so support can act on it.
        ErrorKind.UNKNOWN -> if (this is HttpException) {
            "Couldn't $genericAction (${code()})."
        } else {
            "Couldn't $genericAction. Please try again."
        }
        // Unreachable: cancellation returned above. Present so this `when` stays exhaustive
        // over ErrorKind, which is what makes a new category a compile error here.
        ErrorKind.CANCELLED -> throw this
    }
}
