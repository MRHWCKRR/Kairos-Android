package com.kairos.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

fun getKairosTypography(font: String): Typography {
    val family = when (font) {
        "round" -> FontFamily.SansSerif // Fallback for round if no custom font
        "mono" -> FontFamily.Monospace
        else -> FontFamily.Default
    }

    return Typography(
        headlineLarge = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.ExtraBold, 
            fontSize = 32.sp
        ),
        titleMedium = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Bold, 
            fontSize = 18.sp
        ),
        bodyMedium = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Normal, 
            fontSize = 14.sp
        ),
        labelMedium = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp
        )
    )
}

// Keep the old one for compatibility if needed elsewhere temporarily
val KairosTypography = getKairosTypography("sans")
