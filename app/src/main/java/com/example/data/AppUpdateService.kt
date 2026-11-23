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
        val releasePageUrl: String,
        val latestBuildCode: Int? = null,
        val repoSlug: String = "${AppUpdateService.DEFAULT_REPO_OWNER}/${AppUpdateService.DEFAULT_REPO_NAME}"
    ) : UpdateCheckState

    data class UpdateAvailable(
        val currentVersionName: String,
        val currentVersionCode: Int,
        val latestVersionName: String,
        val latestBuildCode: Int,
        val apkDownloadUrl: String,
        val releasePageUrl: String,
        val apkSizeLabel: String,
        val publishedDate: String,
        val repoSlug: String = "${AppUpdateService.DEFAULT_REPO_OWNER}/${AppUpdateService.DEFAULT_REPO_NAME}"
    ) : UpdateCheckState

    data class Error(
        val message: String,
        val fallbackReleasesUrl: String
    ) : UpdateCheckState
}

class AppUpdateService(
    private val defaultRepoOwner: String = DEFAULT_REPO_OWNER,
    private val defaultRepoName: String = DEFAULT_REPO_NAME
) {
    val defaultRepoSlug: String
        get() = "$defaultRepoOwner/$defaultRepoName"

    fun parseRepoSlug(repoSlug: String?): Pair<String, String> {
        val cleaned = repoSlug.orEmpty()
            .trim()
            .removePrefix("https://github.com/")
            .removePrefix("http://github.com/")
            .removePrefix("github.com/")
            .trim('/')
        val parts = cleaned.split('/').filter { it.isNotBlank() }
        return if (parts.size >= 2) {
            parts[0] to parts[1].removeSuffix(".git")
        } else {
            defaultRepoOwner to defaultRepoName
        }
    }

    fun releasesPageUrlFor(repoSlug: String? = null): String {
        val (owner, repo) = parseRepoSlug(repoSlug)
        return "https://github.com/$owner/$repo/releases"
    }

    private fun fetchUrl(
        urlStr: String,
        currentVersionName: String,
        authToken: String? = null
    ): Pair<Int, String> {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                useCaches = false
                instanceFollowRedirects = true
                connectTimeout = 10_000
                readTimeout = 12_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                setRequestProperty("Cache-Control", "no-cache, no-store")
                setRequestProperty("Pragma", "no-cache")
                setRequestProperty("User-Agent", "StudyTracker-Android/$currentVersionName")
                if (!authToken.isNullOrBlank()) {
                    setRequestProperty("Authorization", "Bearer ${authToken.trim()}")
                }
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
        currentVersionName: String = BuildConfig.VERSION_NAME,
        authToken: String? = null,
        repoSlugOverride: String? = null
    ): UpdateCheckState = withContext(Dispatchers.IO) {
        val (owner, repo) = parseRepoSlug(repoSlugOverride)
        val slug = "$owner/$repo"
        val releasesPageUrl = "https://github.com/$slug/releases"
        val repoApiUrl = "https://api.github.com/repos/$slug"
        val releasesListApiUrl = "$repoApiUrl/releases?per_page=10"
        val latestReleaseApiUrl = "$repoApiUrl/releases/latest"

        try {
            val cleanToken = authToken?.trim()?.takeIf { it.isNotEmpty() }

            // Query /releases?per_page=10 first so we catch latest releases, pre-releases,
            // and pick the true highest build number even if GitHub's /latest pointer lags.
            val (listCode, listBody) = fetchUrl(releasesListApiUrl, currentVersionName, cleanToken)
            when {
                listCode == HttpURLConnection.HTTP_OK -> {
                    val parsed = parseReleasesArrayJson(
                        jsonArrayBody = listBody,
                        currentVersionCode = currentVersionCode,
                        currentVersionName = currentVersionName,
                        repoSlug = slug
                    )
                    if (parsed != null) return@withContext parsed

                    // Fallback to /releases/latest just in case
                    val (latestCode, latestBody) = fetchUrl(latestReleaseApiUrl, currentVersionName, cleanToken)
                    if (latestCode == HttpURLConnection.HTTP_OK) {
                        return@withContext parseReleaseJson(
                            jsonBody = latestBody,
                            currentVersionCode = currentVersionCode,
                            currentVersionName = currentVersionName,
                            repoSlug = slug
                        )
                    }

                    UpdateCheckState.Error(
                        message = "No published GitHub Release found in $slug yet. Push an update and wait 2–3 mins for GitHub Actions to finish, or tap 'Releases'.",
                        fallbackReleasesUrl = releasesPageUrl
                    )
                }

                listCode == HttpURLConnection.HTTP_UNAUTHORIZED -> {
                    UpdateCheckState.Error(
                        message = "Saved GitHub token was rejected (401). Clear it or paste a valid read-only token.",
                        fallbackReleasesUrl = releasesPageUrl
                    )
                }

                listCode == HttpURLConnection.HTTP_NOT_FOUND -> {
                    val (repoCode, _) = fetchUrl(repoApiUrl, currentVersionName, cleanToken)
                    if (repoCode == HttpURLConnection.HTTP_NOT_FOUND) {
                        UpdateCheckState.Error(
                            message = "Repo '$slug' is Private or not found. Make it Public, verify the repo name below, or save a GitHub token.",
                            fallbackReleasesUrl = releasesPageUrl
                        )
                    } else {
                        UpdateCheckState.Error(
                            message = "No published GitHub Release found in $slug yet. Push an update & wait 2–3 mins, or tap 'Releases'.",
                            fallbackReleasesUrl = releasesPageUrl
                        )
                    }
                }

                listCode == 403 || listCode == 429 -> {
                    UpdateCheckState.Error(
                        message = "GitHub rate limit reached. Add a GitHub token below or tap 'Releases' directly.",
                        fallbackReleasesUrl = releasesPageUrl
                    )
                }

                else -> {
                    UpdateCheckState.Error(
                        message = "Could not check updates right now (HTTP $listCode).",
                        fallbackReleasesUrl = releasesPageUrl
                    )
                }
            }
        } catch (_: Exception) {
            UpdateCheckState.Error(
                message = "Unable to reach GitHub. Check your internet connection and retry.",
                fallbackReleasesUrl = releasesPageUrl
            )
        }
    }

    private data class CandidateRelease(
        val obj: JSONObject,
        val buildCode: Int?,
        val hasApk: Boolean,
        val index: Int
    )

    internal fun parseReleasesArrayJson(
        jsonArrayBody: String,
        currentVersionCode: Int,
        currentVersionName: String,
        repoSlug: String = defaultRepoSlug
    ): UpdateCheckState? {
        val arr = JSONArray(jsonArrayBody)
        val candidates = mutableListOf<CandidateRelease>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            if (obj.optBoolean("draft", false)) continue
            val tagName = obj.optString("tag_name", "").trim()
            val releaseName = obj.optString("name", "").trim()
            val apkName = findFirstApkAsset(obj)?.first.orEmpty()
            val buildCode = extractBuildNumber(tagName, releaseName, apkName)
            candidates += CandidateRelease(
                obj = obj,
                buildCode = buildCode,
                hasApk = apkName.isNotBlank(),
                index = i
            )
        }
        if (candidates.isEmpty()) return null

        // Prefer highest parsed build number, then APK presence, then newest ordering from GitHub
        val best = candidates.sortedWith(
            compareByDescending<CandidateRelease> { it.buildCode ?: Int.MIN_VALUE }
                .thenByDescending { if (it.hasApk) 1 else 0 }
                .thenBy { it.index }
        ).first()

        return parseReleaseObject(best.obj, currentVersionCode, currentVersionName, repoSlug)
    }

    internal fun parseReleaseJson(
        jsonBody: String,
        currentVersionCode: Int,
        currentVersionName: String,
        repoSlug: String = defaultRepoSlug
    ): UpdateCheckState = parseReleaseObject(JSONObject(jsonBody), currentVersionCode, currentVersionName, repoSlug)

    private fun findFirstApkAsset(json: JSONObject): Triple<String, String, Long>? {
        val assets = json.optJSONArray("assets") ?: return null
        for (i in 0 until assets.length()) {
            val asset = assets.optJSONObject(i) ?: continue
            val assetName = asset.optString("name", "")
            val downloadUrl = asset.optString("browser_download_url", "")
            if (assetName.endsWith(".apk", ignoreCase = true) && downloadUrl.isNotBlank()) {
                return Triple(assetName, downloadUrl, asset.optLong("size", -1L))
            }
        }
        return null
    }

    private fun parseReleaseObject(
        json: JSONObject,
        currentVersionCode: Int,
        currentVersionName: String,
        repoSlug: String
    ): UpdateCheckState {
        val fallbackPage = releasesPageUrlFor(repoSlug)
        val tagName = json.optString("tag_name", "").trim()
        val releaseName = json.optString("name", "").trim()
        val htmlUrl = json.optString("html_url", fallbackPage).ifBlank { fallbackPage }
        val publishedRaw = json.optString("published_at", "")
        val publishedDate = if (publishedRaw.length >= 10) publishedRaw.substring(0, 10) else ""

        val apkAsset = findFirstApkAsset(json)
        val apkName = apkAsset?.first.orEmpty()
        val apkDownloadUrl = apkAsset?.second
        val apkSizeBytes = apkAsset?.third ?: -1L

        val remoteBuildCode = extractBuildNumber(tagName, releaseName, apkName)
        val latestVersionLabel = when {
            releaseName.isNotBlank() -> releaseName
            remoteBuildCode != null -> "v1.0.$remoteBuildCode"
            tagName.isNotBlank() -> tagName
            else -> "Latest Release"
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
                publishedDate = publishedDate,
                repoSlug = repoSlug
            )
        }

        return UpdateCheckState.UpToDate(
            currentVersionName = currentVersionName,
            currentVersionCode = currentVersionCode,
            latestVersionLabel = latestVersionLabel,
            releasePageUrl = htmlUrl,
            latestBuildCode = remoteBuildCode,
            repoSlug = repoSlug
        )
    }

    internal fun extractBuildNumber(
        tagName: String,
        releaseName: String,
        apkAssetName: String = ""
    ): Int? {
        // Matches tags/assets like "debug-apk-build-14-1", "my-tracker-debug-build-14.apk", or "build-14"
        val tagRegex = Regex("""build[-_ ]?(\d+)""", RegexOption.IGNORE_CASE)
        tagRegex.find(tagName)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { return it }
        tagRegex.find(apkAssetName)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { return it }

        // Matches titles/tags like "Study Tracker v1.0.14" or "v1.0.14" or "1.0.14"
        val semverRegex = Regex("""v?\d+\.\d+\.(\d+)""", RegexOption.IGNORE_CASE)
        semverRegex.find(releaseName)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { return it }
        semverRegex.find(tagName)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { return it }
        semverRegex.find(apkAssetName)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { return it }

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
        const val DEFAULT_REPO_NAME = "my-ai"
    }
}
