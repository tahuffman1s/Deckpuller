package com.deckpuller.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ScryfallCollectionRequest(
    val identifiers: List<ScryfallIdentifier>,
)

@Serializable
data class ScryfallIdentifier(
    val id: String,
)

@Serializable
data class ScryfallCollectionResponse(
    val data: List<ScryfallCardDto> = emptyList(),
    @SerialName("not_found") val notFound: List<ScryfallIdentifier> = emptyList(),
)

@Serializable
data class ScryfallCardDto(
    val id: String,
    val name: String,
    @SerialName("type_line") val typeLine: String? = null,
    @SerialName("color_identity") val colorIdentity: List<String> = emptyList(),
    @SerialName("image_uris") val imageUris: ScryfallImageUris? = null,
    @SerialName("card_faces") val cardFaces: List<ScryfallCardFaceDto> = emptyList(),
) {
    /** Top-level type line, falling back to the front face for double-faced cards. */
    fun bestTypeLine(): String? = typeLine ?: cardFaces.firstOrNull()?.typeLine

    /** Normal image, falling back to the front face for double-faced cards. */
    fun bestImageUrl(): String? =
        imageUris?.normal ?: cardFaces.firstOrNull()?.imageUris?.normal
}

@Serializable
data class ScryfallCardFaceDto(
    @SerialName("type_line") val typeLine: String? = null,
    @SerialName("image_uris") val imageUris: ScryfallImageUris? = null,
)

@Serializable
data class ScryfallImageUris(
    val small: String? = null,
    val normal: String? = null,
)
