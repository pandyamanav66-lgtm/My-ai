package com.example.data

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.recaptcha.RecaptchaAppCheckProviderFactory

private const val TAG = "AppCheckInit"

// App Check contract keys defined by the Firebase SDK and runtime harness.
private const val DEBUG_TOKEN_EXTRA = "FIREBASE_APPCHECK_DEBUG_TOKEN"
private const val DEBUG_STORE_PREFIX = "com.google.firebase.appcheck.debug.store."
private const val DEBUG_SECRET_KEY = "com.google.firebase.appcheck.debug.DEBUG_SECRET"
private const val RECAPTCHA_SITE_KEY_RES = "recaptcha_site_key"

/**
 * Configures Firebase App Check before any UI or Firebase AI operations.
 *
 * - In AI Studio / emulator debug launches: persists FIREBASE_APPCHECK_DEBUG_TOKEN synchronously
 *   via commit() and installs DebugAppCheckProviderFactory.
 * - On real Android devices running sideloaded debug APKs (where no harness debug token is
 *   injected via Intent and none is saved): installs RecaptchaAppCheckProviderFactory with the
 *   provisioned Android reCAPTCHA Enterprise site key so Firebase AI calls succeed instead of
 *   failing with an unregistered random debug token.
 * - In release builds: always installs RecaptchaAppCheckProviderFactory.
 */
fun configureAppCheck(context: Context, intent: Intent?) {
    try {
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context)
        }

        val appCheck = FirebaseAppCheck.getInstance()
        val recaptchaSiteKey = resolveRecaptchaSiteKey(context)

        if (BuildConfig.DEBUG) {
            val intentDebugToken = intent?.getStringExtra(DEBUG_TOKEN_EXTRA)?.trim()
            val persistenceKey = FirebaseApp.getInstance().persistenceKey
            val prefs = context.getSharedPreferences(
                "$DEBUG_STORE_PREFIX$persistenceKey",
                Context.MODE_PRIVATE
            )

            if (!intentDebugToken.isNullOrEmpty()) {
                // CRITICAL: MUST use synchronous commit() before DebugAppCheckProviderFactory reads it.
                prefs.edit()
                    .putString(DEBUG_SECRET_KEY, intentDebugToken)
                    .commit()
                appCheck.installAppCheckProviderFactory(
                    DebugAppCheckProviderFactory.getInstance()
                )
                Log.d(TAG, "App Check debug secret committed; DebugAppCheckProviderFactory installed.")
            } else {
                val persistedDebugToken = prefs.getString(DEBUG_SECRET_KEY, null)?.trim()
                if (!persistedDebugToken.isNullOrEmpty()) {
                    appCheck.installAppCheckProviderFactory(
                        DebugAppCheckProviderFactory.getInstance()
                    )
                    Log.d(TAG, "Using persisted debug secret; DebugAppCheckProviderFactory installed.")
                } else if (!recaptchaSiteKey.isNullOrBlank()) {
                    // Sideloaded debug APK on a real phone: use real reCAPTCHA Enterprise attestation
                    // instead of generating an unregistered random debug UUID that Firebase rejects.
                    appCheck.installAppCheckProviderFactory(
                        RecaptchaAppCheckProviderFactory.getInstance(recaptchaSiteKey)
                    )
                    Log.d(TAG, "No debug token provided on device; RecaptchaAppCheckProviderFactory installed.")
                } else {
                    appCheck.installAppCheckProviderFactory(
                        DebugAppCheckProviderFactory.getInstance()
                    )
                    Log.w(TAG, "No debug token or recaptcha_site_key found; falling back to DebugAppCheckProviderFactory.")
                }
            }
        } else {
            if (!recaptchaSiteKey.isNullOrBlank()) {
                appCheck.installAppCheckProviderFactory(
                    RecaptchaAppCheckProviderFactory.getInstance(recaptchaSiteKey)
                )
                Log.d(TAG, "Release RecaptchaAppCheckProviderFactory installed.")
            } else {
                Log.e(TAG, "recaptcha_site_key string resource is missing or blank in release build.")
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to initialize Firebase App Check", e)
    }
}

private fun resolveRecaptchaSiteKey(context: Context): String? {
    val resId = context.resources.getIdentifier(
        RECAPTCHA_SITE_KEY_RES,
        "string",
        context.packageName
    )
    if (resId == 0) return null
    return context.getString(resId).trim().takeIf { it.isNotEmpty() }
}
