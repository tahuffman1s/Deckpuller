package com.deckpuller.platform

/** Current wall-clock time in epoch milliseconds (the multiplatform `System.currentTimeMillis`). */
expect fun nowMillis(): Long

/** Formats an epoch-millis instant as a localized medium-date / short-time string for display. */
expect fun formatImportDate(epochMillis: Long): String
