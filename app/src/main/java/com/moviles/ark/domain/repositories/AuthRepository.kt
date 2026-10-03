package com.moviles.ark.domain.repositories

import com.moviles.ark.domain.models.UserModel

interface AuthRepository {
    fun isLoggedIn(): Boolean
    suspend fun login(email: String, password: String): Result<Unit>
    suspend fun registerUser(user: UserModel): Result<Unit>
    fun logout()
}
