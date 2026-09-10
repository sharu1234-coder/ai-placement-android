package com.aiplacementos.app.fakes

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.HttpException
import retrofit2.Response

/** Builds a real retrofit2.HttpException with the given status code, the same way a fake
 * failing API call would produce one -- lets tests exercise a ViewModel's `e.code()`
 * branches without a real network call. */
fun httpException(code: Int): HttpException {
    val body = "{}".toResponseBody("application/json".toMediaType())
    return HttpException(Response.error<Any>(code, body))
}

/**
 * An HttpException carrying a real error body.
 *
 * The plain [httpException] above sends "{}", which is right for the many cases where only the
 * status code matters. It is not right for the ones where the client parses the body -- the
 * 402 free-limit response, in particular, whose `code` and `message` drive which screen the
 * student sees. Testing those against an empty body would only ever exercise the fallback.
 */
fun httpExceptionWithBody(code: Int, json: String): HttpException {
    val body = json.toResponseBody("application/json".toMediaType())
    return HttpException(Response.error<Any>(code, body))
}
