package com.kairos.app.utils

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.delay
import kotlin.random.Random

@Composable
fun ConfettiCannon(trigger: Boolean, onFinished: () -> Unit) {
    if (trigger) {
        LaunchedEffect(Unit) {
            delay(3000)
            onFinished()
        }
        
        Canvas(modifier = Modifier.fillMaxSize()) {
            val rand = Random(42)
            repeat(50) {
                drawCircle(
                    color = Color(
                        red = rand.nextFloat(),
                        green = rand.nextFloat(),
                        blue = rand.nextFloat(),
                        alpha = 0.8f
                    ),
                    radius = 12f,
                    center = Offset(
                        x = rand.nextFloat() * size.width,
                        y = rand.nextFloat() * size.height
                    )
                )
            }
        }
    }
}
