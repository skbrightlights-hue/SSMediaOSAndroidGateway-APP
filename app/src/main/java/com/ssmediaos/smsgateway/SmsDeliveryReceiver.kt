package com.ssmediaos.smsgateway

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * Receives carrier/network delivery reports for SMS parts.
 *
 * IMPORTANT:
 * - SMS_SENT and SMS_DELIVERED are different events.
 * - A delivery report is counted only when Android returns RESULT_OK.
 * - Multipart SMS is marked DELIVERED only after every part reports success.
 * - Missing delivery reports are not guessed as delivered.
 */
class SmsDeliveryReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SMS_DELIVERY_RESULT) {
            Log.w(TAG, "Ignoring unexpected delivery action: ${intent.action}")
            return
        }

        val jobId = intent.getLongExtra(EXTRA_JOB_ID, -1L)
        val requestId = intent.getStringExtra(EXTRA_GATEWAY_REQUEST_ID).orEmpty().trim()
        val partIndex = intent.getIntExtra(EXTRA_PART_INDEX, -1)
        val totalParts = intent.getIntExtra(EXTRA_TOTAL_PARTS, 1)
        val requestCode = intent.getIntExtra(EXTRA_REQUEST_CODE, -1)

        if (jobId <= 0L || requestId.isBlank()) {
            Log.w(TAG, "Ignoring delivery callback with missing job/request identity")
            return
        }
        if (totalParts !in 1..255 || partIndex !in 0 until totalParts) {
            Log.w(TAG, "Ignoring invalid multipart delivery callback: $partIndex/$totalParts")
            return
        }

        val delivered = resultCode == Activity.RESULT_OK
        val failureCode = when (resultCode) {
            SmsManager.RESULT_ERROR_GENERIC_FAILURE -> "DELIVERY_GENERIC_FAILURE"
            SmsManager.RESULT_ERROR_NO_SERVICE -> "DELIVERY_NO_SERVICE"
            SmsManager.RESULT_ERROR_NULL_PDU -> "DELIVERY_NULL_PDU"
            SmsManager.RESULT_ERROR_RADIO_OFF -> "DELIVERY_RADIO_OFF"
            else -> if (delivered) "" else "DELIVERY_UNKNOWN_$resultCode"
        }

        val appContext = context.applicationContext
        val allDelivered = updatePartState(
            context = appContext,
            jobId = jobId,
            requestId = requestId,
            partIndex = partIndex,
            totalParts = totalParts,
            delivered = delivered
        )

        if (allDelivered) {
            SmsHistoryStore.record(
                context = appContext,
                jobId = jobId,
                requestId = requestId,
                status = "DELIVERED"
            )
        } else if (!delivered) {
            // Keep the job as SENT: a delivery report failure is not the same
            // as Android failing to submit the SMS. Never count it as delivered.
            SmsHistoryStore.record(
                context = appContext,
                jobId = jobId,
                requestId = requestId,
                status = "SENT",
                errorCode = failureCode
            )
        }

        // Explicit in-app event. GatewayService can consume this in the next
        // integration step to report delivery state to Main Server if its API
        // contract supports delivery receipts.
        val callback = Intent(ACTION_SMS_DELIVERY_PART_RESULT).apply {
            setPackage(appContext.packageName)
            putExtra(EXTRA_JOB_ID, jobId)
            putExtra(EXTRA_GATEWAY_REQUEST_ID, requestId)
            putExtra(EXTRA_PART_INDEX, partIndex)
            putExtra(EXTRA_TOTAL_PARTS, totalParts)
            putExtra(EXTRA_DELIVERED, delivered)
            putExtra(EXTRA_DELIVERY_ERROR, failureCode)
            putExtra(EXTRA_ALL_PARTS_DELIVERED, allDelivered)
            putExtra(EXTRA_REQUEST_CODE, requestCode)
        }
        appContext.sendBroadcast(callback)

        Log.i(
            TAG,
            "Delivery callback: job=$jobId part=${partIndex + 1}/$totalParts " +
                "delivered=$delivered allDelivered=$allDelivered"
        )
    }

    /**
     * Persist per-part delivery state so multipart callbacks can arrive in any
     * order and duplicate callbacks do not inflate the result.
     */
    private fun updatePartState(
        context: Context,
        jobId: Long,
        requestId: String,
        partIndex: Int,
        totalParts: Int,
        delivered: Boolean
    ): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = "delivery_${jobId}_${requestId.take(80)}"

        synchronized(LOCK) {
            val existing = try {
                JSONObject(prefs.getString(key, "{}") ?: "{}")
            } catch (_: Exception) {
                JSONObject()
            }

            val storedRequestId = existing.optString("request_id")
            val storedTotal = existing.optInt("total_parts", totalParts)
            if (storedRequestId.isNotEmpty() && storedRequestId != requestId) {
                Log.w(TAG, "Request identity changed for delivery state; resetting")
                existing.remove("parts")
            }
            if (storedTotal != totalParts) {
                Log.w(TAG, "Multipart count changed for job $jobId; resetting delivery state")
                existing.remove("parts")
            }

            val parts = existing.optJSONObject("parts") ?: JSONObject()
            val partKey = partIndex.toString()
            // Once a part has reported failure, a later duplicate success must
            // not silently convert the same callback into a delivered result.
            if (!parts.has(partKey)) {
                parts.put(partKey, delivered)
            }

            var everyPartDelivered = parts.length() == totalParts
            if (everyPartDelivered) {
                for (index in 0 until totalParts) {
                    if (!parts.optBoolean(index.toString(), false)) {
                        everyPartDelivered = false
                        break
                    }
                }
            }

            val updated = JSONObject().apply {
                put("request_id", requestId)
                put("total_parts", totalParts)
                put("parts", parts)
                put("updated_at", System.currentTimeMillis())
            }
            prefs.edit().putString(key, updated.toString()).apply()
            return everyPartDelivered
        }
    }

    companion object {
        private const val TAG = "SmsDeliveryReceiver"
        private const val PREFS = "ss_mediaos_sms_delivery_parts_v1"
        private val LOCK = Any()

        const val ACTION_SMS_DELIVERY_RESULT =
            "com.ssmediaos.smsgateway.ACTION_SMS_DELIVERY_RESULT"
        const val ACTION_SMS_DELIVERY_PART_RESULT =
            "com.ssmediaos.smsgateway.ACTION_SMS_DELIVERY_PART_RESULT"

        const val EXTRA_REQUEST_CODE = "request_code"
        const val EXTRA_JOB_ID = "job_id"
        const val EXTRA_GATEWAY_REQUEST_ID = "gateway_request_id"
        const val EXTRA_PART_INDEX = "part_index"
        const val EXTRA_TOTAL_PARTS = "total_parts"
        const val EXTRA_DELIVERED = "delivery_success"
        const val EXTRA_DELIVERY_ERROR = "delivery_error"
        const val EXTRA_ALL_PARTS_DELIVERED = "all_parts_delivered"
    }
}

