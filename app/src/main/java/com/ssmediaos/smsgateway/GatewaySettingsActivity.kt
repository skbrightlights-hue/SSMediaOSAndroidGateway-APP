package com.ssmediaos.smsgateway

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.text.Editable
import android.text.TextWatcher
import android.os.Build
import android.os.Bundle
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.widget.ArrayAdapter
import android.widget.AdapterView
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import android.view.View
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class GatewaySettingsActivity : Activity() {

    private lateinit var serverUrlInput: EditText
    private lateinit var apiTokenInput: EditText
    private lateinit var deviceIdInput: EditText
    private lateinit var organizationCodeInput: EditText
    private lateinit var registeredMobileInput: EditText
    private lateinit var organizationNameText: TextView
    private lateinit var organizationStatusText: TextView
    private lateinit var simStatusText: TextView
    private lateinit var simSelectionSpinner: Spinner
    private var availableSims: List<SubscriptionInfo> = emptyList()
    private lateinit var verifyMobileButton: Button
    private lateinit var connectionStatusText: TextView
    private lateinit var activationStatusText: TextView
    private lateinit var saveSettingsButton: Button
    private lateinit var testConnectionButton: Button
    private lateinit var activateDeviceButton: Button

    private val statusPrefsName = "ss_mediaos_gateway_status"

    companion object {
        private const val CONNECTION_TIMEOUT = 15000
        private const val ACTIVATION_READ_TIMEOUT = 20000
        private const val REQUEST_SIM_PERMISSION = 2201

        private const val PREF_SERVICE_RUNNING = "service_running"
        private const val PREF_SERVER_STATE = "server_state"
        private const val PREF_SERVER_CHECK_AT = "server_check_at"

        private const val STATE_CONNECTED = "CONNECTED"
        private const val STATE_FAILED = "FAILED"
        private const val STATE_CONNECTING = "CONNECTING"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gateway_settings)

        serverUrlInput = findViewById(R.id.serverUrlInput)
        apiTokenInput = findViewById(R.id.apiTokenInput)
        deviceIdInput = findViewById(R.id.deviceIdInput)
        organizationCodeInput = findViewById(R.id.organizationCodeInput)
        registeredMobileInput = findViewById(R.id.registeredMobileInput)
        organizationNameText = findViewById(R.id.organizationNameText)
        organizationStatusText = findViewById(R.id.organizationStatusText)
        simStatusText = findViewById(R.id.simStatusText)
        simSelectionSpinner = findViewById(R.id.simSelectionSpinner)
        verifyMobileButton = findViewById(R.id.verifyMobileButton)
        connectionStatusText = findViewById(R.id.connectionStatusText)
        activationStatusText = findViewById(R.id.activationStatusText)

        // Keep status-card colors synchronized with the real verification state.
        organizationStatusText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val value = s?.toString().orEmpty().uppercase()
                val verified = value.contains("VERIFIED") && !value.contains("NOT VERIFIED")
                val rejected = value.contains("REJECTED")
                val bg = when {
                    verified -> 0xFFE6F6F3.toInt()
                    rejected -> 0xFFFEECEC.toInt()
                    else -> 0xFFFFF7E8.toInt()
                }
                val fg = when {
                    verified -> 0xFF087F5B.toInt()
                    rejected -> 0xFFB42318.toInt()
                    else -> 0xFF9A5B00.toInt()
                }
                organizationStatusText.backgroundTintList = ColorStateList.valueOf(bg)
                organizationStatusText.setTextColor(fg)
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })

        activationStatusText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val value = s?.toString().orEmpty().uppercase()
                val activated = value.contains("ACTIVATED") && !value.contains("NOT ACTIVATED") &&
                    !value.contains("INCOMPLETE")
                val inProgress = value.contains("PROGRESS")
                val bg = when {
                    activated -> 0xFFE6F6F3.toInt()
                    inProgress -> 0xFFEAF2FF.toInt()
                    else -> 0xFFFFF7E8.toInt()
                }
                val fg = when {
                    activated -> 0xFF087F5B.toInt()
                    inProgress -> 0xFF2457A6.toInt()
                    else -> 0xFF9A5B00.toInt()
                }
                activationStatusText.backgroundTintList = ColorStateList.valueOf(bg)
                activationStatusText.setTextColor(fg)
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })

        saveSettingsButton = findViewById(R.id.saveSettingsButton)
        testConnectionButton = findViewById(R.id.testConnectionButton)
        activateDeviceButton = findViewById(R.id.activateDeviceButton)

        // Server URL and Device ID are controlled internally.
        serverUrlInput.setText(SecureConfig.DEFAULT_SERVER_URL)
        serverUrlInput.visibility = View.GONE // Server URL is never shown in the UI.

        val deviceCode = SecureConfig.getDeviceCode(this).orEmpty()
        deviceIdInput.setText(deviceCode)
        deviceIdInput.isEnabled = false

        loadSettings()

        saveSettingsButton.setOnClickListener {
            saveSettings()
        }

        testConnectionButton.setOnClickListener {
            testConnection()
        }

        activateDeviceButton.setOnClickListener {
            activateDevice()
        }

        verifyMobileButton.setOnClickListener {
            verifyOrganizationAndMobile()
        }

        simSelectionSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selected = availableSims.getOrNull(position) ?: return
                val oldId = SecureConfig.getSelectedSimSubscriptionId(this@GatewaySettingsActivity)
                if (oldId != selected.subscriptionId) {
                    SecureConfig.saveSelectedSimSubscriptionId(this@GatewaySettingsActivity, selected.subscriptionId)
                    simStatusText.text = "SIM selected: ${simLabel(selected)}"
                }
            }
        }
        loadAvailableSims()
    }

    private fun loadSettings() {
        serverUrlInput.setText(SecureConfig.DEFAULT_SERVER_URL)
        serverUrlInput.visibility = View.GONE

        // The XML input should use textPassword so the API key is masked.
        apiTokenInput.setText(
            SecureConfig.getCompanyApiKey(this).orEmpty()
        )

        deviceIdInput.setText(
            SecureConfig.getDeviceCode(this).orEmpty()
        )

        val prefs = getSharedPreferences(statusPrefsName, MODE_PRIVATE)
        organizationCodeInput.setText(prefs.getString("organization_code", "").orEmpty())
        registeredMobileInput.setText(prefs.getString("registered_mobile", "").orEmpty())
        organizationNameText.text = prefs.getString("organization_name", null)
            ?.takeIf { it.isNotBlank() } ?: "Not verified"
        organizationStatusText.text = prefs.getString("organization_status", null)
            ?.takeIf { it.isNotBlank() } ?: "Organization Verification: PENDING"

        updateActivationStatus()
    }

    private fun saveSettings(): Boolean {
        val companyApiKey = apiTokenInput.text.toString().trim()
        val deviceCode = SecureConfig.getDeviceCode(this).orEmpty()
        val organizationCode = organizationCodeInput.text.toString().trim()
        val registeredMobile = registeredMobileInput.text.toString().trim()

        if (companyApiKey.isBlank()) {
            apiTokenInput.error = "Company API Token required"
            apiTokenInput.requestFocus()
            return false
        }

        if (organizationCode.isBlank()) {
            organizationCodeInput.error = "Organization Code required"
            organizationCodeInput.requestFocus()
            return false
        }

        if (registeredMobile.isBlank()) {
            registeredMobileInput.error = "Registered mobile number required"
            registeredMobileInput.requestFocus()
            return false
        }

        if (registeredMobile.any { !it.isDigit() } || registeredMobile.length !in 8..15) {
            registeredMobileInput.error = "Enter a valid mobile number (8–15 digits)"
            registeredMobileInput.requestFocus()
            return false
        }

        if (deviceCode.isBlank()) {
            connectionStatusText.text =
                "Unable to create this installation's Device ID."
            return false
        }

        return try {
            val oldApiKey = SecureConfig.getCompanyApiKey(this).orEmpty()

            val saved = SecureConfig.saveActivationSettings(
                context = this,
                serverUrl = SecureConfig.DEFAULT_SERVER_URL,
                deviceCode = deviceCode,
                activationToken = companyApiKey
            )

            if (!saved) {
                connectionStatusText.text =
                    "Settings could not be saved. Please check the Company API Token."
                return false
            }

            if (oldApiKey != companyApiKey) {
                SecureConfig.clearDeviceToken(this)
                setServerState(STATE_CONNECTING)
            }

            val prefs = getSharedPreferences(statusPrefsName, MODE_PRIVATE)
            val oldOrganizationCode = prefs.getString("organization_code", "").orEmpty()
            val oldRegisteredMobile = prefs.getString("registered_mobile", "").orEmpty()
            val identityChanged =
                oldOrganizationCode != organizationCode ||
                oldRegisteredMobile != registeredMobile ||
                oldApiKey != companyApiKey

            val editor = prefs.edit()
                .putString("organization_code", organizationCode)
                .putString("registered_mobile", registeredMobile)
            if (identityChanged) {
                editor.putString("organization_status", "PENDING")
                    .remove("organization_name")
                    .putBoolean("organization_verified", false)
                SecureConfig.clearDeviceToken(this)
                organizationNameText.text = "Not verified"
                organizationStatusText.text = "Organization Verification: PENDING"
            }
            editor.apply()

            updateActivationStatus()

            Toast.makeText(
                this,
                "Company API Token saved successfully",
                Toast.LENGTH_SHORT
            ).show()

            true
        } catch (e: Exception) {
            connectionStatusText.text =
                "Settings could not be saved. Please try again."
            false
        }
    }

    private fun testConnection() {
        if (!saveSettings()) return

        connectionStatusText.text = "Checking server connection..."
        testConnectionButton.isEnabled = false

        Thread {
            var reachable = false

            val resultText = try {
                val connection = URL(
                    "${SecureConfig.DEFAULT_SERVER_URL}/"
                ).openConnection() as HttpURLConnection

                connection.requestMethod = "GET"
                connection.connectTimeout = CONNECTION_TIMEOUT
                connection.readTimeout = CONNECTION_TIMEOUT
                connection.useCaches = false
                connection.instanceFollowRedirects = true

                try {
                    val statusCode = connection.responseCode
                    reachable = statusCode in 200..299

                    if (reachable) {
                        "Server is reachable (HTTP $statusCode)."
                    } else {
                        "Server responded with HTTP $statusCode."
                    }
                } finally {
                    connection.disconnect()
                }
            } catch (e: Exception) {
                "Connection failed. Check your internet connection and try again."
            }

            runOnUiThread {
                connectionStatusText.text = resultText

                if (!reachable) {
                    setServerState(STATE_FAILED)
                }

                testConnectionButton.isEnabled = true
                updateActivationStatus()
            }
        }.start()
    }

    private fun activateDevice() {
        if (!saveSettings()) return

        if (!SecureConfig.isOrganizationVerified(this)) {
            activationStatusText.text = "Device status: NOT ACTIVATED"
            connectionStatusText.text = "Verify the institution with Main Server first."
            return
        }

        activateDeviceButton.isEnabled = false
        activateDeviceButton.text = "Connecting..."
        activationStatusText.text = "Device status: ACTIVATION IN PROGRESS"
        connectionStatusText.text = "Activating this Gateway with Main Server..."

        ApiClient.activate { result ->
            runOnUiThread {
                activateDeviceButton.isEnabled = true
                activateDeviceButton.text = "Verify/Connect Device"

                result.onSuccess { response ->
                    val clientId = SecureConfig.getClientInstitutionId(this)
                    val mainId = SecureConfig.getMainInstitutionId(this)
                    val deviceTokenSaved = !SecureConfig.getDeviceToken(this).isNullOrBlank()

                    if (!deviceTokenSaved || mainId.isBlank()) {
                        SecureConfig.clearDeviceToken(this)
                        activationStatusText.text = "Device status: ACTIVATION INCOMPLETE"
                        connectionStatusText.text =
                            "Main Server did not save a valid device token and Main Institution ID."
                    } else {
                        activationStatusText.text = "Device status: ACTIVATED"
                        organizationStatusText.text =
                            "Organization Verification: VERIFIED\n" +
                            "Client Institution ID: $clientId\n" +
                            "Main Server Institution ID: $mainId"
                        connectionStatusText.text =
                            response.optString("message", "Gateway device activated successfully.")
                        setServerState(STATE_CONNECTED)
                    }
                }.onFailure { error ->
                    SecureConfig.clearDeviceToken(this)
                    activationStatusText.text = "Device status: NOT ACTIVATED"
                    connectionStatusText.text =
                        error.message ?: "Main Server could not activate this Gateway."
                    setServerState(STATE_FAILED)
                }

                updateActivationStatus()
            }
        }
    }

    private fun verifyOrganizationAndMobile() {
        if (!saveSettings()) return

        val apiKey = SecureConfig.getCompanyApiKey(this).orEmpty().trim()
        val code = organizationCodeInput.text.toString().trim()
        val mobile = registeredMobileInput.text.toString().trim()

        if (apiKey.isBlank()) {
            apiTokenInput.error = "Company API Token required"
            apiTokenInput.requestFocus()
            return
        }
        if (code.isBlank()) {
            organizationCodeInput.error = "Institution Code required"
            organizationCodeInput.requestFocus()
            return
        }
        if (mobile.isBlank()) {
            registeredMobileInput.error = "Registered mobile number required"
            registeredMobileInput.requestFocus()
            return
        }

        verifyMobileButton.isEnabled = false
        organizationNameText.text = "Verifying institution..."
        organizationStatusText.text = "Organization Verification: CHECKING"
        activationStatusText.text = "Device status: NOT ACTIVATED"
        connectionStatusText.text =
            "Checking Main Server, Client Server, institution mapping and registered mobile..."

        // ApiClient validates the Main Server response and persists Client and
        // Main Institution IDs separately. Never compare these IDs numerically.
        ApiClient.diagnoseGateway(apiKey, code, mobile) { result ->
            runOnUiThread {
                verifyMobileButton.isEnabled = true

                result.onSuccess { response ->
                    val clientId = SecureConfig.getClientInstitutionId(this)
                    val mainId = SecureConfig.getMainInstitutionId(this)
                    val institutionName = SecureConfig.getOrganizationName(this)
                    val verified = SecureConfig.isOrganizationVerified(this)

                    if (!verified || clientId.isBlank() || institutionName.isBlank()) {
                        SecureConfig.clearOrganizationVerification(this)
                        SecureConfig.clearDeviceToken(this)
                        organizationNameText.text = "Not verified"
                        organizationStatusText.text = "Organization Verification: REJECTED"
                        connectionStatusText.text =
                            "Main Server response did not contain a complete verified Client institution identity."
                    } else {
                        getSharedPreferences(statusPrefsName, MODE_PRIVATE).edit()
                            .putBoolean("organization_verified", true)
                            .putString("organization_status", "VERIFIED")
                            .putString("organization_name", institutionName)
                            .apply()

                        organizationNameText.text = institutionName
                        organizationStatusText.text =
                            "Organization Verification: VERIFIED\n" +
                            "Client Institution ID: $clientId\n" +
                            "Main Server Institution ID: ${mainId.ifBlank { "Assigned during activation" }}"
                        connectionStatusText.text =
                            response.optString("message", "Institution verified successfully by Main Server.")
                        setServerState(STATE_CONNECTED)
                    }
                }.onFailure { error ->
                    SecureConfig.clearOrganizationVerification(this)
                    SecureConfig.clearDeviceToken(this)
                    getSharedPreferences(statusPrefsName, MODE_PRIVATE).edit()
                        .putBoolean("organization_verified", false)
                        .putString("organization_status", "REJECTED")
                        .remove("organization_name")
                        .apply()

                    organizationNameText.text = "Not verified"
                    organizationStatusText.text = "Organization Verification: REJECTED"
                    connectionStatusText.text =
                        error.message ?: "Main Server could not verify this institution."
                    setServerState(STATE_FAILED)
                }

                updateActivationStatus()
            }
        }
    }

    private fun loadAvailableSims() {
        if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            simStatusText.text = "SIM status: Permission required to list available SIMs"
            requestPermissions(arrayOf(Manifest.permission.READ_PHONE_STATE), REQUEST_SIM_PERMISSION)
            return
        }
        try {
            val manager = getSystemService(SubscriptionManager::class.java)
            availableSims = manager?.activeSubscriptionInfoList.orEmpty()
            if (availableSims.isEmpty()) {
                simSelectionSpinner.adapter = ArrayAdapter(
                    this, android.R.layout.simple_spinner_dropdown_item,
                    listOf("No active SIM found")
                )
                simStatusText.text = "SIM status: Insert/enable a SIM and refresh this screen"
                SecureConfig.clearSelectedSimSubscriptionId(this)
                return
            }
            val labels = availableSims.map { simLabel(it) }
            simSelectionSpinner.adapter = ArrayAdapter(
                this, android.R.layout.simple_spinner_item, labels
            ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
            val selectedId = SecureConfig.getSelectedSimSubscriptionId(this)
            val selectedIndex = availableSims.indexOfFirst { it.subscriptionId == selectedId }
            if (selectedIndex >= 0) {
                simSelectionSpinner.setSelection(selectedIndex)
                simStatusText.text = "SIM selected: ${labels[selectedIndex]}"
            } else {
                simSelectionSpinner.setSelection(0)
                val first = availableSims.first()
                SecureConfig.saveSelectedSimSubscriptionId(this, first.subscriptionId)
                simStatusText.text = "SIM selected: ${labels[0]}"
            }
        } catch (e: SecurityException) {
            simStatusText.text = "SIM status: Android did not grant SIM information access"
        } catch (e: Exception) {
            simStatusText.text = "SIM status: Could not read SIM list (${e.javaClass.simpleName})"
        }
    }

    private fun simLabel(info: SubscriptionInfo): String {
        val slot = if (info.simSlotIndex >= 0) "SIM ${info.simSlotIndex + 1}" else "SIM"
        val carrier = info.carrierName?.toString()?.takeIf { it.isNotBlank() }
            ?: info.displayName?.toString()?.takeIf { it.isNotBlank() }
            ?: "Mobile network"
        return "$slot — $carrier"
    }

    @Deprecated("Deprecated in Android API")
    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_SIM_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadAvailableSims()
            } else {
                simStatusText.text = "SIM status: Allow Phone permission to select the assigned SIM"
            }
        }
    }

    private fun updateActivationStatus() {
        val deviceToken = SecureConfig.getDeviceToken(this)
        val companyApiKey = SecureConfig.getCompanyApiKey(this)
        val deviceCode = SecureConfig.getDeviceCode(this)
        val organizationVerified = getSharedPreferences(
            statusPrefsName, MODE_PRIVATE
        ).getBoolean("organization_verified", false)

        if (
            deviceToken.isNullOrBlank() ||
            companyApiKey.isNullOrBlank() ||
            deviceCode.isNullOrBlank() ||
            !organizationVerified
        ) {
            activationStatusText.text = "Device status: NOT ACTIVATED"

            if (connectionStatusText.text.isNullOrBlank()) {
                connectionStatusText.text =
                    "Enter the Company API Token and activate this device."
            }
            return
        }

        activationStatusText.text = "Device status: ACTIVATED"

        val clientInstitutionId = SecureConfig.getClientInstitutionId(this)
        val mainInstitutionId = SecureConfig.getMainInstitutionId(this)
        organizationStatusText.text =
            "Organization Verification: VERIFIED\n" +
            "Client Institution ID: ${clientInstitutionId.ifBlank { "Not returned" }}\n" +
            "Main Server Institution ID: ${mainInstitutionId.ifBlank { "Pending activation" }}"

        val prefs = getSharedPreferences(
            statusPrefsName,
            MODE_PRIVATE
        )

        val serviceRunning = prefs.getBoolean(
            PREF_SERVICE_RUNNING,
            false
        )

        val serverState = prefs.getString(
            PREF_SERVER_STATE,
            STATE_CONNECTING
        ) ?: STATE_CONNECTING

        val lastCheckAt = prefs.getLong(
            PREF_SERVER_CHECK_AT,
            0L
        )

        val isRecent = lastCheckAt > 0L &&
            System.currentTimeMillis() - lastCheckAt <= 30000L

        when {
            !serviceRunning -> {
                connectionStatusText.text =
                    "Device activated. Gateway service is not running."
            }

            !isRecent -> {
                connectionStatusText.text =
                    "Device activated. Waiting for a fresh server connection check."
            }

            serverState == STATE_CONNECTED -> {
                connectionStatusText.text =
                    "Device activated. Server connection confirmed by gateway polling."
            }

            serverState == STATE_FAILED -> {
                connectionStatusText.text =
                    "Device activated, but server connection failed. Retrying..."
            }

            else -> {
                connectionStatusText.text =
                    "Device activated. Connecting to server..."
            }
        }
    }

    private fun setServerState(state: String) {
        getSharedPreferences(
            statusPrefsName,
            MODE_PRIVATE
        ).edit()
            .putString(PREF_SERVER_STATE, state)
            .putLong(PREF_SERVER_CHECK_AT, System.currentTimeMillis())
            .apply()
    }

    override fun onResume() {
        super.onResume()

        if (::activationStatusText.isInitialized) {
            updateActivationStatus()
        }
    }
}
