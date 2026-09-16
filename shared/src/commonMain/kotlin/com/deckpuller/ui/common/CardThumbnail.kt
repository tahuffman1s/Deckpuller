package com.deckpuller.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
 * Scryfall's CDN paths carry the image size as their first segment
 * (`cards.scryfall.io/<size>/front/<a>/<b>/<id>.jpg`), so an existing URL can be retargeted
 * at a different size without another API call. Thumbnails ask for `small` (146x204):
 * `normal` is 488x680, and decoding one of those for a 46x64dp row costs several
 * milliseconds and ~8x the bytes over the wire — enough to stutter a fling.
 *
 * Anything that isn't a recognised Scryfall JPEG path is returned untouched.
 */
fun scryfallSized(url: String?, version: String): String? {
    if (url == null || !url.endsWith(".jpg")) return url
    val marker = "cards.scryfall.io/"
    val hostEnd = url.indexOf(marker)
    if (hostEnd < 0) return url
    val start = hostEnd + marker.length
    val end = url.indexOf('/', start)
    if (end < 0) return url
    val current = url.substring(start, end)
    if (current !in SCRYFALL_JPEG_SIZES || current == version) return url
    return url.substring(0, start) + version + url.substring(end)
}

private val SCRYFALL_JPEG_SIZES = setOf("small", "normal", "large", "art_crop", "border_crop")

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
    val placeholderColor = MaterialTheme.colorScheme.surfaceVariant
    val placeholder = remember(placeholderColor) { ColorPainter(placeholderColor) }
    // A row thumbnail never needs more than Scryfall's `small` rendition.
    val thumbUrl = remember(imageUrl) { scryfallSized(imageUrl, "small") }
    // Foil cards get the same gentle holographic shimmer the pull list shows.
    val foilShimmer = if (isFoil) {
        Modifier.animatedFoilSheen(shape = RoundedCornerShape(8.dp), intensity = 0.5f)
    } else {
        Modifier
    }
    AsyncImage(
        model = thumbUrl,
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
