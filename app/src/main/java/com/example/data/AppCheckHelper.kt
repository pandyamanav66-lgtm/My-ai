package com.example.data

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.recaptcha.RecaptchaAppCheckProviderFactory

private const val TAG = "AppCheckInit"

private const val DEBUG_TOKEN_EXTRA = "FIREBASE_APPCHECK_DEBUG_TOKEN"
private const val DEBUG_STORE_PREFIX = "com.google.firebase.appcheck.debug.store."
private const val DEBUG_SECRET_KEY = "com.google.firebase.appcheck.debug.DEBUG_SECRET"
private const val RECAPTCHA_SITE_KEY_RES = "recaptcha_site_key"
private const val RUNTIME_MODE_PREFS = "drst_appcheck_runtime_mode"
private const val KEY_HAS_HARNESS_TOKEN = "has_harness_debug_token"

private fun readStringResource(context: Context, name: String): String? {
    val resId = context.resources.getIdentifier(name, "string", context.packageName)
    if (resId == 0) return null
    return runCatching { context.getString(resId) }.getOrNull()?.takeIf { it.isNotBlank() }
}

private fun ensureFirebaseInitialized(appContext: Context): Boolean {
    if (FirebaseApp.getApps(appContext).isNotEmpty()) return true

    if (FirebaseApp.initializeApp(appContext) != null) return true
    if (FirebaseApp.getApps(appContext).isNotEmpty()) return true

    // Explicit fallback reading google-services generated string resources directly
    val appId = readStringResource(appContext, "google_app_id")
    val apiKey = readStringResource(appContext, "google_api_key")
    val projectId = readStringResource(appContext, "project_id")
    if (appId != null && apiKey != null) {
        val builder = FirebaseOptions.Builder()
            .setApplicationId(appId)
            .setApiKey(apiKey)
        if (projectId != null) builder.setProjectId(projectId)
        readStringResource(appContext, "gcm_defaultSenderId")?.let { builder.setGcmSenderId(it) }
        readStringResource(appContext, "google_storage_bucket")?.let { builder.setStorageBucket(it) }
        FirebaseApp.initializeApp(appContext, builder.build())
        Log.d(TAG, "FirebaseApp initialized via explicit resource fallback.")
        return FirebaseApp.getApps(appContext).isNotEmpty()
    }

    Log.e(
        TAG,
        "Default FirebaseApp could not be initialized: google_app_id/google_api_key resources missing from APK."
    )
    return false
}

private fun preWarmAppCheckToken() {
    runCatching {
        FirebaseAppCheck.getInstance()
            .getAppCheckToken(false)
            .addOnSuccessListener {
                Log.d(TAG, "App Check token pre-warmed successfully.")
            }
            .addOnFailureListener { err ->
                Log.e(TAG, "App Check token pre-warm failed: ${err.localizedMessage}", err)
            }
    }.onFailure { err ->
        Log.e(TAG, "Unable to trigger App Check pre-warm", err)
    }
}

private fun installRecaptchaProvider(appContext: Context): Boolean {
    val siteKey = readStringResource(appContext, RECAPTCHA_SITE_KEY_RES)
    if (siteKey == null) {
        Log.e(TAG, "recaptcha_site_key string resource not found or blank.")
        return false
    }
    FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
        RecaptchaAppCheckProviderFactory.getInstance(siteKey)
    )
    Log.d(TAG, "RecaptchaAppCheckProviderFactory installed.")
    return true
}

fun configureAppCheck(context: Context, intent: Intent?) {
    val appContext = context.applicationContext ?: context
    try {
        if (!ensureFirebaseInitialized(appContext)) return

        if (BuildConfig.DEBUG) {
            val debugToken = intent?.getStringExtra(DEBUG_TOKEN_EXTRA)
            val modePrefs = appContext.getSharedPreferences(RUNTIME_MODE_PREFS, Context.MODE_PRIVATE)

            if (!debugToken.isNullOrEmpty()) {
                val persistenceKey = FirebaseApp.getInstance().persistenceKey
                val prefs = appContext.getSharedPreferences(
                    "$DEBUG_STORE_PREFIX$persistenceKey",
                    Context.MODE_PRIVATE
                )
                // Synchronous commit() ensures the secret is written before provider init
                prefs.edit()
                    .putString(DEBUG_SECRET_KEY, debugToken)
                    .commit()
                modePrefs.edit()
                    .putBoolean(KEY_HAS_HARNESS_TOKEN, true)
                    .commit()
                Log.d(TAG, "App Check debug secret configured successfully.")

                FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                    DebugAppCheckProviderFactory.getInstance()
                )
                Log.d(TAG, "DebugAppCheckProviderFactory installed (harness token).")
            } else if (modePrefs.getBoolean(KEY_HAS_HARNESS_TOKEN, false)) {
                FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                    DebugAppCheckProviderFactory.getInstance()
                )
                Log.d(TAG, "DebugAppCheckProviderFactory installed (persisted harness token).")
            } else {
                // Physical device launching a sideloaded Debug APK without an injected debug token:
                // Use reCAPTCHA Enterprise App Check so Gemini AI calls pass real attestation.
                Log.i(TAG, "No harness debug token in Intent; using reCAPTCHA App Check for real-device debug build.")
                if (!installRecaptchaProvider(appContext)) {
                    FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                        DebugAppCheckProviderFactory.getInstance()
                    )
                    Log.w(TAG, "Fallback: DebugAppCheckProviderFactory installed without token.")
                }
            }
        } else {
            installRecaptchaProvider(appContext)
        }

        // Pre-warm App Check token during splash screen so first AI request responds fast
        preWarmAppCheckToken()
    } catch (e: Exception) {
        Log.e(TAG, "Failed to initialize Firebase App Check", e)
    }
}
