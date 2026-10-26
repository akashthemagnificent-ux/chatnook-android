package com.chatnook.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

/**
 * Circular avatar with a solid fallback. Coil handles both still and animated
 * sources, so a GIF avatar decoration animates without extra work.
 */
@Composable
fun Avatar(
    url: String?,
    size: Int,
    accent: Color,
    fallbackIcon: ImageVector,
    fallbackLabel: String? = null,
    modifier: Modifier = Modifier,
) {
    val shape = CircleShape
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(shape)
            .background(accent),
        contentAlignment = Alignment.Center,
    ) {
        if (url.isNullOrBlank()) {
            if (fallbackLabel != null) {
                androidx.compose.material3.Text(
                    text = fallbackLabel.take(1).uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                )
            } else {
                Icon(
                    imageVector = fallbackIcon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size((size / 2).dp),
                )
            }
        } else {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size.dp).clip(shape),
            )
        }
    }
}
