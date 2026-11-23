package com.ssmediaos.smsgateway

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * Restarts the Gateway after a normal device reboot only when the user had
 * previously enabled it. Android Force Stop cannot be bypassed by an app.
 */
class GatewayBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val receivedAction = intent?.action ?: return
        if (receivedAction != Intent.ACTION_BOOT_COMPLETED &&
            receivedAction != Intent.ACTION_LOCKED_BOOT_COMPLETED
        ) {
            return
        }

        // This app stores the preference in credential-protected storage, so
        // wait until BOOT_COMPLETED (after the user unlocks the device).
        if (receivedAction == Intent.ACTION_LOCKED_BOOT_COMPLETED) return

        val enabled = context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(PREF_GATEWAY_ENABLED, false)

        if (!enabled) {
            Log.i(TAG, "Gateway was not enabled by user; skipping boot restart")
            return
        }

        // Use setAction() rather than an apply { action = ... } block. The latter
        // can resolve `action` to the outer immutable local variable and cause
        // Kotlin's: 'val' cannot be reassigned.
        val serviceIntent = Intent(context, GatewayService::class.java)
            .setAction(GatewayService.ACTION_START)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                @Suppress("DEPRECATION")
                context.startService(serviceIntent)
            }
            Log.i(TAG, "Requested Gateway restart after device boot")
        } catch (exception: Exception) {
            Log.e(TAG, "Could not restart Gateway after boot", exception)
        }
    }

    companion object {
        private const val TAG = "GatewayBootReceiver"
        private const val PREFS_NAME = "ss_mediaos_gateway_status"
        private const val PREF_GATEWAY_ENABLED = "gateway_enabled_by_user"
    }
}
