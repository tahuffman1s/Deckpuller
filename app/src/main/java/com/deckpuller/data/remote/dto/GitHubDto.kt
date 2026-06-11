package com.deckpuller.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GitHubReleaseDto(
    @SerialName("tag_name") val tagName: String,
    val name: String? = null,
    val body: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<GitHubAssetDto> = emptyList(),
) {
    /** The first installable APK asset, if any. */
    fun apkAsset(): GitHubAssetDto? = assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
}

@Serializable
data class GitHubAssetDto(
    val name: String,
    @SerialName("browser_download_url") val downloadUrl: String,
    val size: Long = 0,
)
