package com.deckpuller.data.remote

import com.deckpuller.data.remote.dto.GitHubReleaseDto
import retrofit2.http.GET
import retrofit2.http.Path

interface GitHubApi {
    /** Latest published (non-draft, non-prerelease) release for a repo. */
    @GET("repos/{owner}/{repo}/releases/latest")
    suspend fun latestRelease(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
    ): GitHubReleaseDto
}
