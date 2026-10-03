package com.moviles.ark.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.moviles.ark.R

//Font families
//Sorean
val SoreanFontFamily = FontFamily(Font(R.font.sorean, FontWeight.Normal))
//Figtree
val FigtreeFontFamily = FontFamily(
    Font(R.font.figtree_normal, FontWeight.Normal),
    Font(R.font.figtree_bold, FontWeight.Bold)
    )

// Typography hierarchy
val Typography = Typography(
    // H1
    headlineLarge = TextStyle(
        fontFamily = SoreanFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 28.sp,
        lineHeight = 34.sp
    ),
    // H2
    headlineMedium = TextStyle(
        fontFamily = FigtreeFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    // H3
    headlineSmall = TextStyle(
        fontFamily = FigtreeFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 18.sp,
        lineHeight = 24.sp
    ),
    // Body
    bodyLarge = TextStyle(
        fontFamily = FigtreeFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    // Buttons and labels
    labelLarge = TextStyle(
        fontFamily = FigtreeFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    )
)