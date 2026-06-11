package com.deckpuller.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.deckpuller.domain.VersionComparator
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** A newer release available on GitHub. */
data class UpdateInfo(
    val versionName: String,
    val apkUrl: String,
    val apkSizeBytes: Long,
    val notes: String,
)

/**
 * Self-update against the project's GitHub Releases: checks the latest release,
 * downloads its APK, and hands it to the system package installer.
 *
 * Updates only install over an existing app signed with the same key, so this is
 * for release-signed builds distributed via GitHub (see docs/AUTO_UPDATE.md).
 */
@Singleton
class UpdateManager @Inject constructor(
    private val okHttpClient: OkHttpClient,
    @ApplicationContext private val context: Context,
) {
    // A client that surfaces (rather than follows) GitHub's redirect, so we can read the
    // latest tag from the Location header.
    private val redirectReader: OkHttpClient by lazy {
        okHttpClient.newBuilder().followRedirects(false).build()
    }

    val currentVersionName: String
        get() = runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "0"

    /**
     * Returns info about a newer release, or null if up to date / unavailable.
     *
     * GitHub's REST API (api.github.com) allows only 60 unauthenticated requests per
     * hour *per source IP*. On carrier-grade NAT many phones share one address, so that
     * budget is routinely exhausted and the API returns HTTP 403. We instead hit the
     * releases/latest *web* endpoint, which isn't rate-limited and 302-redirects to
     * .../releases/tag/<tag>; the tag gives us the version and the deterministic APK URL.
     */
    suspend fun checkForUpdate(): UpdateInfo? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://github.com/$OWNER/$REPO/releases/latest")
            .build()
        val tag = redirectReader.newCall(request).execute().use { response ->
            response.header("Location")
                ?.substringAfterLast("/tag/", "")
                ?.takeIf { it.isNotBlank() }
        } ?: return@withContext null

        if (!VersionComparator.isNewer(tag, currentVersionName)) return@withContext null
        val version = tag.removePrefix("v")
        UpdateInfo(
            versionName = version,
            // CI publishes the APK under a stable name (see .github/workflows release job).
            apkUrl = "https://github.com/$OWNER/$REPO/releases/download/$tag/DeckPuller-$version.apk",
            apkSizeBytes = 0L, // Unknown ahead of time; download reports progress via Content-Length.
            notes = "",
        )
    }

    /** Streams the APK to cacheDir/updates, reporting 0f..1f progress. */
    suspend fun downloadApk(info: UpdateInfo, onProgress: (Float) -> Unit): File =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val out = File(dir, "DeckPuller-${info.versionName}.apk")

            val request = Request.Builder().url(info.apkUrl).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Download failed: HTTP ${response.code}")
                val body = response.body ?: throw IOException("Empty response body")
                val total = if (info.apkSizeBytes > 0) info.apkSizeBytes else body.contentLength()
                body.byteStream().use { input ->
                    out.outputStream().use { output ->
                        val buffer = ByteArray(8 * 1024)
                        var downloaded = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            if (total > 0) onProgress((downloaded.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
            }
            out
        }

    /** Launches the system installer for a downloaded APK. */
    fun installApk(file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }

    /** Whether the app may install packages (Android 8+ requires user opt-in). */
    fun canInstallPackages(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            context.packageManager.canRequestPackageInstalls()

    /** Opens the system screen to grant "install unknown apps" for this app. */
    fun openInstallPermissionSettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    companion object {
        const val OWNER = "tahuffman1s"
        const val REPO = "Deckpuller"
    }
}
