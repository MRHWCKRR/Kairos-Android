package com.kairos.app.ui.theme

import android.graphics.Typeface

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

fun getKairosTypography(font: String): Typography {
    val rounded = FontFamily(Typeface.create("sans-serif-rounded", Typeface.NORMAL))
    val family = when (font) {
        "round", "rounded" -> rounded
        "mono", "monospaced" -> FontFamily.Monospace
        "serif" -> FontFamily.Serif
        // Native sans substitutes for web fonts and Apple-only system fonts.
        else -> FontFamily.SansSerif
    }
    fun style(size: Int, line: Int, weight: FontWeight = FontWeight.Normal) = TextStyle(
        fontFamily = family, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight
    )
    return Typography(
        displayLarge = style(44, 52, FontWeight.Bold).copy(fontFamily = rounded),
        displayMedium = style(40, 48, FontWeight.Bold).copy(fontFamily = rounded),
        displaySmall = style(38, 46, FontWeight.Bold).copy(fontFamily = rounded),
        headlineLarge = style(34, 42, FontWeight.Bold).copy(fontFamily = rounded),
        headlineMedium = style(28, 36, FontWeight.Bold),
        headlineSmall = style(24, 32, FontWeight.Bold),
        titleLarge = style(22, 30, FontWeight.Bold),
        titleMedium = style(18, 26, FontWeight.SemiBold),
        titleSmall = style(16, 24, FontWeight.SemiBold),
        bodyLarge = style(16, 24),
        bodyMedium = style(14, 22),
        bodySmall = style(12, 18),
        labelLarge = style(14, 20, FontWeight.SemiBold),
        labelMedium = style(12, 18, FontWeight.Medium),
        labelSmall = style(11, 16, FontWeight.Medium)
    )
}

val KairosTypography = getKairosTypography("sans")

