package com.deckpuller.data.remote

import com.deckpuller.data.remote.dto.ArchidektDeckDto
import com.deckpuller.data.remote.dto.ArchidektDeckListDto
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface ArchidektApi {
    @GET("decks/{id}/")
    suspend fun getDeck(@Path("id") deckId: String): ArchidektDeckDto

    @GET("decks/v3/")
    suspend fun searchByOwner(
        @Query("owner") owner: String,
        @Query("ownerexact") exact: Boolean = true,
        @Query("orderBy") orderBy: String = "-updatedAt",
        @Query("pageSize") pageSize: Int = 50,
    ): ArchidektDeckListDto
}
