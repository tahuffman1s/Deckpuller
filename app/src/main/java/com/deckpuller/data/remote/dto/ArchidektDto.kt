package com.deckpuller.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class ArchidektDeckDto(
    val name: String,
    val cards: List<ArchidektCardDto>,
)

@Serializable
data class ArchidektCardDto(
    val quantity: Int,
    val card: ArchidektCardDetailDto,
)

@Serializable
data class ArchidektCardDetailDto(
    val uid: String,
    val oracleCard: ArchidektOracleCardDto,
)

@Serializable
data class ArchidektOracleCardDto(
    val name: String,
)

@Serializable
data class ArchidektDeckListDto(
    val results: List<ArchidektDeckSummaryDto> = emptyList(),
)

@Serializable
data class ArchidektDeckSummaryDto(
    val id: Long,
    val name: String,
    val size: Int = 0,
    val featured: String = "",
)
