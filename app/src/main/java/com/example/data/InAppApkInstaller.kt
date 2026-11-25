package com.example.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.coroutines.coroutineContext

sealed interface ApkDownloadState {
    data object Idle : ApkDownloadState
    data class Downloading(
        val progressPercent: Int,
        val downloadedLabel: String,
        val totalLabel: String
    ) : ApkDownloadState

    data class ReadyToInstall(
        val apkFilePath: String,
        val buildCode: Int
    ) : ApkDownloadState

    data class Error(
        val message: String
    ) : ApkDownloadState
}

object InAppApkInstaller {

    private const val UPDATES_DIR_NAME = "updates"
    private const val MAX_REDIRECTS = 6
    private const val MIN_VALID_APK_BYTES = 100_000L

    fun canInstallPackages(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun createUnknownSourcesSettingsIntent(context: Context): Intent {
        return Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        )
    }

    fun launchSystemInstaller(context: Context, apkFile: File): Result<Unit> = runCatching {
        if (!apkFile.exists() || !isValidApkFile(apkFile)) {
            throw IOException("Downloaded update file is incomplete or corrupted. Please download again.")
        }
        val authority = "${context.packageName}.fileprovider"
        val contentUri = FileProvider.getUriForFile(context, authority, apkFile)
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(installIntent)
    }

    internal fun isValidZipMagic(bytes: ByteArray): Boolean {
        return bytes.size >= 4 &&
            bytes[0] == 0x50.toByte() && // 'P'
            bytes[1] == 0x4B.toByte() && // 'K'
            bytes[2] == 0x03.toByte() &&
            bytes[3] == 0x04.toByte()
    }

    fun isValidApkFile(file: File): Boolean {
        if (!file.exists() || file.length() < MIN_VALID_APK_BYTES) return false
        return runCatching {
            val header = ByteArray(4)
            FileInputStream(file).use { input ->
                if (input.read(header) < 4) return false
            }
            isValidZipMagic(header)
        }.getOrDefault(false)
    }

    fun formatMb(bytes: Long): String {
        if (bytes <= 0L) return ""
        return String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
    }

    suspend fun downloadApk(
        context: Context,
        downloadUrl: String,
        buildCode: Int,
        authToken: String? = null,
        onProgress: (percent: Int, downloadedBytes: Long, totalBytes: Long) -> Unit
    ): File = withContext(Dispatchers.IO) {
        if (!downloadUrl.endsWith(".apk", ignoreCase = true) && !downloadUrl.contains("/releases/download/", ignoreCase = true)) {
            throw IOException("No direct APK asset found on this release. Wait for the APK build to finish and check again.")
        }

        val updatesDir = File(context.cacheDir, UPDATES_DIR_NAME).apply { mkdirs() }
        val targetFile = File(updatesDir, "study_tracker_build_$buildCode.apk")

        // Reuse already downloaded verified APK so returning from permission settings installs instantly
        if (isValidApkFile(targetFile)) {
            val size = targetFile.length()
            onProgress(100, size, size)
            return@withContext targetFile
        }

        // Clean stale APK/temp files from earlier builds to keep cache tiny
        updatesDir.listFiles()?.forEach { old ->
            if (old.name != targetFile.name) {
                runCatching { old.delete() }
            }
        }

        val tempFile = File(updatesDir, "study_tracker_build_$buildCode.apk.tmp")
        if (tempFile.exists()) tempFile.delete()

        var currentUrl = downloadUrl.trim()
        var redirects = 0
        var conn: HttpURLConnection? = null

        try {
            while (true) {
                coroutineContext.ensureActive()
                val parsedUrl = URL(currentUrl)
                conn = (parsedUrl.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    instanceFollowRedirects = false
                    useCaches = false
                    connectTimeout = 15_000
                    readTimeout = 30_000
                    setRequestProperty("Accept", "application/octet-stream, */*")
                    setRequestProperty("User-Agent", "StudyTracker-Android-Updater")
                    val host = parsedUrl.host.lowercase(Locale.US)
                    if (!authToken.isNullOrBlank() && (host == "github.com" || host == "api.github.com")) {
                        setRequestProperty("Authorization", "Bearer ${authToken.trim()}")
                    }
                }

                val code = conn.responseCode
                if (code in setOf(301, 302, 303, 307, 308)) {
                    val location = conn.getHeaderField("Location")
                        ?: throw IOException("Update server returned redirect without Location header.")
                    conn.disconnect()
                    conn = null
                    redirects++
                    if (redirects > MAX_REDIRECTS) {
                        throw IOException("Too many redirects while downloading update APK.")
                    }
                    currentUrl = URL(parsedUrl, location).toString()
                    continue
                }

                if (code !in 200..299) {
                    throw IOException("Failed to download update APK (HTTP $code). Check internet and retry.")
                }

                val totalBytes = conn.contentLengthLong
                var downloadedBytes = 0L
                var lastReportedPct = -1

                conn.inputStream.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(16 * 1024)
                        while (true) {
                            coroutineContext.ensureActive()
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            downloadedBytes += read
                            val pct = if (totalBytes > 0L) {
                                ((downloadedBytes * 100L) / totalBytes).toInt().coerceIn(0, 100)
                            } else {
                                -1
                            }
                            if (pct != lastReportedPct || pct == -1) {
                                lastReportedPct = pct
                                onProgress(pct, downloadedBytes, totalBytes)
                            }
                        }
                        output.flush()
                    }
                }
                break
            }

            if (!isValidApkFile(tempFile)) {
                tempFile.delete()
                throw IOException("Downloaded file is not a valid Android APK. Please try again.")
            }

            if (targetFile.exists()) targetFile.delete()
            if (!tempFile.renameTo(targetFile)) {
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }

            val finalSize = targetFile.length()
            onProgress(100, finalSize, finalSize)
            targetFile
        } catch (e: Exception) {
            runCatching { tempFile.delete() }
            throw e
        } finally {
            conn?.disconnect()
        }
    }
}
