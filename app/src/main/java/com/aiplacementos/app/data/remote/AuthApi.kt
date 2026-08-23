package com.aiplacementos.app.data.remote

import retrofit2.http.Body
import retrofit2.http.POST

interface AuthApi {
    @POST("auth/signup")
    suspend fun signup(@Body request: SignupRequestDto): UserDto

    @POST("auth/login")
    suspend fun login(@Body request: LoginRequestDto): TokenResponseDto

    @POST("auth/forgot-password")
    suspend fun forgotPassword(@Body request: ForgotPasswordRequestDto): MessageResponseDto

    @POST("auth/reset-password")
    suspend fun resetPassword(@Body request: ResetPasswordRequestDto): MessageResponseDto
}
