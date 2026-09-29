package com.aiplacementos.app.data.remote

import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import okhttp3.ResponseBody
import okio.BufferedSource
import okio.ByteString.Companion.encodeUtf8
import retrofit2.Converter
import retrofit2.Retrofit

/**
 * A response the backend may legitimately answer with the JSON literal `null`, meaning "this
 * doesn't exist yet" -- no Career Path chosen, no roadmap built, no resume uploaded. [value] is
 * null exactly then, so the screen reaches its designed empty state instead of an error.
 *
 * Why a wrapper rather than declaring the endpoint `Dto?`: that `?` never reaches Retrofit. It
 * reads a suspend function's return type through Java reflection, where Kotlin nullability
 * does not exist, so the converter is asked for a non-null Dto and rejects the literal -- and a
 * converter that returned null anyway would be refused by Retrofit's own non-null check (both
 * pinned in TopLevelNullWireTest). The wrapper is never null itself, so neither step objects.
 *
 * Opt-in by type: only an endpoint declared as NullableBody gets this. Everywhere else a `null`
 * body is still the broken response it would be there.
 */
class NullableBody<T : Any>(val value: T?)

/**
 * Decodes [NullableBody] responses. Only the literal `null` (JSON whitespace around it allowed)
 * becomes an empty NullableBody. Anything else goes to the next converter in the chain -- the
 * same one every other response uses -- so a real object decodes exactly as before, and
 * malformed JSON fails exactly as before. HTTP errors and network failures never reach a
 * converter at all, so they are untouched by construction.
 */
internal object NullableBodyConverterFactory : Converter.Factory() {
    override fun responseBodyConverter(
        type: Type,
        annotations: Array<out Annotation>,
        retrofit: Retrofit,
    ): Converter<ResponseBody, *>? {
        if (getRawType(type) != NullableBody::class.java) return null
        // Only a stripped generic signature leaves this raw -- see NullableBody in proguard-rules.pro.
        check(type is ParameterizedType) { "NullableBody lost its type argument: $type" }
        val dto = retrofit.nextResponseBodyConverter<Any>(this, getParameterUpperBound(0, type), annotations)
        return Converter<ResponseBody, NullableBody<Any>> { body ->
            if (body.isJsonNullLiteral()) {
                body.close()
                NullableBody(null)
            } else {
                NullableBody(checkNotNull(dto.convert(body)) { "Decoded a non-null body to null" })
            }
        }
    }
}

private val NULL_LITERAL = "null".encodeUtf8()

/** Reads through peek(), so when the answer is no, the next converter gets the body untouched. */
private fun ResponseBody.isJsonNullLiteral(): Boolean {
    val peek = source().peek()
    peek.skipJsonWhitespace()
    if (!peek.rangeEquals(0, NULL_LITERAL)) return false
    peek.skip(NULL_LITERAL.size.toLong())
    peek.skipJsonWhitespace()
    return peek.exhausted()
}

private fun BufferedSource.skipJsonWhitespace() {
    while (request(1)) {
        when (buffer[0L].toInt().toChar()) {
            ' ', '\t', '\n', '\r' -> skip(1)
            else -> return
        }
    }
}
