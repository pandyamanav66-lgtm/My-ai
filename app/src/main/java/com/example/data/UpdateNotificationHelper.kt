package com.example.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R

object UpdateNotificationHelper {
    const val EXTRA_OPEN_UPDATE = "extra_open_update_sheet"
    private const val CHANNEL_ID = "study_tracker_updates"
    private const val NOTIFICATION_ID = 4102
    private const val PREFS_NAME = "drst_update_prefs"
    private const val KEY_LAST_NOTIFIED_BUILD = "last_notified_build"
    private const val KEY_GITHUB_TOKEN = "github_read_token"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "App Updates",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Alerts when a new Study Tracker update is ready to download."
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    fun getSavedGitHubToken(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_GITHUB_TOKEN, "").orEmpty().trim()
    }

    fun saveGitHubToken(context: Context, token: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_GITHUB_TOKEN, token.trim()).apply()
    }

    fun notifyUpdateAvailable(
        context: Context,
        update: UpdateCheckState.UpdateAvailable,
        forceNotify: Boolean = false
    ) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastNotified = prefs.getInt(KEY_LAST_NOTIFIED_BUILD, -1)
        if (!forceNotify && lastNotified >= update.latestBuildCode) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        ensureChannel(context)

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_UPDATE, true)
        }
        val pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pendingIntent = PendingIntent.getActivity(context, NOTIFICATION_ID, openIntent, pendingFlags)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("New Update Available: ${update.latestVersionName}")
            .setContentText("Build #${update.latestBuildCode} is ready. Tap to download & update without deleting your data.")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "Study Tracker ${update.latestVersionName} (Build #${update.latestBuildCode}) is ready. Tap to open Study Tracker and download the update directly — all your study data stays safe."
                )
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
            prefs.edit().putInt(KEY_LAST_NOTIFIED_BUILD, update.latestBuildCode).apply()
        }
    }
}
