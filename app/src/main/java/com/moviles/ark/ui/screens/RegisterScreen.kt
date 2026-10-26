package com.moviles.ark.ui.screens

import android.app.DatePickerDialog
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.moviles.ark.R
import com.moviles.ark.domain.models.UserModel
import com.moviles.ark.domain.repositories.AuthRepository
import com.moviles.ark.ui.theme.AppTheme
import com.moviles.ark.ui.theme.SecondaryColor
import com.moviles.ark.ui.viewmodels.RegisterViewModel
import java.util.Calendar
import java.util.Locale

@Composable
fun RegisterScreen(
    viewModel: RegisterViewModel = viewModel(factory = RegisterViewModel.Factory),
    onNavigateToLogin: () -> Unit = {},
    onRegisterSuccess: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    //colores limpios para las cajitas de texto en blanco
    val fieldColors = TextFieldDefaults.colors(
        focusedContainerColor = Color.White,
        unfocusedContainerColor = Color.White
    )

    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }

    //dialogo nativo de seleccion de fecha para evitar errores de tipeo
    val calendar = Calendar.getInstance()
    val defaultYear = calendar.get(Calendar.YEAR) - 20
    val defaultMonth = calendar.get(Calendar.MONTH)
    val defaultDay = calendar.get(Calendar.DAY_OF_MONTH)
    val datePickerDialog = remember {
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val formatted = String.format(Locale.ROOT, "%04d-%02d-%02d", year, month + 1, dayOfMonth)
                viewModel.onBirthDateChange(formatted)
            },
            defaultYear,
            defaultMonth,
            defaultDay
        )
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(R.drawable.ic_mascot_log),
                contentDescription = "App Mascot",
                modifier = Modifier.size(125.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))

            Text("Create Account", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onBackground)
            Text("Sign up to start your wellbeing journey", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
            Spacer(modifier = Modifier.height(20.dp))

            //campo nombre con validacion reactiva inline
            TextField(
                value = uiState.name,
                onValueChange = viewModel::onNameChange,
                label = { Text("Name") },
                isError = uiState.nameError != null,
                supportingText = if (uiState.nameError != null) {
                    { Text(uiState.nameError!!, color = MaterialTheme.colorScheme.error) }
                } else null,
                singleLine = true,
                colors = fieldColors,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))

            //campo fecha de nacimiento con icono de calendario para abrir selector
            TextField(
                value = uiState.birthDate,
                onValueChange = viewModel::onBirthDateChange,
                label = { Text("Date of Birth (YYYY-MM-DD)") },
                isError = uiState.birthDateError != null,
                supportingText = if (uiState.birthDateError != null) {
                    { Text(uiState.birthDateError!!, color = MaterialTheme.colorScheme.error) }
                } else null,
                trailingIcon = {
                    IconButton(onClick = { datePickerDialog.show() }) {
                        Icon(Icons.Default.DateRange, contentDescription = "Select birth date", tint = MaterialTheme.colorScheme.primary)
                    }
                },
                singleLine = true,
                colors = fieldColors,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))

            //campo correo con validacion reactiva
            TextField(
                value = uiState.email,
                onValueChange = viewModel::onEmailChange,
                label = { Text("Email Address") },
                isError = uiState.emailError != null,
                supportingText = if (uiState.emailError != null) {
                    { Text(uiState.emailError!!, color = MaterialTheme.colorScheme.error) }
                } else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                singleLine = true,
                colors = fieldColors,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))

            //campo contrasena con ojito y supporting text unicamente cuando hay error
            TextField(
                value = uiState.password,
                onValueChange = viewModel::onPasswordChange,
                label = { Text("Password") },
                isError = uiState.passwordError != null,
                supportingText = if (uiState.passwordError != null) {
                    { Text(uiState.passwordError!!, color = MaterialTheme.colorScheme.error) }
                } else null,
                singleLine = true,
                colors = fieldColors,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    TextButton(onClick = { passwordVisible = !passwordVisible }) {
                        Text(if (passwordVisible) "Hide" else "Show", style = MaterialTheme.typography.labelSmall)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))

            //campo confirmar contrasena con validacion inline de coincidencia
            TextField(
                value = uiState.confirmPassword,
                onValueChange = viewModel::onConfirmPasswordChange,
                label = { Text("Confirm Password") },
                isError = uiState.confirmPasswordError != null,
                supportingText = if (uiState.confirmPasswordError != null) {
                    { Text(uiState.confirmPasswordError!!, color = MaterialTheme.colorScheme.error) }
                } else null,
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

            //mensaje de error general o del backend si ocurre
            if (uiState.errorMessage != null) {
                Text(text = uiState.errorMessage!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(8.dp))
            }

            //mensaje de exito si el registro fue satisfactorio
            if (uiState.successMessage != null) {
                Text(text = uiState.successMessage!!, color = SecondaryColor, style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(8.dp))
            }

            //boton registrarse habilitado de forma reactiva cuando el formulario es valido
            Button(
                onClick = {
                    viewModel.register(onSuccess = onRegisterSuccess)
                },
                enabled = uiState.isFormValid && !uiState.isLoading,
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text("Sign Up")
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Already have an account?", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
                TextButton(onClick = onNavigateToLogin) {
                    Text("Log In")
                }
            }
        }
    }
}

@Preview(showBackground = true, name = "Register Screen Preview")
@Composable
fun RegisterScreenPreview() {
    val fakeRepository = object : AuthRepository {
        override fun isLoggedIn() = false
        override suspend fun login(email: String, password: String) = Result.success(Unit)
        override suspend fun registerUser(user: UserModel) = Result.success(Unit)
        override fun logout() {}
    }
    AppTheme {
        RegisterScreen(viewModel = viewModel { RegisterViewModel(fakeRepository) })
    }
}
