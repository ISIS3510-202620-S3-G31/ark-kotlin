package com.moviles.ark.domain.adapters

//adapter del servicio de autenticacion (#136): AuthRepositoryImpl solo habla con esta interfaz y no sabe que hay firebase detras
//no expone tipos de firebase, asi en las pruebas se cambia por un fake en memoria
//si algo falla, el Result trae la excepcion original para que los viewmodels sigan mostrando el mismo mensaje
interface AuthServiceAdapter {
    //uid del usuario con sesion, o null si no hay sesion
    fun currentUserId(): String?
    fun isLoggedIn(): Boolean = currentUserId() != null
    //devuelve el uid del usuario que inicio sesion
    suspend fun signIn(email: String, password: String): Result<String>
    //crea la cuenta (queda con la sesion iniciada) y devuelve su uid
    suspend fun createUser(email: String, password: String): Result<String>
    fun signOut()
}
