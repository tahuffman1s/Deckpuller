package com.deckpuller.domain

import java.text.Normalizer

private val combiningMarks = Regex("\\p{Mn}+")

internal actual fun deaccentNfkd(raw: String): String =
    Normalizer.normalize(raw, Normalizer.Form.NFKD).replace(combiningMarks, "")
