package com.moviles.ark.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.moviles.ark.ArkApplication
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

    //actualizar el estado reactivamente y calcular validacion inline en tiempo real
    fun onNameChange(newName: String) {
        val current = privateUiState.value
        val dummy = UserModel(name = newName, birthDate = current.birthDate, email = current.email, password = current.password)
        val error = when {
            newName.isNotBlank() && !dummy.isValidName() -> {
                if (newName.trim().length < UserModel.MIN_NAME_LENGTH) "Name must be at least 2 characters"
                else "Name cannot exceed 100 characters"
            }
            else -> null
        }
        val updated = current.copy(name = newName, nameError = error, errorMessage = null)
        privateUiState.value = updated.copy(isFormValid = checkFormValidity(updated))
    }

    fun onBirthDateChange(newBirthDate: String) {
        val current = privateUiState.value
        val dummy = UserModel(name = current.name, birthDate = newBirthDate, email = current.email, password = current.password)
        val error = when {
            newBirthDate.length >= 10 && !dummy.isValidBirthDate() -> "Must be a valid date and at least 13 years old (YYYY-MM-DD)"
            else -> null
        }
        val updated = current.copy(birthDate = newBirthDate, birthDateError = error, errorMessage = null)
        privateUiState.value = updated.copy(isFormValid = checkFormValidity(updated))
    }

    fun onEmailChange(newEmail: String) {
        val current = privateUiState.value
        val dummy = UserModel(name = current.name, birthDate = current.birthDate, email = newEmail, password = current.password)
        val error = when {
            newEmail.isNotBlank() && (newEmail.contains("@") && newEmail.contains(".")) && !dummy.isValidEmail() -> "Please enter a valid email address"
            newEmail.length >= 6 && !newEmail.contains("@") -> "Email must contain @"
            else -> null
        }
        val updated = current.copy(email = newEmail, emailError = error, errorMessage = null)
        privateUiState.value = updated.copy(isFormValid = checkFormValidity(updated))
    }

    fun onPasswordChange(newPassword: String) {
        val current = privateUiState.value
        val dummy = UserModel(name = current.name, birthDate = current.birthDate, email = current.email, password = newPassword)
        val passError = when {
            newPassword.isNotBlank() && !dummy.isValidPassword() -> "Must be at least 8 chars, 1 uppercase and 1 number"
            else -> null
        }
        val confirmError = when {
            current.confirmPassword.isNotBlank() && newPassword != current.confirmPassword -> "Passwords do not match"
            else -> null
        }
        val updated = current.copy(password = newPassword, passwordError = passError, confirmPasswordError = confirmError, errorMessage = null)
        privateUiState.value = updated.copy(isFormValid = checkFormValidity(updated))
    }

    fun onConfirmPasswordChange(newConfirmPassword: String) {
        val current = privateUiState.value
        val error = when {
            newConfirmPassword.isNotBlank() && newConfirmPassword != current.password -> "Passwords do not match"
            else -> null
        }
        val updated = current.copy(confirmPassword = newConfirmPassword, confirmPasswordError = error, errorMessage = null)
        privateUiState.value = updated.copy(isFormValid = checkFormValidity(updated))
    }

    //verifica con el modelo de dominio si todo el formulario cumple los requisitos
    private fun checkFormValidity(state: RegisterUiState): Boolean {
        if (state.name.isBlank() || state.birthDate.isBlank() || state.email.isBlank() || state.password.isBlank() || state.confirmPassword.isBlank()) {
            return false
        }
        if (state.password != state.confirmPassword) {
            return false
        }
        val user = UserModel(
            name = state.name,
            birthDate = state.birthDate,
            email = state.email,
            password = state.password
        )
        return user.isValidName() && user.isValidBirthDate() && user.isValidEmail() && user.isValidPassword()
    }

    //hacer el registro cuando el usuario presiona el boton en la screen
    //las reglas de negocio pertenecen al modelo UserModel
    fun register(onSuccess: () -> Unit) {
        val state = privateUiState.value

        //revisar que no haya ningun campo vacio
        if (state.name.isBlank() || state.birthDate.isBlank() || state.email.isBlank() || state.password.isBlank() || state.confirmPassword.isBlank()) {
            privateUiState.value = state.copy(errorMessage = "Please fill in all fields")
            return
        }

        //revisar coincidencia de claves
        if (state.password != state.confirmPassword) {
            privateUiState.value = state.copy(errorMessage = "Passwords do not match", confirmPasswordError = "Passwords do not match")
            return
        }

        val userModel = UserModel(
            name = state.name.trim(),
            birthDate = state.birthDate.trim(),
            email = state.email.trim(),
            password = state.password
        )

        //validacion con el modelo de dominio
        if (!userModel.isValidName()) {
            privateUiState.value = state.copy(nameError = "Name must be at least 2 characters", errorMessage = "Please check highlighted errors")
            return
        }

        if (!userModel.isValidBirthDate()) {
            privateUiState.value = state.copy(birthDateError = "Must be a valid date and at least 13 years old (YYYY-MM-DD)", errorMessage = "Please check highlighted errors")
            return
        }

        if (!userModel.isValidEmail()) {
            privateUiState.value = state.copy(emailError = "Please enter a valid email address", errorMessage = "Please check highlighted errors")
            return
        }

        if (!userModel.isValidPassword()) {
            privateUiState.value = state.copy(passwordError = "Must be at least 8 chars, 1 uppercase and 1 number", errorMessage = "Please check highlighted errors")
            return
        }

        //activar spinner de carga
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
                val message = when (error) {
                    is FirebaseAuthUserCollisionException -> "This email is already registered"
                    is FirebaseAuthInvalidCredentialsException -> "Please enter a valid email address"
                    is FirebaseNetworkException -> "No internet connection, try again"
                    else -> "Could not create the account, try again"
                }
                privateUiState.value = privateUiState.value.copy(errorMessage = message)
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
