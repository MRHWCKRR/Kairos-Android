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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.kairos.app.utils.ImageUtils

/**
 * A robust profile image component that handles both web URLs and stored Base64 data.
 */
@Composable
fun ProfileImage(
    imageUrl: String,
    modifier: Modifier = Modifier,
    placeholderSize: Int = 40
) {
    // Determine the model for Coil. If it looks like Base64, decode it to ByteArray.
    val model = remember(imageUrl) {
        if (imageUrl.length > 100 || !imageUrl.startsWith("http")) {
            // Likely Base64 or non-URL data
            ImageUtils.decodeBase64(imageUrl)
        } else {
            // Likely a standard web URL
            imageUrl
        }
    }

    if (imageUrl.isNotBlank()) {
        AsyncImage(
            model = model,
            contentDescription = "Profile Picture",
            modifier = modifier.clip(CircleShape),
            contentScale = ContentScale.Crop,
            error = null // Fallback handled by the else block if model is null
        )
    } else {
        // Default placeholder
        Surface(
            modifier = modifier,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }
    }
}
