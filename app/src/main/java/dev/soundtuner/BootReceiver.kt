package dev.soundtuner

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/** Turns the EQ back on after the phone restarts or the app is updated, if it was on before. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return
        if (!Settings(context).enabled) return
        try {
            context.startForegroundService(
                Intent(context, EqService::class.java).setAction(EqService.ACTION_APPLY)
            )
        } catch (e: Exception) {
            Log.w("SoundTuner", "Could not restart EQ after ${intent.action}", e)
        }
    }
}
