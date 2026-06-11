package com.deckpuller.data.remote.dto

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GitHubDtoTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `parses latest release and picks the apk asset`() {
        val raw = """
            {
              "tag_name": "v1.2.0",
              "name": "DeckPuller v1.2.0",
              "body": "- New stuff",
              "draft": false,
              "prerelease": false,
              "assets": [
                {"name": "DeckPuller-1.2.0.aab", "browser_download_url": "https://x/a.aab", "size": 111},
                {"name": "DeckPuller-1.2.0.apk", "browser_download_url": "https://x/a.apk", "size": 222}
              ]
            }
        """.trimIndent()

        val dto = json.decodeFromString(GitHubReleaseDto.serializer(), raw)

        assertEquals("v1.2.0", dto.tagName)
        val apk = dto.apkAsset()!!
        assertEquals("DeckPuller-1.2.0.apk", apk.name)
        assertEquals("https://x/a.apk", apk.downloadUrl)
        assertEquals(222L, apk.size)
    }

    @Test
    fun `apkAsset is null when no apk is attached`() {
        val raw = """
            {"tag_name":"v1.0.0","assets":[{"name":"notes.txt","browser_download_url":"https://x/n.txt"}]}
        """.trimIndent()

        val dto = json.decodeFromString(GitHubReleaseDto.serializer(), raw)

        assertNull(dto.apkAsset())
    }
}
