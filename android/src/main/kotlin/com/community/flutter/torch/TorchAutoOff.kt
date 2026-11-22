package com.community.flutter.torch

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock

/**
 * The timed auto-off of a steady light, without a service: an inexact AlarmManager alarm
 * (setAndAllowWhileIdle, so no SCHEDULE_EXACT_ALARM) delivered to [TorchAutoOffReceiver], which switches
 * the torch off. It fires while the app is in the background or its activity is gone; Android may deliver
 * it somewhat late (inexact, and at most about once per 9 minutes per app in Doze).
 *
 * Opt-in: scheduling needs the receiver in the host app's manifest (see README); without it
 * [schedule] does nothing and `getFeatures()` reports `autoOff: false`.
 */
internal object TorchAutoOff {
    private const val PREFS = "community_flutter_torch"

    /** Elapsed-realtime deadline of the armed alarm; absent when none is armed. */
    private const val PREF_DEADLINE = "auto_off_deadline"
    private const val REQUEST_CODE = 0x4146
    const val ACTION = "com.community.flutter.torch.AUTO_OFF"

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isDeclared(ctx: Context): Boolean = TorchCamera.declaredReceiver(ctx, TorchAutoOffReceiver::class.java) != null

    private fun pendingIntent(ctx: Context): PendingIntent? {
        val cn = TorchCamera.declaredReceiver(ctx, TorchAutoOffReceiver::class.java) ?: return null
        val i = Intent(ACTION).setComponent(cn)
        return PendingIntent.getBroadcast(
            ctx.applicationContext, REQUEST_CODE, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** Arms (or re-arms) the off [seconds] from now. False when the receiver is not declared. */
    fun schedule(ctx: Context, seconds: Int): Boolean {
        if (seconds <= 0) {
            cancel(ctx)
            return false
        }
        val pi = pendingIntent(ctx) ?: return false
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val at = SystemClock.elapsedRealtime() + seconds * 1000L
        am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pi)
        prefs(ctx).edit().putLong(PREF_DEADLINE, at).apply()
        return true
    }

    /** Drops an armed off (a manual switch replaces the timer). Cheap when none is armed. */
    fun cancel(ctx: Context) {
        val p = prefs(ctx)
        if (!p.contains(PREF_DEADLINE)) return
        p.edit().remove(PREF_DEADLINE).apply()
        val pi = pendingIntent(ctx) ?: return
        try {
            (ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pi)
        } catch (_: Exception) {
        }
        pi.cancel()
    }

    fun isArmed(ctx: Context): Boolean = prefs(ctx).contains(PREF_DEADLINE)

    /** The alarm fired: switch the torch off unless the off was cancelled meanwhile. */
    fun fire(ctx: Context) {
        val p = prefs(ctx)
        if (!p.contains(PREF_DEADLINE)) return
        p.edit().remove(PREF_DEADLINE).apply()
        try {
            TorchCamera.setTorch(ctx, false)
        } catch (_: Exception) {
            // No torch, or the camera is in use (the torch is off then anyway).
        }
    }
}

/** Receives the [TorchAutoOff] alarm. Declared by the host app (exported="false"). `open` like the tiles. */
open class TorchAutoOffReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == TorchAutoOff.ACTION) TorchAutoOff.fire(context)
    }
}
