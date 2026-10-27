package com.moviles.ark.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.moviles.ark.ArkApplication
import com.moviles.ark.data.local.AvatarPreferences
import com.moviles.ark.data.local.ProfilePreferences
import kotlinx.coroutines.Dispatchers
import com.moviles.ark.domain.models.CheckInStreak
import com.moviles.ark.domain.models.UserProfile
import com.moviles.ark.domain.repositories.AuthRepository
import com.moviles.ark.domain.repositories.MoodRepository
import com.moviles.ark.domain.repositories.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

//now se puede cambiar en las pruebas para fijar "hoy"; en la app es la hora del telefono
class ProfileViewModel(
    private val authRepository: AuthRepository,
    //perfil guardado en room (#119): se muestra de una vez, aunque no haya internet
    private val userRepository: UserRepository,
    //check-ins del usuario para la racha (#96)
    private val moodRepository: MoodRepository? = null,
    private val now: () -> Long = { System.currentTimeMillis() }
) : ViewModel() {
    //privado: solo el viewmodel lo modifica
    private val _uiState = MutableStateFlow(ProfileUiState(isLoading = true))

    //publico: la pantalla solo lo escucha
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    private val streak = CheckInStreak(now = now)

    //racha recien calculada con los check-ins; si existe manda sobre la guardada en room
    private var freshStreak: Int? = null

    //room se escucha apenas se crea el viewmodel; si la pantalla rota no se vuelve a pedir
    //el refresco con firestore lo pide la pantalla cada vez que se muestra (loadProfile)
    init {
        loadCachedPreferences()
        observeProfile()
        observeAvatar()
    }

    //carga inmediata de datos escalares guardados en sharedpreferences (offline-first)
    private fun loadCachedPreferences() {
        val cached = ProfilePreferences.getScalarProfile()
        if (cached.name.isNotBlank() || cached.email.isNotBlank()) {
            val name = cached.name.ifBlank { cached.email.substringBefore("@") }
            _uiState.update {
                it.copy(
                    name = name,
                    initials = initialsOf(name),
                    email = cached.email,
                    age = cached.age,
                    selectedAvatarId = cached.avatarId,
                    isLoading = false
                )
            }
        }
    }

    //room avisa cada vez que cambia la fila: al refrescar desde firestore, al editar o al cambiar el avatar
    private fun observeProfile() {
        viewModelScope.launch {
            userRepository.observeProfile().collect { profile ->
                if (profile != null) showProfile(profile)
            }
        }
    }

    private fun observeAvatar() {
        viewModelScope.launch {
            AvatarPreferences.currentAvatarId.collect { avatarId ->
                _uiState.update { it.copy(selectedAvatarId = avatarId) }
            }
        }
    }

    fun onAvatarSelected(avatarId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            ProfilePreferences.saveAvatarId(avatarId)
        }
    }

    //trae lo ultimo de firestore (y sube un cambio que haya quedado pendiente sin internet)
    //si falla y no hay nada en el telefono se muestra el error
    fun loadProfile() {
        _uiState.update { it.copy(isLoading = it.email.isEmpty(), errorMessage = null) }
        viewModelScope.launch {
            userRepository.refreshProfile().onFailure {
                _uiState.update { state ->
                    if (state.email.isNotEmpty()) state.copy(isLoading = false)
                    else state.copy(isLoading = false, errorMessage = "Could not load your profile, try again")
                }
            }
        }
    }

    //la pantalla lo pide cada vez que se muestra, asi la racha se ve al dia despues de un check-in en el home (#96)
    fun loadStreak() {
        val repository = moodRepository ?: return
        viewModelScope.launch {
            val from = now() - CheckInStreak.LOOKBACK_DAYS * DAY_MILLIS
            //si no se puede leer (por ejemplo sin internet y sin copia local) se queda la racha guardada en room
            repository.getCheckInTimestampsSince(from).onSuccess { timestamps ->
                //le preguntamos al modelo cuantos dias seguidos lleva y se guarda para verla sin internet (#119)
                val days = streak.daysInARow(timestamps)
                freshStreak = days
                userRepository.saveStreak(days, timestamps.maxOrNull())
                _uiState.update { it.copy(streakDays = days) }
            }
        }
    }

    fun onEditProfileClick() {
        val state = _uiState.value
        _uiState.update {
            it.copy(
                isEditing = true,
                editName = state.name,
                editAge = if (state.age > 0) state.age.toString() else "",
                editError = null
            )
        }
    }

    fun onEditNameChange(name: String) {
        _uiState.update { it.copy(editName = name, editError = null) }
    }

    //solo numeros y maximo 3 cifras
    fun onEditAgeChange(age: String) {
        _uiState.update { it.copy(editAge = age.filter { c -> c.isDigit() }.take(3), editError = null) }
    }

    fun onEditDismiss() {
        _uiState.update { it.copy(isEditing = false, editError = null) }
    }

    fun onSaveProfileClick() {
        val state = _uiState.value
        //las reglas viven en el modelo
        val edited = UserProfile(
            name = state.editName.trim(),
            email = state.email,
            memberSince = 0L,
            age = state.editAge.toIntOrNull() ?: 0
        )
        val error = when {
            !edited.isValidName() -> "Your name needs ${UserProfile.MIN_NAME_LENGTH} to ${UserProfile.MAX_NAME_LENGTH} letters"
            !edited.isValidAge() -> "Enter an age between 1 and 120"
            else -> null
        }
        if (error != null) {
            _uiState.update { it.copy(editError = error) }
            return
        }
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            userRepository.updateProfile(edited.name, edited.age)
                .onSuccess {
                    launch(Dispatchers.IO) {
                        ProfilePreferences.saveProfile(
                            name = edited.name,
                            email = state.email,
                            age = edited.age,
                            avatarId = state.selectedAvatarId
                        )
                    }
                    _uiState.update { it.copy(isSaving = false, isEditing = false, message = "Profile saved") }
                }
                .onFailure { _uiState.update { it.copy(isSaving = false, editError = "Could not save your profile, try again") } }
        }
    }

    //uri que devuelve el selector de fotos
    fun onAvatarPicked(uri: String) {
        viewModelScope.launch {
            userRepository.updateAvatar(uri).onFailure {
                _uiState.update { it.copy(message = "Could not use that photo, try another one") }
            }
        }
    }

    fun onMessageShown() {
        _uiState.update { it.copy(message = null) }
    }

    fun onSignOutClick() {
        authRepository.logout()
        viewModelScope.launch(Dispatchers.IO) {
            ProfilePreferences.clear()
        }
        _uiState.update { it.copy(isSignedOut = true) }
    }

    private fun showProfile(profile: UserProfile) {
        //si no hay nombre (sin internet la primera vez), se usa la parte del correo antes de la @
        val name = profile.name.ifBlank { profile.email.substringBefore("@") }
        _uiState.update {
            it.copy(
                name = name,
                initials = initialsOf(name),
                email = profile.email,
                age = profile.getCalculatedAge(),
                avatarPath = profile.avatarPath,
                memberSince = formatMemberSince(profile.memberSince),
                daysWithArk = daysSince(profile.memberSince),
                //la racha guardada solo vale si el ultimo check-in fue hoy o ayer
                streakDays = freshStreak ?: streak.stillAlive(profile.streakDays, profile.lastCheckInAt),
                isLoading = false,
                errorMessage = null
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            ProfilePreferences.saveProfile(
                name = name,
                email = profile.email,
                age = profile.getCalculatedAge(),
                avatarId = _uiState.value.selectedAvatarId
            )
        }
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

    //factory para que el viewmodel reciba los repositorios del AppContainer
    companion object {
        private const val DAY_MILLIS = 24 * 60 * 60 * 1000L

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as ArkApplication
                ProfileViewModel(
                    authRepository = app.container.authRepository,
                    userRepository = app.container.userRepository,
                    moodRepository = app.container.moodRepository
                )
            }
        }
    }
}

