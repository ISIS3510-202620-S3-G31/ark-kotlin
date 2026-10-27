package com.moviles.ark.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moviles.ark.ui.theme.FigtreeFontFamily
import com.moviles.ark.ui.theme.PrimaryColor
import com.moviles.ark.ui.theme.TextColor

/**
 * Dialogo informativo amigable que se muestra cuando la autenticacion falla por falta de conexion a internet
 * o por tener activado el modo avion.
 */
@Composable
fun AuthNetworkErrorDialog(
    isAirplaneMode: Boolean,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        shape = RoundedCornerShape(24.dp),
        icon = {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(PrimaryColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.WifiOff,
                    contentDescription = if (isAirplaneMode) "Airplane mode" else "No internet connection",
                    tint = PrimaryColor,
                    modifier = Modifier.size(26.dp)
                )
            }
        },
        title = {
            Text(
                text = if (isAirplaneMode) "Airplane Mode Active" else "Internet Connection Required",
                style = MaterialTheme.typography.titleMedium,
                fontFamily = FigtreeFontFamily,
                fontWeight = FontWeight.Bold,
                color = TextColor,
                textAlign = TextAlign.Center
            )
        },
        text = {
            Text(
                text = if (isAirplaneMode) {
                    "Your device is in Airplane Mode. Authentication is strictly network-only and requires active server connectivity to verify your credentials. Please disable Airplane Mode and check your internet connection."
                } else {
                    "Authentication is strictly network-only and requires active server connectivity to verify credentials. Please check your Wi-Fi or mobile data connection and try again."
                },
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FigtreeFontFamily,
                color = TextColor.copy(alpha = 0.75f),
                lineHeight = 20.sp,
                textAlign = TextAlign.Center
            )
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Check Connection",
                    fontFamily = FigtreeFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
        }
    )
}
