package com.moviles.ark.data.remote.apis

import com.google.firebase.auth.FirebaseAuth
import com.moviles.ark.domain.adapters.AuthServiceAdapter
import kotlinx.coroutines.tasks.await

//adapta firebase auth a AuthServiceAdapter (#136); es la unica clase del login que conoce FirebaseAuth
class FirebaseAuthAdapterImpl(
    private val auth: FirebaseAuth
) : AuthServiceAdapter {

    // Firebase saves the session on the device, so this works offline
    override fun currentUserId(): String? = auth.currentUser?.uid

    override suspend fun signIn(email: String, password: String): Result<String> = runCatching {
        val result = auth.signInWithEmailAndPassword(email, password).await()
        result.user?.uid ?: error("Firebase did not return the signed in user")
    }

    override suspend fun createUser(email: String, password: String): Result<String> = runCatching {
        val result = auth.createUserWithEmailAndPassword(email, password).await()
        result.user?.uid ?: error("Firebase did not return the new user")
    }

    override fun signOut() = auth.signOut()
}
