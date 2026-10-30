package com.moviles.ark.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.moviles.ark.ui.theme.BackgroundColor
import com.moviles.ark.ui.theme.FigtreeFontFamily
import com.moviles.ark.ui.theme.SecondaryColor
import com.moviles.ark.ui.theme.TextColor

//esquinas de los campos y botones de login y registro, las mismas de las tarjetas del resto de la app
val AuthFieldShape = RoundedCornerShape(14.dp)
val AuthButtonShape = RoundedCornerShape(16.dp)

//campo de texto de login y registro: solo colores de la paleta (ms6) y letra figtree
//el error se muestra debajo del campo unicamente cuando existe, en el rojo de error del tema
@Composable
fun AuthTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    error: String?,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    isPassword: Boolean = false,
    passwordVisible: Boolean = false
) {
    val errorColor = MaterialTheme.colorScheme.error
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        //se cambia solo la fuente: material anima el tamano de la etiqueta y usaria su letra por defecto
        label = { Text(label, style = LocalTextStyle.current.copy(fontFamily = FigtreeFontFamily)) },
        isError = error != null,
        supportingText = error?.let { { Text(it, style = MaterialTheme.typography.bodyLarge, color = errorColor) } },
        leadingIcon = leadingIcon?.let { icon ->
            { Icon(imageVector = icon, contentDescription = null, tint = TextColor, modifier = Modifier.size(20.dp)) }
        },
        trailingIcon = trailingIcon,
        visualTransformation = if (isPassword && !passwordVisible) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = TextColor),
        shape = AuthFieldShape,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = BackgroundColor,
            unfocusedContainerColor = BackgroundColor,
            errorContainerColor = BackgroundColor,
            focusedBorderColor = SecondaryColor,
            unfocusedBorderColor = TextColor.copy(alpha = 0.3f),
            errorBorderColor = errorColor,
            focusedLabelColor = TextColor,
            unfocusedLabelColor = TextColor.copy(alpha = 0.7f),
            errorLabelColor = errorColor,
            cursorColor = TextColor
        ),
        modifier = modifier.fillMaxWidth()
    )
}
