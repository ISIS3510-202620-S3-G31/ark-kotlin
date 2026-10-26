package com.moviles.ark.data.repositories

import com.google.firebase.firestore.FirebaseFirestore
import com.moviles.ark.data.remote.dtos.toDto
import com.moviles.ark.domain.models.UserModel
import com.moviles.ark.data.remote.CrashlyticsHelper
import com.moviles.ark.domain.adapters.AuthServiceAdapter
import com.moviles.ark.domain.repositories.AuthRepository
import kotlinx.coroutines.tasks.await

//la autenticacion pasa solo por el adapter; firestore sigue aqui para guardar el perfil
class AuthRepositoryImpl(
    private val authServiceAdapter: AuthServiceAdapter,
    private val firestore: FirebaseFirestore
) : AuthRepository {

    // the session is saved on the device, so this works offline
    override fun isLoggedIn(): Boolean = authServiceAdapter.isLoggedIn()

    override suspend fun login(email: String, password: String): Result<Unit> = runCatching {
        authServiceAdapter.signIn(email.trim(), password).getOrThrow()
        Unit
    }.onFailure { error ->
        CrashlyticsHelper.logNonFatal(
            componentName = "AuthRepository",
            action = "login",
            throwable = error,
            extraKeys = mapOf(
                "email_domain" to email.substringAfter("@", "unknown"),
                "is_empty_password" to password.isEmpty()
            )
        )
    }

    override suspend fun registerUser(user: UserModel): Result<Unit> = runCatching {
        val uid = authServiceAdapter.createUser(user.email.trim(), user.password).getOrThrow()
        // the password stays in Firebase Auth, Firestore only gets the profile
        firestore.collection("users").document(uid)
            .set(user.toDto())
            .await()
        Unit
    }.onFailure { error ->
        CrashlyticsHelper.logNonFatal(
            componentName = "AuthRepository",
            action = "registerUser",
            throwable = error,
            extraKeys = mapOf(
                "user_birth_date" to user.birthDate,
                "email_domain" to user.email.substringAfter("@", "unknown")
            )
        )
    }

    override fun logout() = authServiceAdapter.signOut()
}
