package com.aiplacementos.app.ui.common

import java.io.IOException

/** True for connectivity failures (no signal, DNS failure, timed-out connect) -- covers
 * UnknownHostException/ConnectException/SocketTimeoutException, all IOException subtypes.
 * False for a reached-but-unhappy server (HttpException) or any other failure, so callers
 * can show "No internet connection" only when that's actually what happened. */
fun Throwable.isNetworkError(): Boolean = this is IOException
