package com.moviles.ark.data.remote

import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * Utility for handling Firebase Crashlytics non-fatal exception logging with custom keys
 * and verifying fatal crashes.
 */
object CrashlyticsHelper {

    /**
     * Logs a non-fatal exception to Firebase Crashlytics with structured custom keys.
     */
    fun logNonFatal(
        componentName: String,
        action: String,
        throwable: Throwable,
        extraKeys: Map<String, Any> = emptyMap()
    ) {
        val crashlytics = FirebaseCrashlytics.getInstance()
        crashlytics.setCustomKey("component", componentName)
        crashlytics.setCustomKey("action", action)
        crashlytics.setCustomKey("error_message", throwable.message ?: "No error message")

        extraKeys.forEach { (key, value) ->
            when (value) {
                is String -> crashlytics.setCustomKey(key, value)
                is Boolean -> crashlytics.setCustomKey(key, value)
                is Int -> crashlytics.setCustomKey(key, value)
                is Long -> crashlytics.setCustomKey(key, value)
                is Double -> crashlytics.setCustomKey(key, value)
                is Float -> crashlytics.setCustomKey(key, value)
                else -> crashlytics.setCustomKey(key, value.toString())
            }
        }

        crashlytics.recordException(throwable)
    }

    /**
     * Simulates a fatal crash for verification of automatic Crashlytics fatal crash reporting.
     * WARNING: Calling this will crash the app process.
     */
    fun triggerTestFatalCrash(): Nothing {
        val crashlytics = FirebaseCrashlytics.getInstance()
        crashlytics.setCustomKey("test_type", "fatal_crash_verification")
        crashlytics.log("Simulating fatal crash for Firebase Crashlytics verification")
        throw RuntimeException("Test Fatal Crash for Firebase Crashlytics Verification")
    }
}
