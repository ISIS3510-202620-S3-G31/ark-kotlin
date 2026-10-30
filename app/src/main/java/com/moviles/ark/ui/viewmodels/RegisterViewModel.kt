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
import com.moviles.ark.domain.models.RegistrationErrors
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
    //al escribir se borra el mensaje general, los errores de cada campo se recalculan
    fun onNameChange(newName: String) {
        val name = UserModel.limitName(newName)
        privateUiState.value = privateUiState.value.copy(name = name, nameError = UserModel.nameErrorWhileTyping(name), errorMessage = null)
    }

    fun onBirthDateChange(newBirthDate: String) {
        privateUiState.value = privateUiState.value.copy(
            birthDate = newBirthDate,
            birthDateError = UserModel.birthDateErrorWhileTyping(newBirthDate),
            errorMessage = null
        )
    }

    fun onEmailChange(newEmail: String) {
        privateUiState.value = privateUiState.value.copy(email = newEmail, emailError = UserModel.emailErrorWhileTyping(newEmail), errorMessage = null)
    }

    fun onPasswordChange(newPassword: String) {
        val current = privateUiState.value
        privateUiState.value = current.copy(
            password = newPassword,
            passwordError = UserModel.passwordErrorWhileTyping(newPassword),
            confirmPasswordError = UserModel.confirmPasswordErrorWhileTyping(newPassword, current.confirmPassword),
            errorMessage = null
        )
    }

    fun onConfirmPasswordChange(newConfirmPassword: String) {
        val current = privateUiState.value
        privateUiState.value = current.copy(
            confirmPassword = newConfirmPassword,
            confirmPasswordError = UserModel.confirmPasswordErrorWhileTyping(current.password, newConfirmPassword),
            errorMessage = null
        )
    }

    private fun userOf(state: RegisterUiState) = UserModel(
        name = state.name.trim(),
        birthDate = state.birthDate.trim(),
        email = state.email.trim(),
        password = state.password
    )

    //pone cada error junto a su campo y debajo un resumen que nombra los campos a corregir
    private fun showErrors(errors: RegistrationErrors) {
        privateUiState.value = privateUiState.value.copy(
            nameError = errors.name,
            birthDateError = errors.birthDate,
            emailError = errors.email,
            passwordError = errors.password,
            confirmPasswordError = errors.confirmPassword,
            errorMessage = errors.summary()
        )
    }

    //hacer el registro cuando el usuario presiona el boton en la screen
    //las reglas de negocio pertenecen al modelo UserModel; el toque nunca se ignora:
    //si algun campo no cumple se muestra por que y la cuenta solo se crea cuando todos cumplen
    fun register(onSuccess: () -> Unit) {
        val state = privateUiState.value
        if (state.isLoading) return

        val userModel = userOf(state)
        val errors = userModel.registrationErrors(state.confirmPassword)
        if (errors.hasErrors()) {
            showErrors(errors)
            return
        }

        //activamos la carga para mostrar el spinner en la pantalla
        privateUiState.value = state.copy(isLoading = true, errorMessage = null)

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
                //los del correo van en su campo; sin internet se avisa en rojo debajo de los campos
                when (error) {
                    is NetworkUnavailableException ->
                        privateUiState.value = privateUiState.value.copy(errorMessage = UserModel.noInternetError(error.isAirplaneMode))
                    is EmailAlreadyRegisteredException -> showErrors(RegistrationErrors(email = UserModel.EMAIL_ALREADY_REGISTERED_ERROR))
                    is InvalidCredentialsException -> showErrors(RegistrationErrors(email = UserModel.INVALID_EMAIL_ERROR))
                    else -> privateUiState.value = privateUiState.value.copy(errorMessage = "Could not create the account, try again")
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
