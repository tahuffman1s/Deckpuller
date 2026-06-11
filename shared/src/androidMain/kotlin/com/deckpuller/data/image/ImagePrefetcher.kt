package com.deckpuller.data.image

import android.content.Context
import coil.ImageLoader
import coil.request.ImageRequest

/** Warms an image cache so cards display offline after import. */
fun interface ImagePrefetcher {
    fun prefetch(urls: List<String>)
}

class CoilImagePrefetcher(
    private val context: Context,
    private val imageLoader: ImageLoader,
) : ImagePrefetcher {
    override fun prefetch(urls: List<String>) {
        urls.forEach { url ->
            imageLoader.enqueue(
                ImageRequest.Builder(context)
                    .data(url)
                    .build(),
            )
        }
    }
}
