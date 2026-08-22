package com.aiplacementos.app.network

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

/** Attaches the stored access token; never attaches to auth endpoints since those calls precede login. */
class AuthInterceptor(private val tokenStore: TokenStore) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.url.encodedPath.startsWith("/auth/")) {
            return chain.proceed(request)
        }

        val token = runBlocking { tokenStore.currentAccessToken() }
        val authorized = if (token != null) {
            request.newBuilder().addHeader("Authorization", "Bearer $token").build()
        } else {
            request
        }
        return chain.proceed(authorized)
    }
}
