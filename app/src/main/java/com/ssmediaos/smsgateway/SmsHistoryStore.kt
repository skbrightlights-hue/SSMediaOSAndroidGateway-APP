package com.ssmediaos.smsgateway

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * Private, bounded on-device SMS history for this Gateway installation.
 *
 * SENT means Android reported successful submission to the network stack; it
 * does NOT prove delivery to the recipient. DELIVERED is recorded only after
 * Android invokes a delivery-report PendingIntent for that SMS part/job.
 * Institution-wide reporting must still come from the authenticated Main Server.
 */
object SmsHistoryStore {

    private const val PREFS = "ss_mediaos_sms_history_v2"
    private const val KEY_RECORDS = "records"
    private const val MAX_RECORDS = 2000

    private val allowedStatuses = setOf(
        "QUEUED", "SENDING", "SENT", "DELIVERED", "FAILED"
    )

    data class Entry(
        val jobId: Long,
        val requestId: String,
        val status: String,
        val timestampMs: Long,
        val recipientMasked: String,
        val errorCode: String
    )

    data class Summary(
        val total: Int,
        val queued: Int,
        val sending: Int,
        val sent: Int,
        val delivered: Int,
        val failed: Int
    )

    @Synchronized
    fun record(
        context: Context,
        jobId: Long,
        requestId: String,
        status: String,
        recipient: String = "",
        errorCode: String = ""
    ) {
        if (jobId <= 0L || requestId.isBlank()) return

        val normalizedStatus = status.trim().uppercase(Locale.ROOT)
        if (normalizedStatus !in allowedStatuses) return

        val prefs = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val old = try {
            JSONArray(prefs.getString(KEY_RECORDS, "[]") ?: "[]")
        } catch (_: Exception) {
            JSONArray()
        }

        // One current row per server job. A later state replaces the earlier state.
        val rebuilt = JSONArray()
        for (i in 0 until old.length()) {
            val item = old.optJSONObject(i) ?: continue
            if (item.optLong("job_id") != jobId) rebuilt.put(item)
        }

        val row = JSONObject().apply {
            put("job_id", jobId)
            put("request_id", requestId.trim().take(80))
            put("status", normalizedStatus)
            put("timestamp_ms", System.currentTimeMillis())
            put("recipient_masked", maskNumber(recipient))
            put("error_code", errorCode.trim().take(120))
        }
        rebuilt.put(row)

        val trimmed = JSONArray()
        val start = (rebuilt.length() - MAX_RECORDS).coerceAtLeast(0)
        for (i in start until rebuilt.length()) {
            trimmed.put(rebuilt.getJSONObject(i))
        }

        prefs.edit().putString(KEY_RECORDS, trimmed.toString()).apply()
    }

    fun list(context: Context, limit: Int = 500): List<Entry> {
        val raw = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_RECORDS, "[]") ?: "[]"
        val rows = try { JSONArray(raw) } catch (_: Exception) { JSONArray() }
        val result = ArrayList<Entry>()
        for (i in rows.length() - 1 downTo 0) {
            val row = rows.optJSONObject(i) ?: continue
            result.add(
                Entry(
                    jobId = row.optLong("job_id"),
                    requestId = row.optString("request_id"),
                    status = row.optString("status", "UNKNOWN"),
                    timestampMs = row.optLong("timestamp_ms"),
                    recipientMasked = row.optString("recipient_masked", "—"),
                    errorCode = row.optString("error_code")
                )
            )
            if (result.size >= limit.coerceIn(1, MAX_RECORDS)) break
        }
        return result
    }

    fun summary(context: Context, fromTimestampMs: Long = 0L): Summary {
        val entries = list(context, MAX_RECORDS)
            .filter { it.timestampMs >= fromTimestampMs }
        return Summary(
            total = entries.size,
            queued = entries.count { it.status == "QUEUED" },
            sending = entries.count { it.status == "SENDING" },
            sent = entries.count { it.status == "SENT" },
            delivered = entries.count { it.status == "DELIVERED" },
            failed = entries.count { it.status == "FAILED" }
        )
    }

    private fun maskNumber(value: String): String {
        val digits = value.filter { it.isDigit() }
        if (digits.isBlank()) return "—"
        return if (digits.length <= 4) "••••" else "••••••${digits.takeLast(4)}"
    }
}

