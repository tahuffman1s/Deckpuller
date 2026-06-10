package com.deckpuller.data.remote

import com.deckpuller.data.remote.dto.ScryfallCollectionRequest
import com.deckpuller.data.remote.dto.ScryfallCollectionResponse
import retrofit2.http.Body
import retrofit2.http.POST

fun interface ScryfallApi {
    @POST("cards/collection")
    suspend fun getCollection(
        @Body request: ScryfallCollectionRequest,
    ): ScryfallCollectionResponse
}
