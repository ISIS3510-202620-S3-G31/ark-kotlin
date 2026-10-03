package com.moviles.ark.ui.screens
import androidx.compose.runtime.Composable
import com.moviles.ark.R
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.TextField
import androidx.compose.material3.Text
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.moviles.ark.ui.viewmodels.LoginUiState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.moviles.ark.ui.viewmodels.LoginViewModel
import androidx.compose.ui.tooling.preview.Preview
import com.moviles.ark.ui.theme.AppTheme

@Composable
fun LoginScreen(uiState: LoginUiState,
                onEmailChange: (String) -> Unit, // En jetpack es necesario mandar las funciones que se usen por parametro
                onPasswordChange: (String) -> Unit,
                onLoginClick: () -> Unit,
                onNavigateToRegister:() -> Unit // Esta es pura de UI porque
) {
    val fieldColors = TextFieldDefaults.colors(focusedContainerColor = Color.White, unfocusedContainerColor = Color.White)
    var confirmPasswordVisible by remember { mutableStateOf(false) }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        // Como va una cosa debajo de la otra lo que hago es usar Colum

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_mascot_log), // R es una clase que todo lo que yo guarde en res le va a asignar un id y lo va a guardar para que yo lo pueda usar despues
                contentDescription = "Mascota de la aplicación" // esto pa ahcernos asecibles
            )
            Spacer(modifier = Modifier.height(16.dp))

            //titulo con la fuente Sorean (headlineLarge) y subtitulo
            Text("Glad you're here", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onBackground)
            Text("Take a deep breath and enter your calm space.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
            Spacer(modifier = Modifier.height(20.dp))

            TextField(
                value = uiState.email, // Texto actual -- si lo dejo como value="" nunca cambiara asi el usuario escriba
                onValueChange = onEmailChange, // nuevoTexto es la variable donde nos entrega el texto actualizado cada vez que el usuario escribe algo
                label = { Text("Email") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                singleLine = true,
                colors = fieldColors, modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))

            TextField(
                value = uiState.password,
                onValueChange = onPasswordChange,
                label = { Text("Password") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    TextButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                        Text(if (confirmPasswordVisible) "Hide" else "Show", style = MaterialTheme.typography.labelSmall)
                    }
                },
                singleLine = true,
                colors = fieldColors, modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))

            // mensaje de error si el login falla
            if (uiState.errorMessage != null) {
                Text(text = uiState.errorMessage, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.height(8.dp))
            }

            Button(
                onClick = onLoginClick,
                enabled = !uiState.isLoading // Evita múltiples clics mientras carga
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),// Usamos dp porque es más facil cuando dos telefonos tiene difernte resolucion hace que to do quede proporcioinal en dif pantallas
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Login")
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            TextButton(
                onClick = onNavigateToRegister
            ) {
                Text("Don't have an account? Sign up")
            }
        }
    }
}

@Composable
fun LoginRoute(viewModel: LoginViewModel = viewModel(factory = LoginViewModel.Factory),
               onNavigateToRegister: () -> Unit,
               onLoginSuccess: () -> Unit = {}) {
    // Escuchamos el estado desde la UI
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Le entregamos el estado y las funciones a nuestra pantalla
    LoginScreen(
        uiState = uiState,
        onEmailChange = viewModel::onEmailChange,
        onPasswordChange = viewModel::onPasswordChange,
        onLoginClick = { viewModel.onLoginClick(onLoginSuccess) },
        onNavigateToRegister = onNavigateToRegister
    )
}

// ESTO ES PARA IR MIRANDO COMO ESTAN LAS COSAS VISUALMENTE Y ASDI PODER CORREREGIR SIN LANZAR EL EMULADOR Y TAMBIEN MIRAR LOS ESTADOS GRACUAS A PREVIEW

@Preview(showBackground = true, name = "Pantalla de Login Normal")
@Composable
fun LoginScreenPreview() {
    //  Importante: Envolvemos con el tema de tu App para ver los colores correctos
    AppTheme {
        //  Creamos un estado de "mentira" para simular lo que pasaría en la app real
        val dummyUiState = LoginUiState(
            email = "usuario@ejemplo.com",
            password = "password123",
            isLoading = false //  Empezamos simulando que NO está cargando
        )

        //  Llamamos a nuestra pantalla real pasándole los datos simulados
        LoginScreen(
            uiState = dummyUiState,
            // Le pasamos funciones vacías '{}' porque en el preview no necesitamos que hagan nada
            onEmailChange = {},
            onPasswordChange = {},
            onLoginClick = {},
            onNavigateToRegister = {}
        )
    }
}