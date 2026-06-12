package com.deckpuller.data.update

/** A newer release available on GitHub. Platform-agnostic; the install path is Android-only. */
data class UpdateInfo(
    val versionName: String,
    val apkUrl: String,
    val apkSizeBytes: Long,
    val notes: String,
)
