package com.moviles.ark.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.moviles.ark.R
import com.moviles.ark.ui.theme.AppTheme
import com.moviles.ark.ui.theme.BackgroundColor
import com.moviles.ark.ui.theme.TextColor

//pantalla provisional para las pestanas que aun no tienen su pantalla real
//stats la reemplaza el #15 y profile el #6; cuando ninguna la use, se puede borrar
@Composable
fun PlaceholderScreen(title: String, message: String) {
    Surface(modifier = Modifier.fillMaxSize(), color = BackgroundColor) {
        Column(
            modifier = Modifier.fillMaxSize().statusBarsPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_mascot_log),
                contentDescription = null,
                modifier = Modifier.size(110.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(title, style = MaterialTheme.typography.headlineLarge, color = TextColor)
            Spacer(modifier = Modifier.height(8.dp))
            Text(message, style = MaterialTheme.typography.bodyLarge, color = TextColor.copy(alpha = 0.7f), textAlign = TextAlign.Center)
        }
    }
}

@Preview(showBackground = true, name = "Placeholder Screen Preview")
@Composable
fun PlaceholderScreenPreview() {
    AppTheme {
        PlaceholderScreen(title = "Stats", message = "Your progress will show up here soon.")
    }
}
