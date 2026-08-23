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
