package com.deckpuller.domain

import platform.Foundation.NSDiacriticInsensitiveSearch
import platform.Foundation.NSString

/**
 * iOS diacritic stripping. `java.text.Normalizer` and `\p{Mn}` regex aren't available on
 * Kotlin/Native, so we fold with Foundation's diacritic-insensitive option (café → cafe,
 * Lim-Dûl → Lim-Dul), which covers the accent removal MTG card-name matching needs. Casing and
 * the "//" face-separator handling are applied later in the common [CardName.normalize].
 */
internal actual fun deaccentNfkd(raw: String): String =
    (raw as NSString).stringByFoldingWithOptions(
        options = NSDiacriticInsensitiveSearch,
        locale = null,
    )
