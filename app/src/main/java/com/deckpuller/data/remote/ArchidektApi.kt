package com.deckpuller.data.remote

import com.deckpuller.data.remote.dto.ArchidektDeckDto
import com.deckpuller.data.remote.dto.ArchidektDeckListDto
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface ArchidektApi {
    @GET("decks/{id}/")
    suspend fun getDeck(@Path("id") deckId: String): ArchidektDeckDto

    // Archidekt's `owner` query param expects a numeric user id; filtering by
    // username requires `ownerUsername` (an unknown `owner` is ignored and the
    // API returns the global recent-decks feed). See decks/v3/ probing.
    @GET("decks/v3/")
    suspend fun searchByOwner(
        @Query("ownerUsername") username: String,
        @Query("orderBy") orderBy: String = "-updatedAt",
        @Query("pageSize") pageSize: Int = 50,
    ): ArchidektDeckListDto
}
