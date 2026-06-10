package com.deckpuller.data.remote

import com.deckpuller.data.remote.dto.ArchidektDeckDto
import retrofit2.http.GET
import retrofit2.http.Path

interface ArchidektApi {
    @GET("decks/{id}/")
    suspend fun getDeck(@Path("id") deckId: String): ArchidektDeckDto
}
