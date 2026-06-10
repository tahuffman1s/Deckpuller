package com.deckpuller.data.image

import android.content.Context
import coil.ImageLoader
import coil.request.ImageRequest
import javax.inject.Inject

/** Warms an image cache so cards display offline after import. */
interface ImagePrefetcher {
    fun prefetch(urls: List<String>)
}

class CoilImagePrefetcher @Inject constructor(
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
