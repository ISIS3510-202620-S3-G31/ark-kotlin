package com.moviles.ark.data.repositories

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.moviles.ark.data.local.sensors.NetworkConnectivityObserver
import com.moviles.ark.data.remote.CrashlyticsHelper
import com.moviles.ark.data.remote.dtos.toDto
import com.moviles.ark.domain.adapters.AuthServiceAdapter
import com.moviles.ark.domain.exceptions.EmailAlreadyRegisteredException
import com.moviles.ark.domain.exceptions.InvalidCredentialsException
import com.moviles.ark.domain.exceptions.NetworkUnavailableException
import com.moviles.ark.domain.models.UserModel
import com.moviles.ark.domain.repositories.AuthRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

//la autenticacion pasa solo por el adapter; firestore sigue aqui para guardar el perfil
//la autenticacion requiere conectividad activa con el servidor
class AuthRepositoryImpl(
    private val authServiceAdapter: AuthServiceAdapter,
    private val firestore: FirebaseFirestore,
    private val networkConnectivityObserver: NetworkConnectivityObserver? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    //al entrar se sube lo que la cuenta dejo pendiente sin red (aunque haya cerrado sesion antes de que subiera)
    private val onSignedIn: () -> Unit = {}
) : AuthRepository {

    // the session is saved on the device, so this works offline
    override fun isLoggedIn(): Boolean = authServiceAdapter.isLoggedIn()

    override suspend fun login(email: String, password: String): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            checkActiveNetworkConnection()
            try {
                authServiceAdapter.signIn(email.trim(), password).getOrThrow()
                onSignedIn()
                Unit
            } catch (e: Throwable) {
                handleNetworkException(e)
            }
        }.onFailure { error ->
            CrashlyticsHelper.logNonFatal(
                componentName = "AuthRepository",
                action = "login",
                throwable = error,
                extraKeys = mapOf(
                    "email_domain" to email.substringAfter("@", "unknown"),
                    "is_empty_password" to password.isEmpty(),
                    "is_network_error" to (error is NetworkUnavailableException || error is FirebaseNetworkException)
                )
            )
        }
    }

    override suspend fun registerUser(user: UserModel): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            checkActiveNetworkConnection()
            try {
                val uid = authServiceAdapter.createUser(user.email.trim(), user.password).getOrThrow()
                // the password stays in Firebase Auth, Firestore only gets the profile
                firestore.collection("users").document(uid)
                    .set(user.toDto())
                    .await()
                Unit
            } catch (e: Throwable) {
                handleNetworkException(e)
            }
        }.onFailure { error ->
            CrashlyticsHelper.logNonFatal(
                componentName = "AuthRepository",
                action = "registerUser",
                throwable = error,
                extraKeys = mapOf(
                    "user_birth_date" to user.birthDate,
                    "email_domain" to user.email.substringAfter("@", "unknown"),
                    "is_network_error" to (error is NetworkUnavailableException || error is FirebaseNetworkException)
                )
            )
        }
    }

    private fun checkActiveNetworkConnection() {
        val observer = networkConnectivityObserver ?: return
        val isAirplane = observer.isAirplaneModeOn()
        val isConnected = observer.isConnected()

        if (isAirplane || !isConnected) {
            val message = if (isAirplane) {
                "Airplane mode is enabled. Please disable airplane mode and check your internet connection."
            } else {
                "Active server connectivity is required. Please check your internet connection."
            }
            throw NetworkUnavailableException(message = message, isAirplaneMode = isAirplane)
        }
    }

    private fun handleNetworkException(error: Throwable): Nothing {
        if (error is NetworkUnavailableException) {
            throw error
        }
        if (isNetworkError(error)) {
            val isAirplane = networkConnectivityObserver?.isAirplaneModeOn() == true
            val message = if (isAirplane) {
                "Airplane mode is enabled. Please disable airplane mode and check your internet connection."
            } else {
                "Network connection failed. Active server connectivity is required to verify credentials."
            }
            throw NetworkUnavailableException(message = message, isAirplaneMode = isAirplane, cause = error)
        }
        //los errores de firebase se traducen a excepciones del dominio para que la ui no dependa de firebase
        throw when (error) {
            is FirebaseAuthInvalidCredentialsException, is FirebaseAuthInvalidUserException -> InvalidCredentialsException(error)
            is FirebaseAuthUserCollisionException -> EmailAlreadyRegisteredException(error)
            else -> error
        }
    }

    private fun isNetworkError(error: Throwable): Boolean {
        if (error is NetworkUnavailableException ||
            error is FirebaseNetworkException ||
            error is UnknownHostException ||
            error is SocketTimeoutException ||
            error is ConnectException ||
            (error is FirebaseFirestoreException &&
                    error.code == FirebaseFirestoreException.Code.UNAVAILABLE) ||
            (error is IOException && error.message?.contains("network", ignoreCase = true) == true)
        ) {
            return true
        }
        return false
    }

    override fun logout() = authServiceAdapter.signOut()
}
