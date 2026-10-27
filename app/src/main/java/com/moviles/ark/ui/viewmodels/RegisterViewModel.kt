package com.moviles.ark.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.moviles.ark.ArkApplication
import com.moviles.ark.domain.exceptions.EmailAlreadyRegisteredException
import com.moviles.ark.domain.exceptions.InvalidCredentialsException
import com.moviles.ark.domain.exceptions.NetworkUnavailableException
import com.moviles.ark.domain.models.UserModel
import com.moviles.ark.domain.repositories.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RegisterViewModel(private val authRepository: AuthRepository) : ViewModel() {
    //solo el viewmodel puede modificar el estado privado
    private val privateUiState = MutableStateFlow(RegisterUiState())
    //contenedor reactivo expuesto a la vista de solo lectura
    val uiState: StateFlow<RegisterUiState> = privateUiState.asStateFlow()

    //cada campo le pregunta al modelo UserModel si cumple; el viewmodel solo guarda el resultado en el estado
    fun onNameChange(newName: String) {
        updateForm(privateUiState.value.copy(name = newName, nameError = UserModel.nameErrorWhileTyping(newName)))
    }

    fun onBirthDateChange(newBirthDate: String) {
        updateForm(privateUiState.value.copy(birthDate = newBirthDate, birthDateError = UserModel.birthDateErrorWhileTyping(newBirthDate)))
    }

    fun onEmailChange(newEmail: String) {
        updateForm(privateUiState.value.copy(email = newEmail, emailError = UserModel.emailErrorWhileTyping(newEmail)))
    }

    fun onPasswordChange(newPassword: String) {
        val current = privateUiState.value
        updateForm(
            current.copy(
                password = newPassword,
                passwordError = UserModel.passwordErrorWhileTyping(newPassword),
                confirmPasswordError = UserModel.confirmPasswordErrorWhileTyping(newPassword, current.confirmPassword)
            )
        )
    }

    fun onConfirmPasswordChange(newConfirmPassword: String) {
        val current = privateUiState.value
        updateForm(
            current.copy(
                confirmPassword = newConfirmPassword,
                confirmPasswordError = UserModel.confirmPasswordErrorWhileTyping(current.password, newConfirmPassword)
            )
        )
    }

    private fun updateForm(updated: RegisterUiState) {
        privateUiState.value = updated.copy(
            errorMessage = null,
            isFormValid = userOf(updated).registrationError(updated.confirmPassword) == null
        )
    }

    private fun userOf(state: RegisterUiState) = UserModel(
        name = state.name.trim(),
        birthDate = state.birthDate.trim(),
        email = state.email.trim(),
        password = state.password
    )

    fun dismissNoInternetDialog() {
        privateUiState.value = privateUiState.value.copy(showNoInternetDialog = false)
    }

    //hacer el registro cuando el usuario presiona el boton en la screen
    //las reglas de negocio pertenecen al modelo UserModel
    fun register(onSuccess: () -> Unit) {
        val state = privateUiState.value

        //el modelo dice el primer error del formulario y el de cada campo
        val userModel = userOf(state)
        val formError = userModel.registrationError(state.confirmPassword)
        if (formError != null) {
            privateUiState.value = state.copy(
                errorMessage = formError,
                nameError = if (formError == UserModel.CHECK_FIELDS_ERROR) UserModel.nameError(userModel.name) else state.nameError,
                birthDateError = if (formError == UserModel.CHECK_FIELDS_ERROR) UserModel.birthDateError(userModel.birthDate) else state.birthDateError,
                emailError = if (formError == UserModel.CHECK_FIELDS_ERROR) UserModel.emailError(userModel.email) else state.emailError,
                passwordError = if (formError == UserModel.CHECK_FIELDS_ERROR) UserModel.passwordError(userModel.password) else state.passwordError,
                confirmPasswordError = if (formError == UserModel.PASSWORDS_DO_NOT_MATCH_ERROR) formError else state.confirmPasswordError
            )
            return
        }

        //activamos la carga para mostrar el spinner en la pantalla
        privateUiState.value = state.copy(isLoading = true, errorMessage = null, showNoInternetDialog = false)

        viewModelScope.launch {
            val result = authRepository.registerUser(userModel)
            privateUiState.value = privateUiState.value.copy(isLoading = false)

            result.onSuccess {
                privateUiState.value = privateUiState.value.copy(
                    successMessage = "Account created successfully! Welcome to Ark"
                )
                onSuccess()
            }
            result.onFailure { error ->
                //el repositorio ya traduce los errores de firebase a excepciones del dominio
                if (error is NetworkUnavailableException) {
                    privateUiState.value = privateUiState.value.copy(
                        showNoInternetDialog = true,
                        isAirplaneModeError = error.isAirplaneMode,
                        errorMessage = null
                    )
                } else {
                    val message = when (error) {
                        is EmailAlreadyRegisteredException -> "This email is already registered"
                        is InvalidCredentialsException -> UserModel.INVALID_EMAIL_ERROR
                        else -> "Could not create the account, try again"
                    }
                    privateUiState.value = privateUiState.value.copy(errorMessage = message)
                }
            }
        }
    }

    //factory para que el viewmodel reciba el authRepository del AppContainer
    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as ArkApplication
                RegisterViewModel(app.container.authRepository)
            }
        }
    }
}
