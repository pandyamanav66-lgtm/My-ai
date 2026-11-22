package com.example.data

import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

sealed interface UpdateCheckState {
    data object Idle : UpdateCheckState
    data object Checking : UpdateCheckState

    data class UpToDate(
        val currentVersionName: String,
        val currentVersionCode: Int,
        val latestVersionLabel: String,
        val releasePageUrl: String
    ) : UpdateCheckState

    data class UpdateAvailable(
        val currentVersionName: String,
        val currentVersionCode: Int,
        val latestVersionName: String,
        val latestBuildCode: Int,
        val apkDownloadUrl: String,
        val releasePageUrl: String,
        val apkSizeLabel: String,
        val publishedDate: String
    ) : UpdateCheckState

    data class Error(
        val message: String,
        val fallbackReleasesUrl: String
    ) : UpdateCheckState
}

class AppUpdateService(
    private val repoOwner: String = DEFAULT_REPO_OWNER,
    private val repoName: String = DEFAULT_REPO_NAME
) {
    val releasesPageUrl: String
        get() = "https://github.com/$repoOwner/$repoName/releases"

    private val repoApiUrl: String
        get() = "https://api.github.com/repos/$repoOwner/$repoName"

    private val latestReleaseApiUrl: String
        get() = "$repoApiUrl/releases/latest"

    private val releasesListApiUrl: String
        get() = "$repoApiUrl/releases?per_page=5"

    private fun fetchUrl(urlStr: String, currentVersionName: String): Pair<Int, String> {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 12_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "StudyTracker-Android/$currentVersionName")
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            code to body
        } finally {
            conn?.disconnect()
        }
    }

    suspend fun checkLatestRelease(
        currentVersionCode: Int = BuildConfig.VERSION_CODE,
        currentVersionName: String = BuildConfig.VERSION_NAME
    ): UpdateCheckState = withContext(Dispatchers.IO) {
        try {
            val (code, body) = fetchUrl(latestReleaseApiUrl, currentVersionName)
            when {
                code == HttpURLConnection.HTTP_OK -> {
                    parseReleaseJson(
                        jsonBody = body,
                        currentVersionCode = currentVersionCode,
                        currentVersionName = currentVersionName
                    )
                }

                code == HttpURLConnection.HTTP_NOT_FOUND -> {
                    // Fallback 1: check /releases list (catches pre-releases or non-'latest' releases)
                    val (listCode, listBody) = fetchUrl(releasesListApiUrl, currentVersionName)
                    if (listCode == HttpURLConnection.HTTP_OK) {
                        val parsed = parseReleasesArrayJson(
                            jsonArrayBody = listBody,
                            currentVersionCode = currentVersionCode,
                            currentVersionName = currentVersionName
                        )
                        if (parsed != null) return@withContext parsed
                    }

                    // Fallback 2: check if the repository itself is Private (returns 404 to unauthenticated API)
                    val (repoCode, _) = fetchUrl(repoApiUrl, currentVersionName)
                    if (repoCode == HttpURLConnection.HTTP_NOT_FOUND) {
                        UpdateCheckState.Error(
                            message = "Your GitHub repo is Private, so automatic checking is blocked. Tap 'Releases' below to download the latest APK directly from GitHub, or make the repo Public for 1-tap auto-checks.",
                            fallbackReleasesUrl = releasesPageUrl
                        )
                    } else {
                        UpdateCheckState.Error(
                            message = "No published GitHub Release found yet. Push an update & wait 2–3 mins, or tap 'Releases' below to download directly.",
                            fallbackReleasesUrl = releasesPageUrl
                        )
                    }
                }

                code == 403 || code == 429 -> {
                    UpdateCheckState.Error(
                        message = "GitHub rate limit reached. Tap 'Releases' below to open the Releases page directly.",
                        fallbackReleasesUrl = releasesPageUrl
                    )
                }

                else -> {
                    UpdateCheckState.Error(
                        message = "Could not check updates right now (HTTP $code).",
                        fallbackReleasesUrl = releasesPageUrl
                    )
                }
            }
        } catch (e: Exception) {
            UpdateCheckState.Error(
                message = "Unable to reach GitHub. Check your internet connection and retry.",
                fallbackReleasesUrl = releasesPageUrl
            )
        }
    }

    internal fun parseReleasesArrayJson(
        jsonArrayBody: String,
        currentVersionCode: Int,
        currentVersionName: String
    ): UpdateCheckState? {
        val arr = JSONArray(jsonArrayBody)
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            if (obj.optBoolean("draft", false)) continue
            return parseReleaseObject(obj, currentVersionCode, currentVersionName)
        }
        return null
    }

    internal fun parseReleaseJson(
        jsonBody: String,
        currentVersionCode: Int,
        currentVersionName: String
    ): UpdateCheckState = parseReleaseObject(JSONObject(jsonBody), currentVersionCode, currentVersionName)

    private fun parseReleaseObject(
        json: JSONObject,
        currentVersionCode: Int,
        currentVersionName: String
    ): UpdateCheckState {
        val tagName = json.optString("tag_name", "").trim()
        val releaseName = json.optString("name", "").trim()
        val htmlUrl = json.optString("html_url", releasesPageUrl).ifBlank { releasesPageUrl }
        val publishedRaw = json.optString("published_at", "")
        val publishedDate = if (publishedRaw.length >= 10) publishedRaw.substring(0, 10) else ""

        val remoteBuildCode = extractBuildNumber(tagName, releaseName)
        val latestVersionLabel = when {
            releaseName.isNotBlank() -> releaseName
            remoteBuildCode != null -> "v1.0.$remoteBuildCode"
            tagName.isNotBlank() -> tagName
            else -> "Latest Release"
        }

        var apkDownloadUrl: String? = null
        var apkSizeBytes: Long = -1L
        val assets = json.optJSONArray("assets")
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val asset = assets.optJSONObject(i) ?: continue
                val assetName = asset.optString("name", "")
                val downloadUrl = asset.optString("browser_download_url", "")
                if (assetName.endsWith(".apk", ignoreCase = true) && downloadUrl.isNotBlank()) {
                    apkDownloadUrl = downloadUrl
                    apkSizeBytes = asset.optLong("size", -1L)
                    break
                }
            }
        }

        if (remoteBuildCode != null && remoteBuildCode > currentVersionCode) {
            return UpdateCheckState.UpdateAvailable(
                currentVersionName = currentVersionName,
                currentVersionCode = currentVersionCode,
                latestVersionName = latestVersionLabel,
                latestBuildCode = remoteBuildCode,
                apkDownloadUrl = apkDownloadUrl ?: htmlUrl,
                releasePageUrl = htmlUrl,
                apkSizeLabel = formatBytes(apkSizeBytes),
                publishedDate = publishedDate
            )
        }

        return UpdateCheckState.UpToDate(
            currentVersionName = currentVersionName,
            currentVersionCode = currentVersionCode,
            latestVersionLabel = latestVersionLabel,
            releasePageUrl = htmlUrl
        )
    }

    internal fun extractBuildNumber(tagName: String, releaseName: String): Int? {
        // Matches tags like "debug-apk-build-14-1" or "build-14"
        val tagRegex = Regex("""build-(\d+)""", RegexOption.IGNORE_CASE)
        tagRegex.find(tagName)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { return it }

        // Matches titles like "Study Tracker v1.0.14" or "v1.0.14"
        val semverRegex = Regex("""v\d+\.\d+\.(\d+)""", RegexOption.IGNORE_CASE)
        semverRegex.find(releaseName)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { return it }
        semverRegex.find(tagName)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { return it }

        // Matches titles like "Debug APK Build #14"
        val hashRegex = Regex("""#(\d+)""")
        hashRegex.find(releaseName)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { return it }

        return null
    }

    internal fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return ""
        val mb = bytes / (1024.0 * 1024.0)
        return String.format(Locale.US, "%.1f MB", mb)
    }

    companion object {
        const val DEFAULT_REPO_OWNER = "pandyamanav66-lgtm"
        const val DEFAULT_REPO_NAME = "my-tracker"
    }
}
