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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.moviles.ark.R
import com.moviles.ark.domain.models.UserModel
import com.moviles.ark.domain.repositories.AuthRepository
import com.moviles.ark.ui.components.AuthButtonShape
import com.moviles.ark.ui.components.AuthNetworkErrorDialog
import com.moviles.ark.ui.components.AuthTextField
import com.moviles.ark.ui.theme.AppTheme
import com.moviles.ark.ui.theme.BackgroundColor
import com.moviles.ark.ui.theme.ErrorColor
import com.moviles.ark.ui.theme.PrimaryColor
import com.moviles.ark.ui.theme.SecondaryColor
import com.moviles.ark.ui.theme.TextColor
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

    if (uiState.showNoInternetDialog) {
        AuthNetworkErrorDialog(
            isAirplaneMode = uiState.isAirplaneModeError,
            onDismiss = viewModel::dismissNoInternetDialog
        )
    }

    Surface(modifier = Modifier.fillMaxSize(), color = BackgroundColor) {
        //imePadding achica la columna cuando sale el teclado y el scroll deja ver el campo que se esta llenando
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(R.drawable.ic_mascot_log),
                contentDescription = "App Mascot",
                modifier = Modifier.size(115.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))

            Text("Create Account", style = MaterialTheme.typography.headlineLarge, color = TextColor, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Sign up to start your wellbeing journey",
                style = MaterialTheme.typography.bodyLarge,
                color = TextColor.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))

            //misma tarjeta redondeada del login para que las dos pantallas se vean iguales
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = TextColor.copy(alpha = 0.06f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 24.dp)
                ) {
                    Text(text = "Sign Up", style = MaterialTheme.typography.headlineSmall, color = TextColor)
                    Spacer(modifier = Modifier.height(16.dp))

                    //campo nombre con validacion reactiva inline
                    AuthTextField(
                        value = uiState.name,
                        onValueChange = viewModel::onNameChange,
                        label = "Name",
                        error = uiState.nameError,
                        leadingIcon = Icons.Default.Person
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    //campo fecha de nacimiento con icono de calendario para abrir selector
                    AuthTextField(
                        value = uiState.birthDate,
                        onValueChange = viewModel::onBirthDateChange,
                        label = "Date of Birth (YYYY-MM-DD)",
                        error = uiState.birthDateError,
                        leadingIcon = Icons.Default.Cake,
                        trailingIcon = {
                            IconButton(onClick = { datePickerDialog.show() }) {
                                Icon(Icons.Default.DateRange, contentDescription = "Select birth date", tint = TextColor)
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    //campo correo con validacion reactiva
                    AuthTextField(
                        value = uiState.email,
                        onValueChange = viewModel::onEmailChange,
                        label = "Email Address",
                        error = uiState.emailError,
                        leadingIcon = Icons.Default.Email,
                        keyboardType = KeyboardType.Email
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    //campo contrasena con ojito
                    AuthTextField(
                        value = uiState.password,
                        onValueChange = viewModel::onPasswordChange,
                        label = "Password",
                        error = uiState.passwordError,
                        leadingIcon = Icons.Default.Lock,
                        keyboardType = KeyboardType.Password,
                        isPassword = true,
                        passwordVisible = passwordVisible,
                        trailingIcon = {
                            PasswordVisibilityButton(visible = passwordVisible, onClick = { passwordVisible = !passwordVisible })
                        }
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    //campo confirmar contrasena con validacion inline de coincidencia
                    AuthTextField(
                        value = uiState.confirmPassword,
                        onValueChange = viewModel::onConfirmPasswordChange,
                        label = "Confirm Password",
                        error = uiState.confirmPasswordError,
                        leadingIcon = Icons.Default.Lock,
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                        isPassword = true,
                        passwordVisible = confirmPasswordVisible,
                        trailingIcon = {
                            PasswordVisibilityButton(visible = confirmPasswordVisible, onClick = { confirmPasswordVisible = !confirmPasswordVisible })
                        }
                    )

                    //mensaje de error general o del backend si ocurre
                    if (uiState.errorMessage != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(text = uiState.errorMessage!!, color = ErrorColor, style = MaterialTheme.typography.bodyLarge)
                    }

                    //mensaje de exito si el registro fue satisfactorio
                    if (uiState.successMessage != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(text = uiState.successMessage!!, color = TextColor, style = MaterialTheme.typography.bodyLarge)
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    //boton registrarse habilitado de forma reactiva cuando el formulario es valido
                    Button(
                        onClick = { viewModel.register(onSuccess = onRegisterSuccess) },
                        enabled = uiState.isFormValid && !uiState.isLoading,
                        shape = AuthButtonShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrimaryColor,
                            contentColor = TextColor,
                            disabledContainerColor = PrimaryColor.copy(alpha = 0.5f),
                            disabledContentColor = TextColor.copy(alpha = 0.6f)
                        ),
                        modifier = Modifier.fillMaxWidth().height(50.dp)
                    ) {
                        if (uiState.isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = TextColor, strokeWidth = 2.5.dp)
                        } else {
                            Text("Sign Up", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                Text("Already have an account?", style = MaterialTheme.typography.bodyLarge, color = TextColor.copy(alpha = 0.75f))
                TextButton(onClick = onNavigateToLogin) {
                    Text("Log In", style = MaterialTheme.typography.labelLarge, color = SecondaryColor)
                }
            }
        }
    }
}

//ojito para mostrar u ocultar la contrasena, igual al del login
@Composable
private fun PasswordVisibilityButton(visible: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
            contentDescription = if (visible) "Hide password" else "Show password",
            tint = TextColor,
            modifier = Modifier.size(20.dp)
        )
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
