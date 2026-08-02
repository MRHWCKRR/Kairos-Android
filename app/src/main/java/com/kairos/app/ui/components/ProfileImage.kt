package com.kairos.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import com.kairos.app.utils.ImageUtils

/**
 * A robust profile image component that handles both web URLs and stored Base64 data.
 */
@Composable
fun ProfileImage(
    imageUrl: String,
    modifier: Modifier = Modifier,
    userName: String = ""
) {
    var isLoading by remember { mutableStateOf(true) }
    var isError by remember { mutableStateOf(false) }

    // Determine the model for Coil. If it looks like Base64, decode it to ByteArray.
    val model = remember(imageUrl) {
        if (imageUrl.length > 100 || (!imageUrl.startsWith("http") && imageUrl.isNotBlank())) {
            ImageUtils.decodeBase64(imageUrl)
        } else if (imageUrl.isNotBlank()) {
            imageUrl
        } else {
            null
        }
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (model != null && !isError) {
            AsyncImage(
                model = model,
                contentDescription = "Profile Picture",
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape),
                contentScale = ContentScale.Crop,
                onState = { state ->
                    isLoading = state is AsyncImagePainter.State.Loading
                    isError = state is AsyncImagePainter.State.Error
                }
            )
        }

        // Show Placeholder if no model, or error, or still loading
        if (model == null || isError) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (userName.isNotBlank()) {
                        Text(
                            text = userName.take(1).uppercase(),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}
