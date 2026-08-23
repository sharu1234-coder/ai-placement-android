package com.aiplacementos.app.data.repository

import com.aiplacementos.app.data.remote.AuthApi
import com.aiplacementos.app.data.remote.ForgotPasswordRequestDto
import com.aiplacementos.app.data.remote.LoginRequestDto
import com.aiplacementos.app.data.remote.ResetPasswordRequestDto
import com.aiplacementos.app.data.remote.SignupRequestDto
import com.aiplacementos.app.data.remote.UserDto
import com.aiplacementos.app.domain.repository.AuthRepository
import com.aiplacementos.app.network.TokenStore

class AuthRepositoryImpl(
    private val authApi: AuthApi,
    private val tokenStore: TokenStore,
) : AuthRepository {

    override suspend fun signup(email: String, password: String): UserDto =
        authApi.signup(SignupRequestDto(email = email, password = password))

    override suspend fun login(email: String, password: String) {
        val tokens = authApi.login(LoginRequestDto(email = email, password = password))
        tokenStore.save(tokens.accessToken, tokens.refreshToken)
    }

    override suspend fun isLoggedIn(): Boolean = tokenStore.currentAccessToken() != null

    override suspend fun logout() {
        tokenStore.clear()
    }

    override suspend fun forgotPassword(email: String): String =
        authApi.forgotPassword(ForgotPasswordRequestDto(email = email)).message

    override suspend fun resetPassword(email: String, code: String, newPassword: String): String =
        authApi.resetPassword(
            ResetPasswordRequestDto(email = email, code = code, newPassword = newPassword)
        ).message
}
