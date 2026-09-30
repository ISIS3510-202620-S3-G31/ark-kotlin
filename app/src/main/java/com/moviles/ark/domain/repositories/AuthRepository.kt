package com.moviles.ark.domain.repositories

import com.moviles.ark.domain.models.User

interface AuthRepository {
    fun getCurrentUser(): User?
    suspend fun login(email: String, password: String): Result<User>
    suspend fun register(name: String, age: Int, email: String, password: String): Result<User>
    fun logout()
}
