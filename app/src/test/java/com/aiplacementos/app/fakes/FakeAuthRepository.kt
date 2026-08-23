package com.aiplacementos.app.fakes

import com.aiplacementos.app.data.remote.UserDto
import com.aiplacementos.app.domain.repository.AuthRepository

class FakeAuthRepository : AuthRepository {
    var signupResult: Result<UserDto> =
        Result.success(UserDto(id = 1, email = "test@example.com", isActive = true, createdAt = "2026-01-01T00:00:00"))
    var loginResult: Result<Unit> = Result.success(Unit)
    var loggedIn: Boolean = false
    var forgotPasswordResult: Result<String> =
        Result.success("If that email is registered, a reset code has been sent.")
    var resetPasswordResult: Result<String> = Result.success("Password has been reset.")

    var logoutCalled = false
    var lastSignupArgs: Pair<String, String>? = null
    var lastLoginArgs: Pair<String, String>? = null
    var lastForgotPasswordEmail: String? = null
    var lastResetPasswordArgs: Triple<String, String, String>? = null

    override suspend fun signup(email: String, password: String): UserDto {
        lastSignupArgs = email to password
        return signupResult.getOrThrow()
    }

    override suspend fun login(email: String, password: String) {
        lastLoginArgs = email to password
        loginResult.getOrThrow()
    }

    override suspend fun isLoggedIn(): Boolean = loggedIn

    override suspend fun logout() {
        logoutCalled = true
    }

    override suspend fun forgotPassword(email: String): String {
        lastForgotPasswordEmail = email
        return forgotPasswordResult.getOrThrow()
    }

    override suspend fun resetPassword(email: String, code: String, newPassword: String): String {
        lastResetPasswordArgs = Triple(email, code, newPassword)
        return resetPasswordResult.getOrThrow()
    }
}
