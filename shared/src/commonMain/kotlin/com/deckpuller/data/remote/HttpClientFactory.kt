package com.deckpuller.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/** The platform Ktor engine: OkHttp on Android, Darwin (NSURLSession) on iOS. */
expect fun httpClientEngine(): HttpClientEngine

/** Builds the shared Ktor client (JSON content negotiation + default DeckPuller headers). */
fun buildHttpClient(json: Json): HttpClient = HttpClient(httpClientEngine()) {
    install(ContentNegotiation) { json(json) }
    install(DefaultRequest) {
        headers.append(HttpHeaders.UserAgent, "DeckPuller/1.0")
        headers.append(HttpHeaders.Accept, "application/json")
    }
}
