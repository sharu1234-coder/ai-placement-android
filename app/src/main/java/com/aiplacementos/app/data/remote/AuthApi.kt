package com.aiplacementos.app.data.remote

import retrofit2.http.Body
import retrofit2.http.POST

interface AuthApi {
    @POST("auth/signup")
    suspend fun signup(@Body request: SignupRequestDto): UserDto

    @POST("auth/login")
    suspend fun login(@Body request: LoginRequestDto): TokenResponseDto
}
