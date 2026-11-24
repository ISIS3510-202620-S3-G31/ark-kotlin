package com.moviles.ark.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.moviles.ark.domain.models.CookieModel
import com.moviles.ark.ui.theme.BackgroundColor
import com.moviles.ark.ui.theme.PrimaryColor
import com.moviles.ark.ui.theme.SecondaryColor
import com.moviles.ark.ui.theme.TextColor

//detalle de una galleta: titulo, texto, imagen y "Eat 🍪" (o "Return to jar" si ya se comio)
@Composable
fun CookieDetailDialog(
    cookie: CookieModel,
    image: Bitmap?,
    isImageLoading: Boolean,
    onEat: () -> Unit,
    onReturnToJar: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(BackgroundColor)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Text(
                    text = cookie.title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextColor,
                    modifier = Modifier
                        .weight(1f)
                        .padding(top = 8.dp)
                )
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(TextColor.copy(alpha = 0.08f))
                        .clickable(role = Role.Button, onClickLabel = "Close", onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = TextColor)
                }
            }

            if (cookie.hasImage) {
                Spacer(modifier = Modifier.height(16.dp))
                CookieImage(image = image, isLoading = isImageLoading)
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = cookie.text,
                style = MaterialTheme.typography.bodyLarge,
                color = TextColor,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(24.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                if (cookie.isActive) {
                    Button(
                        onClick = onEat,
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor, contentColor = TextColor),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Text("Eat 🍪", style = MaterialTheme.typography.labelLarge)
                    }
                } else {
                    Button(
                        onClick = onReturnToJar,
                        colors = ButtonDefaults.buttonColors(containerColor = SecondaryColor, contentColor = TextColor),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Text("Return to jar", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

//la imagen guardada en el telefono; si no esta (se desalojo o esta en la nube) se avisa en su lugar
@Composable
private fun CookieImage(image: Bitmap?, isLoading: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 120.dp, max = 360.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(TextColor.copy(alpha = 0.06f)),
        contentAlignment = Alignment.Center
    ) {
        when {
            image != null -> Image(
                bitmap = image.asImageBitmap(),
                contentDescription = "Picture of the cookie",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth()
            )
            isLoading -> CircularProgressIndicator(color = SecondaryColor, modifier = Modifier.size(32.dp))
            else -> Text(
                text = "The picture isn't on this phone",
                style = MaterialTheme.typography.bodyLarge,
                color = TextColor.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(24.dp)
            )
        }
    }
}
