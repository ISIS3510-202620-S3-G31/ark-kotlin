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
import com.moviles.ark.domain.models.User
import com.moviles.ark.domain.repositories.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RegisterViewModel(private val authRepository: AuthRepository): ViewModel() { //hereda de view model, como hay que llamar al constructor super(), se pone ()
    private val privateUiState = MutableStateFlow(RegisterUiState()) //solo el viewmodel lo puede editar
    //uiState es un contenedor reactivo, guarda solo 1 register ui state a la vez
    //aunque solo guardo un objeto, necesito el contenedor para que este pendiente si algo cambia y le avise a la vista
    val uiState: StateFlow<RegisterUiState> = privateUiState.asStateFlow() //misma direccion de memoria que el private pero solo de lectura

    //actualizar el register ui state cuando el usuario escribe algo (con tan solo una letra ya se lanza)
    //dejamos error message en todos por si ya salio un error y el usuario lo esta corrigiendo, para que no siga viendo que hay error
    fun onNameChange(newName: String): Unit {
        //abrir contenedor, sacar el objeto contenido y hace copia cambiando solo el atributo que le decimos
        //la copia es necesaria porque los campos en register ui state son val (inmutables)
        //ademas, al crear un objeto nuevo, el stateflow lo nota y avisa a la vista
        privateUiState.value = privateUiState.value.copy(name = newName, errorMessage = null)
    }

    fun onAgeChange(newAge: String): Unit {
        privateUiState.value = privateUiState.value.copy(age = newAge, errorMessage = null)
    }

    fun onEmailChange(newEmail: String): Unit {
        privateUiState.value = privateUiState.value.copy(email = newEmail, errorMessage = null)
    }

    fun onPasswordChange(newPassword: String): Unit {
        privateUiState.value = privateUiState.value.copy(password = newPassword, errorMessage = null)
    }

    fun onConfirmPasswordChange(newConfirmPassword: String): Unit {
        privateUiState.value = privateUiState.value.copy(confirmPassword = newConfirmPassword, errorMessage = null)
    }

    //hacer el registro cuando el usuario presiona el boton en la screen
    //aqui solo hacemos validaciones de presentacion de la pantalla y le delegamos las de negocio al modelo User
    //onSuccess se ejecuta cuando firebase termina de crear la cuenta
    fun register(onSuccess: () -> Unit) {
        val state = privateUiState.value

        //reglas de presentacion
        //revisar que no haya ningun campo vacio en la pantalla
        if (state.name.isBlank() || state.age.isBlank() || state.email.isBlank() || state.password.isBlank() || state.confirmPassword.isBlank()) {
            privateUiState.value = state.copy(errorMessage = "Please fill in all fields")
            return //detener la funcion, no seguir revisando nada mas, pues porque ya encontro un error
        }

        //revisar que ambas contrasenas escritas coincidan
        if (state.password != state.confirmPassword) {
            privateUiState.value = state.copy(errorMessage = "Passwords do not match")
            return //detener la funcion, no seguir revisando nada mas, pues porque ya encontro un error
        }

        //revisar que el texto de la edad se pueda convertir a numero
        val ageNumber = state.age.toIntOrNull()
        if (ageNumber == null) {
            privateUiState.value = state.copy(errorMessage = "Please enter a valid age")
            return
        }

        //creamos el objeto del modelo con los datos listos
        val userModel = User(state.name, ageNumber, state.email, state.password)

        //reglas de negocio
        //le preguntamos al modelo User si cumple con reglas de negocio
        if (!userModel.isValidName()) {
            privateUiState.value = state.copy(errorMessage = "Name is too short")
            return
        }

        if (!userModel.isValidAge()) {
            privateUiState.value = state.copy(errorMessage = "Please enter a valid age")
            return
        }

        if (!userModel.isValidEmail()) {
            privateUiState.value = state.copy(errorMessage = "Please enter a valid email address")
            return
        }

        if (!userModel.isValidPassword()) {
            privateUiState.value = state.copy(errorMessage = "Password must be at least 8 characters and contain 1 uppercase and 1 number")
            return
        }

        //activamos la carga para mostrar el spinner en la pantalla
        privateUiState.value = state.copy(isLoading = true, errorMessage = null)

        //llamamos a authRepository para guardar el userModel en firebase
        //se hace en una corrutina porque firebase se demora en responder
        viewModelScope.launch {
            val result = authRepository.registerUser(userModel)

            //apagamos la carga una vez completado
            privateUiState.value = privateUiState.value.copy(isLoading = false)

            result.onSuccess { onSuccess() }
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