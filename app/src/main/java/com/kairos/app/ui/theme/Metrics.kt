package com.kairos.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

object KairosMetrics {
    val pageHorizontal = 20.dp
    val pageVertical = 20.dp
    val sectionSpacing = 24.dp
    val itemSpacing = 12.dp
    val compactSpacing = 8.dp
    val cardRadius = 26.dp
    val controlRadius = 16.dp
    val minimumTouchTarget = 48.dp
}

val KairosShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(KairosMetrics.controlRadius),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(KairosMetrics.cardRadius)
)
