package com.deckpuller.data.remote

import com.deckpuller.data.remote.dto.ScryfallCollectionRequest
import com.deckpuller.data.remote.dto.ScryfallCollectionResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

class ScryfallApi(private val client: HttpClient) {
    suspend fun getCollection(request: ScryfallCollectionRequest): ScryfallCollectionResponse =
        client.post("https://api.scryfall.com/cards/collection") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
}
