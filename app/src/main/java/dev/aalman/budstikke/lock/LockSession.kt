package dev.aalman.budstikke.lock

import android.app.Activity
import android.app.Application
import android.app.KeyguardManager
import android.content.Context
import android.hardware.biometrics.BiometricManager.Authenticators
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether locked text may show. One confirmation of the screen lock opens every locked
 * conversation and message until the app leaves the screen: when the last of its activities
 * stops, other than for a rotation or similar, everything locks again.
 */
object LockSession {

    private val state = MutableStateFlow(false)
    val unlocked: StateFlow<Boolean> = state.asStateFlow()

    /** Watches the app's activities, so leaving the app locks again. Call once, at start. */
    fun install(app: Application) {
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            private var started = 0
            override fun onActivityStarted(activity: Activity) {
                started++
            }
            override fun onActivityStopped(activity: Activity) {
                started--
                if (started <= 0 && !activity.isChangingConfigurations) state.value = false
            }
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }

    /** Whether the phone has a screen lock, without which nothing can be locked. */
    fun canLock(context: Context): Boolean =
        context.getSystemService(KeyguardManager::class.java).isDeviceSecure

    /**
     * Asks for a fingerprint, face or the phone's PIN, pattern or password, and runs
     * [onSuccess] once it is confirmed. Already unlocked, it runs at once. A phone whose
     * screen lock was removed since has nothing to confirm with, so it opens straight away:
     * the lock could only ever be as strong as the phone's own.
     */
    fun unlock(activity: Activity, title: String, onSuccess: () -> Unit) {
        if (state.value || !canLock(activity)) {
            state.value = true
            onSuccess()
            return
        }
        val builder = BiometricPrompt.Builder(activity).setTitle(title)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setAllowedAuthenticators(Authenticators.BIOMETRIC_STRONG or Authenticators.DEVICE_CREDENTIAL)
        } else {
            @Suppress("DEPRECATION")
            builder.setDeviceCredentialAllowed(true)
        }
        builder.build().authenticate(
            CancellationSignal(),
            activity.mainExecutor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    state.value = true
                    onSuccess()
                }
            },
        )
    }

    /** Hides locked text again at once. */
    fun lock() {
        state.value = false
    }
}
