package com.deckpuller.data

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Reads the text content of a user-picked / shared CSV Uri. */
@Singleton
class CollectionImporter @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** @throws java.io.IOException if the Uri can't be opened. */
    fun readText(uri: Uri): String =
        context.contentResolver.openInputStream(uri)?.use { stream ->
            stream.bufferedReader().readText()
        } ?: throw java.io.IOException("Could not open $uri")
}
