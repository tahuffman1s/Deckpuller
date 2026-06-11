package com.deckpuller.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

/**
 * Build a Scryfall card image URL straight from a card's Scryfall id by addressing the
 * image CDN directly (`cards.scryfall.io/<size>/front/<a>/<b>/<id>.jpg`). We deliberately
 * avoid the `api.scryfall.com/cards/<id>?format=image` redirect: that endpoint is rate
 * limited and loading a whole grid through it gets throttled (so images never appear),
 * whereas the CDN is unmetered. Handy when we only persisted the id (collection imports,
 * shopping list) and never the full image URL.
 */
fun scryfallImageUrl(scryfallId: String?, version: String = "small"): String? {
    val id = scryfallId?.trim()?.lowercase()?.takeIf { it.length >= 2 } ?: return null
    return "https://cards.scryfall.io/$version/front/${id[0]}/${id[1]}/$id.jpg"
}

/**
 * Scryfall's canonical "card back" id — the standard brown Magic back, served from the
 * dedicated card-backs CDN ([scryfallGenericCardBackUrl]).
 */
private const val MTG_CARD_BACK_ID = "0aeebaf5-8c7d-4636-9e82-8c27447861f7"

/**
 * The back-face image for a double-faced card, addressed via the CDN's `back/` path. For a
 * single-faced card this 404s (there is no real back), so callers should fall back to
 * [scryfallGenericCardBackUrl] on a load error.
 */
fun scryfallBackFaceUrl(scryfallId: String?, version: String = "small"): String? {
    val id = scryfallId?.trim()?.lowercase()?.takeIf { it.length >= 2 } ?: return null
    return "https://cards.scryfall.io/$version/back/${id[0]}/${id[1]}/$id.jpg"
}

/** The standard Magic card back, served from Scryfall's card-backs CDN. */
fun scryfallGenericCardBackUrl(version: String = "normal"): String =
    "https://backs.scryfall.io/$version/${MTG_CARD_BACK_ID[0]}/${MTG_CARD_BACK_ID[1]}/$MTG_CARD_BACK_ID.jpg"

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
    isFoil: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceVariant)
    // Foil cards get the same gentle holographic shimmer the pull list shows.
    val foilShimmer = if (isFoil) {
        Modifier.animatedFoilSheen(shape = RoundedCornerShape(8.dp), intensity = 0.5f)
    } else {
        Modifier
    }
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
            .then(foilShimmer)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it },
    )
}
