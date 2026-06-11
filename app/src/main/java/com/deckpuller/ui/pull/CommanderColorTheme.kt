package com.deckpuller.ui.pull

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * Themes its [content] from a commander's MTG colour identity (e.g. `["G", "U"]`).
 *
 * The accent roles (primary/secondary/tertiary + containers) are recoloured from the
 * identity's pips — mono-colour commanders get a single hue, guilds blend the first
 * two, and so on. Colourless commanders fall back to a neutral pewter. The base
 * surface/background palette is left intact so contrast stays safe; only the accents
 * shift. With no identity the unmodified theme is used.
 */
@Composable
fun CommanderColorTheme(colors: List<String>, content: @Composable () -> Unit) {
    val base = MaterialTheme.colorScheme
    val scheme = remember(colors, base) { base.withCommanderColors(colors) }
    MaterialTheme(
        colorScheme = scheme,
        typography = MaterialTheme.typography,
        shapes = MaterialTheme.shapes,
        content = content,
    )
}

/** The commander identity's pip hues as Compose colours (e.g. for confetti). Empty if
 *  the identity is colourless/unrecognised — callers can fall back to a default palette. */
fun manaColors(identity: List<String>): List<Color> = identity.mapNotNull(::manaColor)

/** Representative hue for each MTG mana symbol. Black is a muted purple so it reads as
 *  an accent rather than a near-invisible true black. */
private fun manaColor(symbol: String): Color? = when (symbol.uppercase()) {
    "W" -> Color(0xFFE7D7A8)
    "U" -> Color(0xFF2E72B8)
    "B" -> Color(0xFF6E5A78)
    "R" -> Color(0xFFC8412C)
    "G" -> Color(0xFF3E9B57)
    else -> null
}

private fun Color.mix(other: Color, t: Float): Color = Color(
    red = red + (other.red - red) * t,
    green = green + (other.green - green) * t,
    blue = blue + (other.blue - blue) * t,
    alpha = 1f,
)

private fun onColor(color: Color): Color =
    if (color.luminance() > 0.5f) Color.Black else Color.White

private fun ColorScheme.withCommanderColors(colors: List<String>): ColorScheme {
    val mapped = colors.mapNotNull(::manaColor)
    // Colourless commanders (artifacts/Eldrazi) get a neutral pewter; an unrecognised
    // non-empty identity just keeps the existing theme.
    val seeds = when {
        mapped.isNotEmpty() -> mapped
        colors.isEmpty() -> listOf(Color(0xFF8D8D99))
        else -> return this
    }
    val primary = seeds[0]
    val secondary = seeds.getOrElse(1) { seeds[0] }
    val tertiary = seeds.getOrElse(2) { secondary }
    val dark = surface.luminance() < 0.5f

    fun accent(seed: Color) = if (dark) seed.mix(Color.White, 0.2f) else seed
    fun container(seed: Color) =
        if (dark) seed.mix(Color.Black, 0.45f) else seed.mix(Color.White, 0.72f)
    fun onContainer(seed: Color) =
        if (dark) seed.mix(Color.White, 0.7f) else seed.mix(Color.Black, 0.55f)

    return copy(
        primary = accent(primary),
        onPrimary = onColor(accent(primary)),
        primaryContainer = container(primary),
        onPrimaryContainer = onContainer(primary),
        secondary = accent(secondary),
        onSecondary = onColor(accent(secondary)),
        secondaryContainer = container(secondary),
        onSecondaryContainer = onContainer(secondary),
        tertiary = accent(tertiary),
        onTertiary = onColor(accent(tertiary)),
        tertiaryContainer = container(tertiary),
        onTertiaryContainer = onContainer(tertiary),
        // A faint wash of the primary keeps tracks/chips/search field on-theme.
        surfaceVariant = surfaceVariant.mix(primary, if (dark) 0.18f else 0.12f),
    )
}
