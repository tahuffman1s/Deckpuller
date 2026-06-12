package com.deckpuller.platform

import java.text.DateFormat
import java.util.Date

actual fun nowMillis(): Long = System.currentTimeMillis()

actual fun formatImportDate(epochMillis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(epochMillis))
