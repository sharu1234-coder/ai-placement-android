package com.aiplacementos.app.ui.common

import java.io.IOException
import java.net.SocketTimeoutException
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

/** A short, user-facing message for a caught Throwable, following this app's established
 * network/auth/server/timeout split (see JobMatchViewModel/PlacementPathViewModel) --
 * shared here so every screen's blanket `catch (e: Exception)` classifies consistently
 * instead of each one reinventing (or omitting) the same distinction. TokenAuthenticator
 * already retries a 401 with a refreshed token before it can reach this point, so an
 * HttpException(401) here means the refresh itself failed -- a genuinely expired session,
 * not a transient auth hiccup. */
fun Throwable.toUserMessage(genericAction: String): String = when {
    this is HttpException && code() == 401 -> "Your session has expired. Please sign in again."
    this is HttpException && code() >= 500 -> "Something went wrong on our server. Please try again."
    this is HttpException -> "Couldn't $genericAction (${code()})."
    isTimeoutError() -> "The server is taking too long to respond. Please try again."
    isNetworkError() -> "Couldn't reach the server. Check your connection and try again."
    else -> "Couldn't $genericAction. Please try again."
}
