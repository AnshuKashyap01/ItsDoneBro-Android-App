package com.itsdonebro.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/**
 * Restarts the tracking foreground service after device reboot.
 * Requires RECEIVE_BOOT_COMPLETED permission (declared in manifest).
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, TrackingForegroundService::class.java)
            )
        }
    }
}
