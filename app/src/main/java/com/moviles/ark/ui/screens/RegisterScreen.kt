package com.moviles.ark.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.moviles.ark.R
import com.moviles.ark.domain.models.User
import com.moviles.ark.domain.repositories.AuthRepository
import com.moviles.ark.ui.theme.AppTheme
import com.moviles.ark.ui.viewmodels.RegisterViewModel

@Composable
fun RegisterScreen(viewModel: RegisterViewModel = viewModel(factory = RegisterViewModel.Factory), onNavigateToLogin: () -> Unit = {}, onRegisterSuccess: () -> Unit = {}) {
    //leemos el estado de la pantalla desde el viewmodel usando .value
    val uiState = viewModel.uiState.collectAsStateWithLifecycle()

    //colores personalizados para las cajitas de texto en blanco limpio
    val fieldColors = TextFieldDefaults.colors(focusedContainerColor = Color.White, unfocusedContainerColor = Color.White)

    //variables locales de la pantalla para controlar si se ve o no la clave al presionar el ojito
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }

    //surface para pintar el color de fondo oficial del tema
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        //columna principal con scroll vertical por si la pantalla del celular es pequena
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            //logo o mascota de la aplicacion
            Image(painter = painterResource(R.drawable.ic_mascot_log), contentDescription = "App Mascot", modifier = Modifier.size(125.dp))
            Spacer(modifier = Modifier.height(16.dp))

            //titulo con la fuente Sorean (headlineLarge) y subtitulo
            Text("Create Account", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onBackground)
            Text("Sign up to start your wellbeing journey", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
            Spacer(modifier = Modifier.height(20.dp))

            //campo name
            TextField(value = uiState.value.name, onValueChange = { viewModel.onNameChange(it) }, label = { Text("Name") }, singleLine = true, colors = fieldColors, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(12.dp))

            //campo age con teclado de numeros
            TextField(value = uiState.value.age, onValueChange = { viewModel.onAgeChange(it) }, label = { Text("Age") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, colors = fieldColors, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(12.dp))

            //campo email con teclado de correo
            TextField(value = uiState.value.email, onValueChange = { viewModel.onEmailChange(it) }, label = { Text("Email Address") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), singleLine = true, colors = fieldColors, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(12.dp))

            //campo password con ojito y texto de ayuda supportingText
            TextField(
                value = uiState.value.password,
                onValueChange = { viewModel.onPasswordChange(it) },
                label = { Text("Password") },
                singleLine = true,
                colors = fieldColors,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                supportingText = { Text("Must be at least 8 chars, 1 uppercase and 1 number") },
                trailingIcon = {
                    TextButton(onClick = { passwordVisible = !passwordVisible }) {
                        Text(if (passwordVisible) "Hide" else "Show", style = MaterialTheme.typography.labelSmall)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))

            //campo confirm password con ojito
            TextField(
                value = uiState.value.confirmPassword,
                onValueChange = { viewModel.onConfirmPasswordChange(it) },
                label = { Text("Confirm Password") },
                singleLine = true,
                colors = fieldColors,
                visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    TextButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                        Text(if (confirmPasswordVisible) "Hide" else "Show", style = MaterialTheme.typography.labelSmall)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))

            //mensaje de error si existe en el estado
            if (uiState.value.errorMessage != null) {
                Text(text = uiState.value.errorMessage!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(8.dp))
            }

            //boton para registrarse, muestra el spin de carga si isLoading es true
            Button(
                onClick = {
                    viewModel.register(onSuccess = onRegisterSuccess) //ejecutar la navegacion cuando el registro sea exitoso
                },
                enabled = !uiState.value.isLoading,
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                if (uiState.value.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text("Sign Up")
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            //boton para ir a la pantalla de login
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Already have an account?", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
                TextButton(onClick = onNavigateToLogin) {
                    Text("Log In")
                }
            }
        }
    }
}

//preview para poder ver la pantalla en el disenador de android studio
@Preview(showBackground = true, name = "Register Screen Preview")
@Composable
fun RegisterScreenPreview() {
    //repositorio falso solo para el preview, asi no llama a firebase
    val fakeRepository = object : AuthRepository {
        override fun isLoggedIn() = false
        override suspend fun login(email: String, password: String) = Result.success(Unit)
        override suspend fun registerUser(user: User) = Result.success(Unit)
        override fun logout() {}
    }
    AppTheme({
        RegisterScreen(viewModel = viewModel { RegisterViewModel(fakeRepository) })
    })
}