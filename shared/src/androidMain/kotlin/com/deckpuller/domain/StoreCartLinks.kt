package com.deckpuller.domain

import java.net.URLEncoder

/** Builds bulk-add web links and clipboard text for buying a list of cards. */
object StoreCartLinks {

    data class BuyItem(val name: String, val quantity: Int)

    /** Conservative URL length budget; beyond this we drop the pre-filled list. */
    private const val MAX_URL_LENGTH = 6000

    fun clipboardText(items: List<BuyItem>): String =
        items.joinToString("\n") { "${it.quantity} ${it.name}" }

    fun tcgPlayerUrl(items: List<BuyItem>): String {
        val base = "https://www.tcgplayer.com/massentry?productline=Magic"
        val list = items.joinToString("||") { "${it.quantity} ${it.name}" }
        val url = base + "&c=" + encode(list)
        return if (url.length <= MAX_URL_LENGTH) url else base
    }

    fun cardKingdomUrl(items: List<BuyItem>): String {
        // Card Kingdom's pre-fill mechanism is unconfirmed (GET vs POST). We open the
        // builder; the screen also copies the list to the clipboard as a guaranteed fallback.
        val base = "https://www.cardkingdom.com/builder"
        val list = items.joinToString("||") { "${it.quantity} ${it.name}" }
        val url = base + "?c=" + encode(list)
        return if (url.length <= MAX_URL_LENGTH) url else base
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}
