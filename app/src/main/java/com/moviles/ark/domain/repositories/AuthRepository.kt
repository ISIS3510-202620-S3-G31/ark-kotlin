package com.moviles.ark.domain.repositories

import com.moviles.ark.domain.models.User
import com.moviles.ark.domain.models.UserProfile

interface AuthRepository {
    fun isLoggedIn(): Boolean
    suspend fun login(email: String, password: String): Result<Unit>
    suspend fun registerUser(user: User): Result<Unit>
    fun logout()
    suspend fun getProfile(): Result<UserProfile>
}
