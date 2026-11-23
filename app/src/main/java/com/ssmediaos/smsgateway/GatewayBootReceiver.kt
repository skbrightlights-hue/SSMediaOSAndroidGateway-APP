package com.ssmediaos.smsgateway

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * Restarts the Gateway after a normal device reboot only when the user had
 * previously enabled it. It does not attempt to bypass Android Force Stop.
 *
 * Required manifest entries are provided separately in the next integration
 * step: RECEIVE_BOOT_COMPLETED permission and an exported receiver with
 * BOOT_COMPLETED / LOCKED_BOOT_COMPLETED intent actions.
 */
class GatewayBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_LOCKED_BOOT_COMPLETED) {
            return
        }

        // Must match GatewayService.statusPrefsName and preference key.
        // SharedPreferences are device-protected only after unlock in this app;
        // therefore start on BOOT_COMPLETED, not before user unlock.
        if (action == Intent.ACTION_LOCKED_BOOT_COMPLETED) return

        val enabled = context
            .getSharedPreferences("ss_mediaos_gateway_status", Context.MODE_PRIVATE)
            .getBoolean("gateway_enabled_by_user", false)

        if (!enabled) {
            Log.i(TAG, "Gateway was not enabled by user; skipping boot restart")
            return
        }

        val serviceIntent = Intent(context, GatewayService::class.java).apply {
            action = GatewayService.ACTION_START
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
            Log.i(TAG, "Requested Gateway restart after device boot")
        } catch (e: Exception) {
            // Android/OEM restrictions can still prevent a boot-time start.
            Log.e(TAG, "Could not restart Gateway after boot", e)
        }
    }

    companion object {
        private const val TAG = "GatewayBootReceiver"
    }
}

