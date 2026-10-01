package com.moviles.ark.ui.screens
import androidx.compose.runtime.Composable
import com.moviles.ark.R
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.TextField
import androidx.compose.material3.Text
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.moviles.ark.ui.viewmodels.LoginUiState
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.moviles.ark.ui.viewmodels.LoginViewModel

@Composable
fun LoginScreen(uiState: LoginUiState,
                onEmailChange: (String) -> Unit, // En jetpack es necesario mandar las funciones que se usen por parametro
                onPasswordChange: (String) -> Unit,
                onLoginClick: () -> Unit,
                onNavigateToRegister:() -> Unit // Esta es pura de UI porque
) {
    // Como va una cosa debajo de la otra lo que hago es usar Colum
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(id =  R.drawable.ic_mascot_log), // R es una clase que todo lo que yo guarde en res le va a asignar un id y lo va a guardar para que yo lo pueda usar despues
            contentDescription = "Mascota de la aplicación" // esto pa ahcernos asecibles
        )
        TextField(
            value = uiState.email, // Texto actual -- si lo dejo como value="" nunca cambiara asi el usuario escriba
            onValueChange = onEmailChange, // nuevoTexto es la variable donde nos entrega el texto actualizado cada vez que el usuario escribe algo
            label = { Text("Email") }
        )
        TextField(
            value = uiState.password,
            onValueChange = onPasswordChange,
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation()
        )
        Button(
            onClick = onLoginClick
        ) {
            Text("Login")
        }
        TextButton(
            onClick = onNavigateToRegister
        ) {
            Text("Don't have an account? Sign up")
        }
    }
}

@Composable
fun LoginRoute(viewModel: LoginViewModel = viewModel(),
               onNavigateToRegister: () -> Unit) {
    // Escuchamos el estado desde la UI
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Le entregamos el estado y las funciones a nuestra pantalla
    LoginScreen(
        uiState = uiState,
        onEmailChange = viewModel::onEmailChange,
        onPasswordChange = viewModel::onPasswordChange,
        onLoginClick = viewModel::onLoginClick,
        onNavigateToRegister = onNavigateToRegister
    )
}