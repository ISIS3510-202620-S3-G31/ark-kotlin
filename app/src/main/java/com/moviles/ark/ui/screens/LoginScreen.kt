package com.moviles.ark.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.moviles.ark.R
import com.moviles.ark.ui.theme.AppTheme
import com.moviles.ark.ui.theme.ErrorColor
import com.moviles.ark.ui.theme.FigtreeFontFamily
import com.moviles.ark.ui.theme.PrimaryColor
import com.moviles.ark.ui.theme.SoreanFontFamily
import com.moviles.ark.ui.theme.TextColor
import com.moviles.ark.ui.viewmodels.LoginUiState
import com.moviles.ark.ui.viewmodels.LoginViewModel

@Composable
fun LoginScreen(
    uiState: LoginUiState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onLoginClick: () -> Unit,
    onNavigateToRegister: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var passwordVisible by remember { mutableStateOf(false) }

    // Fondo calido oficial (#FFF5E6) acorde al estilo de RegisterScreen y la identidad de Ark
    val warmBackgroundColor = Color(0xFFFFF5E6)

    // Colores limpios y consistentes para los campos de texto
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = Color.White,
        unfocusedContainerColor = Color.White,
        focusedBorderColor = PrimaryColor,
        unfocusedBorderColor = TextColor.copy(alpha = 0.2f),
        focusedLabelColor = PrimaryColor,
        unfocusedLabelColor = TextColor.copy(alpha = 0.6f),
        cursorColor = PrimaryColor
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = warmBackgroundColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Mascota oficial de Ark con tamaño proporcionado y espacio cómodo
            Image(
                painter = painterResource(id = R.drawable.ic_mascot_log),
                contentDescription = "Ark Mascot",
                modifier = Modifier.size(115.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Titulo con la fuente Sorean (headlineLarge) y subtitulo en Figtree
            Text(
                text = "Glad you're here",
                style = MaterialTheme.typography.headlineLarge,
                fontFamily = SoreanFontFamily,
                color = TextColor,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Take a deep breath and enter your calm space.",
                style = MaterialTheme.typography.bodyLarge,
                fontFamily = FigtreeFontFamily,
                color = TextColor.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Contenedor tipo tarjeta redondeada con elevacion sutil para el formulario
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                border = BorderStroke(1.dp, PrimaryColor.copy(alpha = 0.12f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 24.dp)
                ) {
                    Text(
                        text = "Sign In",
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = FigtreeFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = TextColor
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Campo Email con icono representativo, validacion reactiva y supportingText unicamente en error
                    OutlinedTextField(
                        value = uiState.email,
                        onValueChange = onEmailChange,
                        label = { Text("Email Address", fontFamily = FigtreeFontFamily) },
                        isError = uiState.emailError != null,
                        supportingText = if (uiState.emailError != null) {
                            { Text(uiState.emailError, color = MaterialTheme.colorScheme.error) }
                        } else null,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Email,
                                contentDescription = null,
                                tint = PrimaryColor,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = fieldColors,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Campo Password con alternador visual y supportingText unicamente en error
                    OutlinedTextField(
                        value = uiState.password,
                        onValueChange = onPasswordChange,
                        label = { Text("Password", fontFamily = FigtreeFontFamily) },
                        isError = uiState.passwordError != null,
                        supportingText = if (uiState.passwordError != null) {
                            { Text(uiState.passwordError, color = MaterialTheme.colorScheme.error) }
                        } else null,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = PrimaryColor,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                passwordVisible = !passwordVisible
                            }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (passwordVisible) "Hide password" else "Show password",
                                    tint = TextColor.copy(alpha = 0.6f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = fieldColors,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Mensaje de error si la autenticacion falla
                    if (uiState.errorMessage != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = uiState.errorMessage,
                            color = ErrorColor,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FigtreeFontFamily
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Boton principal de Login habilitado cuando el formulario es valido
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onLoginClick()
                        },
                        enabled = uiState.isFormValid && !uiState.isLoading,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrimaryColor,
                            disabledContainerColor = PrimaryColor.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        if (uiState.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = Color.White,
                                strokeWidth = 2.5.dp
                            )
                        } else {
                            Text(
                                text = "Log In",
                                style = MaterialTheme.typography.labelLarge,
                                fontFamily = FigtreeFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Enlace de navegacion hacia registro
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Don't have an account?",
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FigtreeFontFamily,
                    color = TextColor.copy(alpha = 0.75f)
                )
                TextButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onNavigateToRegister()
                    }
                ) {
                    Text(
                        text = "Sign up",
                        style = MaterialTheme.typography.labelLarge,
                        fontFamily = FigtreeFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryColor
                    )
                }
            }
        }
    }
}

@Composable
fun LoginRoute(
    viewModel: LoginViewModel = viewModel(factory = LoginViewModel.Factory),
    onNavigateToRegister: () -> Unit,
    onLoginSuccess: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LoginScreen(
        uiState = uiState,
        onEmailChange = viewModel::onEmailChange,
        onPasswordChange = viewModel::onPasswordChange,
        onLoginClick = { viewModel.onLoginClick(onLoginSuccess) },
        onNavigateToRegister = onNavigateToRegister
    )
}

@Preview(showBackground = true, name = "Pantalla de Login Normal")
@Composable
fun LoginScreenPreview() {
    AppTheme {
        val dummyUiState = LoginUiState(
            email = "usuario@ejemplo.com",
            password = "password123",
            isFormValid = true,
            isLoading = false
        )

        LoginScreen(
            uiState = dummyUiState,
            onEmailChange = {},
            onPasswordChange = {},
            onLoginClick = {},
            onNavigateToRegister = {}
        )
    }
}
