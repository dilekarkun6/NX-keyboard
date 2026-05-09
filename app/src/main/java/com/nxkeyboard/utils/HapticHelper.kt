package com.nxkeyboard.utils

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View

object HapticHelper {

    fun keyPress(view: View) {
        val context = view.context
        if (!isEnabled(context)) return
        val intensity = getIntensity(context)
        if (intensity == 0) return
        val ms = when (intensity) {
            1 -> 8L
            2 -> 18L
            else -> 35L
        }
        triggerVibration(context, ms, intensity)
    }

    fun longPress(view: View) {
        val context = view.context
        if (!isEnabled(context)) return
        val intensity = getIntensity(context)
        if (intensity == 0) return
        val ms = when (intensity) {
            1 -> 18L
            2 -> 32L
            else -> 50L
        }
        triggerVibration(context, ms, intensity)
    }

    fun pulse(context: Context, milliseconds: Long = 30) {
        if (!isEnabled(context)) return
        val intensity = getIntensity(context)
        if (intensity == 0) return
        triggerVibration(context, milliseconds, intensity)
    }

    private fun triggerVibration(context: Context, milliseconds: Long, intensity: Int) {
        val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        if (vibrator == null || !vibrator.hasVibrator()) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val amplitude = when (intensity) {
                    1 -> 60
                    2 -> 130
                    else -> VibrationEffect.DEFAULT_AMPLITUDE
                }
                vibrator.vibrate(VibrationEffect.createOneShot(milliseconds, amplitude))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(milliseconds)
            }
        } catch (_: Throwable) {}
    }

    private fun isEnabled(context: Context): Boolean {
        return PrefsHelper.get(context).getBoolean("haptic_feedback", true)
    }

    private fun getIntensity(context: Context): Int {
        return PrefsHelper.getString(context, "haptic_intensity", "2").toIntOrNull() ?: 2
    }

    @Suppress("unused")
    private fun fallbackPerformHaptic(view: View, kind: Int) {
        view.performHapticFeedback(kind, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
    }
}
