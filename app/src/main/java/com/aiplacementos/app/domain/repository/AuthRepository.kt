package com.aiplacementos.app.domain.repository

import com.aiplacementos.app.data.remote.UserDto

interface AuthRepository {
    suspend fun signup(email: String, password: String): UserDto
    suspend fun login(email: String, password: String)
    suspend fun isLoggedIn(): Boolean
    suspend fun logout()
}
