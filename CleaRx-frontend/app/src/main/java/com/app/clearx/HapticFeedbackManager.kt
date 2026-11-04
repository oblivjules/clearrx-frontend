package com.app.clearx

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.content.ContextCompat

class HapticFeedbackManager(private val context: Context) {

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = ContextCompat.getSystemService(
                context,
                VibratorManager::class.java
            )
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            ContextCompat.getSystemService(context, Vibrator::class.java)
        }
    }

    enum class HapticType {
        TICK,      // Light feedback for state improvement
        SUCCESS,   // Confirmation for capture
        WARNING,   // Gentle buzz for bad state
        IMPACT     // Strong impact for capture button press
    }

    fun triggerHaptic(type: HapticType) {
        val vibratorInstance = vibrator
        if (vibratorInstance == null || !hasVibrator()) {
            return // No vibrator hardware available
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            triggerModernHaptic(type, vibratorInstance)
        } else {
            triggerLegacyHaptic(type, vibratorInstance)
        }
    }

    private fun hasVibrator(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            vibrator?.hasVibrator() ?: false
        } else {
            @Suppress("DEPRECATION")
            vibrator?.hasVibrator() ?: false
        }
    }

    private fun triggerModernHaptic(type: HapticType, vibrator: Vibrator) {
        try {
            when (type) {
                HapticType.TICK -> {
                    // Light tick when state improves (BAD → PARTIAL)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        vibrator.vibrate(
                            VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
                        )
                    } else {
                        vibrator.vibrate(VibrationEffect.createOneShot(12, 100))
                    }
                }

                HapticType.SUCCESS -> {
                    // Distinct double-pulse for successful capture
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        val timings = longArrayOf(0, 40, 60, 40)
                        val amplitudes = intArrayOf(0, 255, 0, 255)
                        vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
                    } else {
                        vibrator.vibrate(VibrationEffect.createOneShot(50, 200))
                    }
                }

                HapticType.WARNING -> {
                    // Gentle buzz for bad state
                    vibrator.vibrate(VibrationEffect.createOneShot(30, 80))
                }

                HapticType.IMPACT -> {
                    // Strong click for button press
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        vibrator.vibrate(
                            VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                        )
                    } else {
                        vibrator.vibrate(VibrationEffect.createOneShot(25, 180))
                    }
                }
            }
        } catch (e: Exception) {
            // Silent fail - haptics are optional
        }
    }

    @Suppress("DEPRECATION")
    private fun triggerLegacyHaptic(type: HapticType, vibrator: Vibrator) {
        try {
            when (type) {
                HapticType.TICK -> vibrator.vibrate(15)
                HapticType.SUCCESS -> vibrator.vibrate(longArrayOf(0, 40, 60, 40), -1)
                HapticType.WARNING -> vibrator.vibrate(30)
                HapticType.IMPACT -> vibrator.vibrate(25)
            }
        } catch (e: Exception) {
            // Silent fail - haptics are optional
        }
    }

    fun release() {
        try {
            vibrator?.cancel()
        } catch (e: Exception) {
            // Silent fail
        }
    }
}