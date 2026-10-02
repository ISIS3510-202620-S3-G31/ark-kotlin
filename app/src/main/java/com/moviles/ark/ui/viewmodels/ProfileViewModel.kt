package com.moviles.ark.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.moviles.ark.ArkApplication
import com.moviles.ark.domain.repositories.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

//now se puede cambiar en las pruebas para fijar "hoy"; en la app es la hora del telefono
class ProfileViewModel(
    private val authRepository: AuthRepository,
    private val now: () -> Long = { System.currentTimeMillis() }
) : ViewModel() {
    //privado: solo el viewmodel lo modifica
    private val _uiState = MutableStateFlow(ProfileUiState())

    //publico: la pantalla solo lo escucha
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    //el perfil se carga apenas se crea el viewmodel; si la pantalla rota no se vuelve a pedir
    init {
        loadProfile()
    }

    fun loadProfile() {
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            val result = authRepository.getProfile()
            result.onSuccess { profile ->
                //si no hay nombre (sin internet la primera vez), se usa la parte del correo antes de la @
                val name = profile.name.ifBlank { profile.email.substringBefore("@") }
                _uiState.value = _uiState.value.copy(
                    name = name,
                    initials = initialsOf(name),
                    email = profile.email,
                    memberSince = formatMemberSince(profile.memberSince),
                    daysWithArk = daysSince(profile.memberSince),
                    isLoading = false
                )
            }
            result.onFailure {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Could not load your profile, try again"
                )
            }
        }
    }

    fun onSignOutClick() {
        authRepository.logout()
        _uiState.value = _uiState.value.copy(isSignedOut = true)
    }

    //milisegundos -> "October 2026"; 0 significa que no se conoce la fecha
    private fun formatMemberSince(millis: Long): String {
        if (millis <= 0L) return ""
        return SimpleDateFormat("MMMM yyyy", Locale.ENGLISH).format(Date(millis))
    }

    //primera letra de las dos primeras palabras: "David Forero" -> "DF", "lau3" -> "L"
    private fun initialsOf(name: String): String {
        return name.trim().split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
            .take(2)
            .joinToString("") { it.first().uppercase() }
    }

    //cuenta dias de calendario en la hora local: registrarse hoy da 1, manana da 2
    //se suma el desfase de la zona horaria para que el dia cambie a medianoche de colombia y no de londres
    private fun daysSince(millis: Long): Int {
        if (millis <= 0L) return 0
        val zone = TimeZone.getDefault()
        val today = (now() + zone.getOffset(now())) / DAY_MILLIS
        val firstDay = (millis + zone.getOffset(millis)) / DAY_MILLIS
        return (today - firstDay + 1).toInt().coerceAtLeast(1)
    }

    //factory para que el viewmodel reciba el authRepository del AppContainer
    companion object {
        private const val DAY_MILLIS = 24 * 60 * 60 * 1000L

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as ArkApplication
                ProfileViewModel(app.container.authRepository)
            }
        }
    }
}
