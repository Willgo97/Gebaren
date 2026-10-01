package nl.gebaren.app

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator

fun Context.tril(t: Trilling) {
    if (t.patroon.isEmpty()) return
    val trilling = getSystemService(Vibrator::class.java) ?: return
    val sterkte = IntArray(t.patroon.size) { if (it % 2 == 1) 255 else 0 }
    val effect = VibrationEffect.createWaveform(t.patroon, sterkte, -1)
    if (Build.VERSION.SDK_INT >= 33) {
        trilling.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ACCESSIBILITY))
    } else {
        @Suppress("DEPRECATION")
        trilling.vibrate(effect, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY).build())
    }
}
