package com.habit1.app.platform.security

import android.app.KeyguardManager
import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Interface managing biometric and device credential authentication (Decision 7.1).
 */
interface BiometricAuthManager {
    /**
     * Checks if biometric or device credential authentication is available on this device.
     * Returns [BiometricManager.BIOMETRIC_SUCCESS] or an error code.
     */
    fun canAuthenticate(): Int

    /**
     * Checks if the device has a secure lock screen configured (PIN, Pattern, or Password).
     */
    fun isDeviceSecure(): Boolean

    /**
     * Triggers the system biometric / device credential authentication prompt.
     */
    fun authenticate(
        activity: FragmentActivity,
        title: String = "Unlock Habit1",
        subtitle: String = "Verify your identity to access your habits",
        onSuccess: () -> Unit,
        onError: (errorCode: Int, errString: CharSequence) -> Unit = { _, _ -> },
        onFailed: () -> Unit = {}
    )
}

/**
 * Production implementation of [BiometricAuthManager] using AndroidX Biometric.
 */
class BiometricAuthManagerImpl(
    private val context: Context
) : BiometricAuthManager {

    private val biometricManager = BiometricManager.from(context)
    private val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager

    override fun canAuthenticate(): Int {
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        return biometricManager.canAuthenticate(authenticators)
    }

    override fun isDeviceSecure(): Boolean {
        return keyguardManager?.isDeviceSecure == true
    }

    override fun authenticate(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        onSuccess: () -> Unit,
        onError: (errorCode: Int, errString: CharSequence) -> Unit,
        onFailed: () -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                onError(errorCode, errString)
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                onFailed()
            }
        }

        val biometricPrompt = BiometricPrompt(activity, executor, callback)
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()

        biometricPrompt.authenticate(promptInfo)
    }
}
