package com.aiplacementos.app.data.remote

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit

/**
 * The one JSON configuration every backend response is decoded with.
 *
 * Extracted from NetworkModule, where it was a private field on a class that needs an Android
 * Context to construct -- which meant a JVM unit test could not reach it and had to hand-copy
 * the settings instead. A copy is exactly the wrong thing here: these settings decide whether a
 * null in a response body is tolerated or throws, so a wire-contract test written against a
 * copy proves the copy is consistent with the DTO, not that the *app* is. Both real
 * null-contract bugs this configuration has hidden (ungraded Recruiter Attack results, and
 * Career Path readiness for a student with no resume) were invisible to tests for that reason.
 *
 * `ignoreUnknownKeys` lets the backend add a response field without breaking older clients.
 * Note what is deliberately NOT enabled: `coerceInputValues` would quietly turn a null into a
 * declared default, which is precisely how "we could not grade this" becomes "you scored zero".
 * A field the backend can legitimately send as null must be declared nullable in its DTO
 * instead, so the absence survives all the way to the screen.
 */
internal val ApiJson: Json = Json { ignoreUnknownKeys = true }

/**
 * The converter chain every response goes through, in order: [NullableBody] endpoints first,
 * then [ApiJson] for everything else -- including a NullableBody's non-null bodies, so a real
 * object decodes the same way wherever it arrives. Shared with the wire tests for the reason
 * above: a test that copies this chain proves the copy, not the app.
 */
internal fun Retrofit.Builder.addApiConverters(): Retrofit.Builder = this
    .addConverterFactory(NullableBodyConverterFactory)
    .addConverterFactory(ApiJson.asConverterFactory("application/json".toMediaType()))
