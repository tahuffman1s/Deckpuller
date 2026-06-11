package com.deckpuller.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage

/**
 * Build a Scryfall card image URL from a card's Scryfall id. The API redirects this to
 * the actual image CDN, so Coil can load it directly — handy when we only persisted the
 * id (collection imports, shopping list) and never the full image URL.
 */
fun scryfallImageUrl(scryfallId: String?, version: String = "small"): String? =
    scryfallId?.takeIf { it.isNotBlank() }
        ?.let { "https://api.scryfall.com/cards/$it?format=image&version=$version" }

/**
 * The little rounded card thumbnail used across the pull, collection and shopping screens.
 * Tapping it (when [onClick] is supplied) zooms the card, matching the pull screen.
 */
@Composable
fun CardThumbnail(
    imageUrl: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    width: Dp = 46.dp,
    height: Dp = 64.dp,
    onClick: (() -> Unit)? = null,
) {
    val placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceVariant)
    AsyncImage(
        model = imageUrl,
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        placeholder = placeholder,
        error = placeholder,
        fallback = placeholder,
        modifier = modifier
            .size(width = width, height = height)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it },
    )
}

/** Full-bleed tap-to-dismiss zoom of a single card image — shared by the card screens. */
@Composable
fun CardImageDialog(imageUrl: String?, name: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = imageUrl,
                contentDescription = name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)),
            )
        }
    }
}
