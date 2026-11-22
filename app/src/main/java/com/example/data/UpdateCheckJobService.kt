package com.example.data

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Lightweight native Android JobService that periodically checks GitHub Releases
 * every 15 minutes when internet is available — even when Study Tracker is closed —
 * and posts a high-priority heads-up notification whenever a newer build is published.
 */
class UpdateCheckJobService : JobService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var runningJob: Job? = null

    override fun onStartJob(params: JobParameters?): Boolean {
        runningJob?.cancel()
        runningJob = serviceScope.launch {
            try {
                val token = UpdateNotificationHelper.getSavedGitHubToken(applicationContext)
                val repoSlug = UpdateNotificationHelper.getSavedRepoSlug(applicationContext)
                val result = AppUpdateService().checkLatestRelease(
                    authToken = token,
                    repoSlugOverride = repoSlug
                )
                if (result is UpdateCheckState.UpdateAvailable) {
                    UpdateNotificationHelper.notifyUpdateAvailable(
                        context = applicationContext,
                        update = result,
                        forceNotify = false
                    )
                }
            } catch (_: Exception) {
                // Ignore transient offline errors; JobScheduler will retry on the next interval
            } finally {
                jobFinished(params, false)
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        runningJob?.cancel()
        return true
    }
}

object UpdateCheckScheduler {
    private const val JOB_ID = 4109
    private const val PERIODIC_INTERVAL_MS = 15L * 60L * 1000L // 15 minutes (Android minimum)

    fun schedulePeriodicCheck(context: Context) {
        runCatching {
            val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler ?: return
            val existing = scheduler.getPendingJob(JOB_ID)
            if (existing != null) return

            val component = ComponentName(context, UpdateCheckJobService::class.java)
            val jobInfo = JobInfo.Builder(JOB_ID, component)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(PERIODIC_INTERVAL_MS)
                .build()
            scheduler.schedule(jobInfo)
        }
    }
}
