package com.deckpuller.data.remote

import com.deckpuller.data.remote.dto.ArchidektDeckDto
import com.deckpuller.data.remote.dto.ArchidektDeckListDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter

class ArchidektApi(private val client: HttpClient) {
    suspend fun getDeck(deckId: String): ArchidektDeckDto =
        client.get("https://archidekt.com/api/decks/$deckId/").body()

    // Archidekt's `owner` query param expects a numeric user id; filtering by
    // username requires `ownerUsername` (an unknown `owner` is ignored and the
    // API returns the global recent-decks feed). See decks/v3/ probing.
    suspend fun searchByOwner(
        username: String,
        orderBy: String = "-updatedAt",
        pageSize: Int = 50,
    ): ArchidektDeckListDto =
        client.get("https://archidekt.com/api/decks/v3/") {
            parameter("ownerUsername", username)
            parameter("orderBy", orderBy)
            parameter("pageSize", pageSize)
        }.body()
}
