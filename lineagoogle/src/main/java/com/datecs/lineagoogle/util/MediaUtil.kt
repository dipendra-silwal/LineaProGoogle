package com.datecs.lineagoogle.util

import android.annotation.SuppressLint
import android.content.Context
import android.media.MediaPlayer
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

object MediaUtil {
    @SuppressLint("ObsoleteSdkInt", "ServiceCast")
    @JvmStatic
    fun playSound(context: Context, soundID: Int) {
        val vibratorManager =  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager =
                context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as Vibrator
        }

        val duration = 500L
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibratorManager.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibratorManager.vibrate(duration)
        }

        val mediaPlayer = MediaPlayer.create(context, soundID)
        mediaPlayer.setVolume(1.0f, 1.0f)
        mediaPlayer.start()
    }
}
