package com.moviles.ark.data.repositories

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.userProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.moviles.ark.domain.models.User
import com.moviles.ark.domain.repositories.AuthRepository
import kotlinx.coroutines.tasks.await

class AuthRepositoryImpl(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) : AuthRepository {

    // Firebase saves the session on the device, so this works offline
    override fun getCurrentUser(): User? = auth.currentUser?.toUser()

    override suspend fun login(email: String, password: String): Result<User> = runCatching {
        val result = auth.signInWithEmailAndPassword(email.trim(), password).await()
        result.user!!.toUser()
    }

    override suspend fun register(name: String, age: Int, email: String, password: String): Result<User> = runCatching {
        val result = auth.createUserWithEmailAndPassword(email.trim(), password).await()
        val user = result.user!!
        user.updateProfile(userProfileChangeRequest { displayName = name.trim() }).await()
        // Firebase Auth has no age field, so the profile data is saved in Firestore
        firestore.collection("users").document(user.uid)
            .set(mapOf("name" to name.trim(), "age" to age, "email" to email.trim()))
            .await()
        user.toUser().copy(name = name.trim())
    }

    override fun logout() = auth.signOut()

    private fun FirebaseUser.toUser() = User(uid, displayName.orEmpty(), email.orEmpty())
}
