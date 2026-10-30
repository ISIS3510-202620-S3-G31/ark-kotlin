package com.moviles.ark.domain.repositories

import com.moviles.ark.domain.models.UserModel

interface AuthRepository {
    fun isLoggedIn(): Boolean
    suspend fun login(email: String, password: String): Result<Unit>
    //true si el perfil ya quedo en la nube; false si la cuenta se creo pero el perfil quedo en cola (la red se cayo
    //justo despues) y se sube solo cuando vuelva la conexion
    suspend fun registerUser(user: UserModel): Result<Boolean>
    fun logout()
    //el perfil se lee desde UserRepository (room primero)
}
