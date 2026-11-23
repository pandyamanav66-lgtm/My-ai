package com.example.data

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R

object UpdateNotificationHelper {
    const val EXTRA_OPEN_UPDATE = "extra_open_update_sheet"
    // Fresh v2 channel ID ensures IMPORTANCE_HIGH takes effect on OEM devices (ColorOS/Realme/MIUI)
    private const val CHANNEL_ID = "study_tracker_updates_v2"
    private const val NOTIFICATION_ID = 4102
    private const val DOWNLOAD_ACTION_REQ_CODE = 4103
    private const val PREFS_NAME = "drst_update_prefs"
    private const val KEY_LAST_NOTIFIED_BUILD = "last_notified_build"
    private const val KEY_GITHUB_TOKEN = "github_read_token"
    private const val KEY_GITHUB_REPO = "github_repo_slug"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "App Updates",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Alerts when a new Study Tracker update is ready to download."
                    enableVibration(true)
                    setShowBadge(true)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    fun areNotificationsAllowed(context: Context): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        }
        return true
    }

    fun getSavedGitHubToken(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_GITHUB_TOKEN, "").orEmpty().trim()
    }

    fun saveGitHubToken(context: Context, token: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_GITHUB_TOKEN, token.trim()).apply()
    }

    fun getSavedRepoSlug(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_GITHUB_REPO, "").orEmpty().trim()
        val defaultSlug = "${AppUpdateService.DEFAULT_REPO_OWNER}/${AppUpdateService.DEFAULT_REPO_NAME}"
        if (saved.isBlank() || saved.equals("pandyamanav66-lgtm/my-tracker", ignoreCase = true)) {
            if (saved.isNotBlank()) {
                prefs.edit().putString(KEY_GITHUB_REPO, defaultSlug).apply()
            }
            return defaultSlug
        }
        return saved
    }

    fun saveRepoSlug(context: Context, repoSlug: String) {
        val clean = repoSlug.trim()
            .removePrefix("https://github.com/")
            .removePrefix("http://github.com/")
            .removePrefix("github.com/")
            .trim('/')
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_GITHUB_REPO, clean).apply()
    }

    fun notifyUpdateAvailable(
        context: Context,
        update: UpdateCheckState.UpdateAvailable,
        forceNotify: Boolean = false
    ): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastNotified = prefs.getInt(KEY_LAST_NOTIFIED_BUILD, -1)
        if (!forceNotify && lastNotified >= update.latestBuildCode) return false

        if (!areNotificationsAllowed(context)) return false

        ensureChannel(context)

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_UPDATE, true)
        }
        val pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pendingIntent = PendingIntent.getActivity(context, NOTIFICATION_ID, openIntent, pendingFlags)

        val downloadIntent = Intent(Intent.ACTION_VIEW, Uri.parse(update.apkDownloadUrl)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val downloadPendingIntent = PendingIntent.getActivity(
            context,
            DOWNLOAD_ACTION_REQ_CODE,
            downloadIntent,
            pendingFlags
        )

        val sizePart = if (update.apkSizeLabel.isNotBlank()) " (${update.apkSizeLabel})" else ""
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_update)
            .setContentTitle("New Update: ${update.latestVersionName}")
            .setContentText("Build #${update.latestBuildCode}$sizePart is ready — tap to update.")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "Study Tracker ${update.latestVersionName} (Build #${update.latestBuildCode}$sizePart) is ready. Tap to download & install over your current app — your study data stays safe."
                )
            )
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.ic_stat_update, "Download APK", downloadPendingIntent)
            .build()

        return runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
            prefs.edit().putInt(KEY_LAST_NOTIFIED_BUILD, update.latestBuildCode).apply()
            true
        }.getOrDefault(false)
    }
}
