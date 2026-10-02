package com.moviles.ark.data.repositories

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.moviles.ark.domain.models.User
import com.moviles.ark.domain.repositories.AuthRepository
import kotlinx.coroutines.tasks.await

class AuthRepositoryImpl(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) : AuthRepository {

    // Firebase saves the session on the device, so this works offline
    override fun isLoggedIn(): Boolean = auth.currentUser != null

    override suspend fun login(email: String, password: String): Result<Unit> = runCatching {
        auth.signInWithEmailAndPassword(email.trim(), password).await()
        Unit
    }

    override suspend fun registerUser(user: User): Result<Unit> = runCatching {
        val result = auth.createUserWithEmailAndPassword(user.email.trim(), user.password).await()
        // the password stays in Firebase Auth, Firestore only gets the profile
        firestore.collection("users").document(result.user!!.uid)
            .set(mapOf("name" to user.name.trim(), "age" to user.age, "email" to user.email.trim()))
            .await()
        Unit
    }

    override fun logout() = auth.signOut()
}
