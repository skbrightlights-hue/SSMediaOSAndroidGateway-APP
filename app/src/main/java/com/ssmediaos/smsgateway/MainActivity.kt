package com.ssmediaos.smsgateway

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var statusText: TextView
    private lateinit var serverText: TextView
    private lateinit var permissionText: TextView
    private lateinit var gatewayText: TextView
    private var permissionActionCard: LinearLayout? = null
    private var permissionActionButton: Button? = null
    private var permissionActionMessage: TextView? = null
    private lateinit var content: LinearLayout
    private lateinit var dashboardScroll: ScrollView
    private lateinit var rootFrame: FrameLayout
    private lateinit var pageContainer: LinearLayout
    private var drawerOverlay: FrameLayout? = null
    private lateinit var sentCountText: TextView
    private lateinit var failedCountText: TextView
    private lateinit var deliveredCountText: TextView
    private lateinit var pendingCountText: TextView
    private lateinit var activePageScroll: ScrollView
    private lateinit var activePageContent: LinearLayout
    private var activePageTitle: String = "Dashboard"

    private var gatewayStartRequested = false
    private var smsStatsRequestInFlight = false

    private val navy = Color.rgb(16, 27, 53)
    private val blue = Color.rgb(24, 119, 242)
    private val green = Color.rgb(34, 197, 94)
    private val backgroundColor = Color.rgb(244, 247, 252)
    private val textDark = Color.rgb(30, 41, 59)
    private val textMuted = Color.rgb(100, 116, 139)
    private val white = Color.WHITE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = navy
        window.navigationBarColor = navy
        window.decorView.systemUiVisibility = 0

        createInterface()
        requestRequiredPermissions()
        updateStatus()
    }

    override fun onResume() {
        super.onResume()

        if (::statusText.isInitialized) {
            updateStatus()
            refreshSmsStats()
        }
    }

    private fun createInterface() {
        // One shared app shell owns the navigation drawer. Individual sections
        // only render content; they must not create their own duplicate menus.
        rootFrame = FrameLayout(this).apply {
            setBackgroundColor(backgroundColor)
        }
        pageContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(backgroundColor)
        }

        // Compact app bar with a working navigation menu.
        val appBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(10), dp(16), dp(10))
            setBackgroundColor(white)
            elevation = dp(3).toFloat()
        }

        val menuButton = TextView(this).apply {
            text = "☰"
            textSize = 27f
            setTextColor(navy)
            gravity = Gravity.CENTER
            contentDescription = "Open navigation menu"
            setPadding(dp(8), dp(4), dp(14), dp(4))
            setOnClickListener { showNavigationMenu(this) }
        }
        appBar.addView(menuButton, LinearLayout.LayoutParams(dp(48), dp(48)))

        val appBarTitle = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        appBarTitle.addView(textView("SS MediaOS", 17f, navy, true))
        appBarTitle.addView(textView("SMS GATEWAY • CONTROL CENTER", 10f, textMuted, true))
        appBar.addView(appBarTitle, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val appBarStatus = TextView(this).apply {
            text = "●"
            textSize = 18f
            setTextColor(green)
            contentDescription = "Application running"
        }
        appBar.addView(appBarStatus)

        pageContainer.addView(appBar, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        dashboardScroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            setPadding(0, 0, 0, dp(16))
        }

        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(24))
        }

        // Brand header
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(22))
            background = roundedGradient(
                intArrayOf(
                    Color.rgb(16, 27, 53),
                    Color.rgb(23, 59, 112),
                    Color.rgb(24, 119, 242)
                ),
                24
            )
            elevation = dp(4).toFloat()
        }

        val brandRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val logo = TextView(this).apply {
            text = "SMS"
            textSize = 19f
            gravity = Gravity.CENTER
            setTextColor(white)
            setTypeface(null, Typeface.BOLD)
            background = roundedBackground(
                Color.rgb(36, 87, 157),
                18
            )
            contentDescription = "SS MediaOS SMS Gateway logo"
        }

        brandRow.addView(
            logo,
            LinearLayout.LayoutParams(dp(66), dp(66))
        )

        val brandText = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), 0, 0, 0)
        }

        brandText.addView(
            textView("SS MediaOS", 22f, white, true)
        )

        brandText.addView(
            textView(
                "SMS GATEWAY",
                12f,
                Color.rgb(207, 226, 255),
                true
            ).apply {
                letterSpacing = 0.12f
                setPadding(0, dp(5), 0, 0)
            }
        )

        brandRow.addView(
            brandText,
            LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
        )

        header.addView(brandRow)

        header.addView(
            textView(
                "Smart messaging. Reliable connectivity.",
                13f,
                Color.rgb(230, 239, 255),
                false
            ).apply {
                setPadding(0, dp(18), 0, 0)
            }
        )

        header.addView(
            textView("Owner: SS MediaOS", 12f, white, true).apply {
                setPadding(0, dp(16), 0, 0)
            }
        )

        content.addView(
            header,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(22)
            }
        )

        // Permission action card stays at the top of the dashboard.
        // Android displays its native permission dialog when the user taps Allow.
        val permissionPromptCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(15), dp(16), dp(15))
            background = roundedBackground(Color.rgb(255, 247, 232), 16)
        }
        permissionPromptCard.addView(
            textView("SMS PERMISSION REQUIRED", 13f, Color.rgb(146, 64, 14), true)
        )
        permissionActionMessage = textView(
            "Allow SMS and SIM permissions to enable outgoing SMS and selected-SIM routing.",
            12f,
            Color.rgb(120, 75, 35),
            false
        ).apply {
            setPadding(0, dp(6), 0, dp(10))
        }
        permissionPromptCard.addView(permissionActionMessage)

        permissionActionButton = Button(this).apply {
            text = "Allow SMS Permission"
            isAllCaps = false
            setTextColor(Color.WHITE)
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
            background = roundedBackground(Color.rgb(8, 127, 115), 10)
            setOnClickListener { requestRequiredPermissions() }
        }
        permissionPromptCard.addView(
            permissionActionButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(48)
            )
        )
        permissionActionCard = permissionPromptCard
        content.addView(
            permissionPromptCard,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(18)
            }
        )

        // Dashboard
        content.addView(
            sectionTitle(
                "Dashboard",
                "Gateway overview and controls"
            )
        )

        // Status cards
        val statusRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val appCard = createStatCard(
            "APP STATUS",
            "RUNNING",
            "Application",
            green
        )

        val connectionCard = createStatCard(
            "SERVER",
            "CHECKING",
            "Live connection status",
            blue
        )

        statusRow.addView(
            appCard,
            LinearLayout.LayoutParams(0, dp(120), 1f).apply {
                marginEnd = dp(7)
            }
        )

        statusRow.addView(
            connectionCard,
            LinearLayout.LayoutParams(0, dp(120), 1f).apply {
                marginStart = dp(7)
            }
        )

        content.addView(
            statusRow,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(14)
            }
        )

        // SMS activity summary. Counts are recorded from Android send callbacks.
        val dashboardInstitutionName = if (SecureConfig.isOrganizationVerified(this)) {
            SecureConfig.getOrganizationName(this).ifBlank { "Verified institution" }
        } else {
            "Institution not verified"
        }
        content.addView(
            sectionTitle(
                "SMS Activity",
                "$dashboardInstitutionName • Today's SMS totals"
            )
        )

        val smsStatsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val sentCard = createStatCard("SMS SENT", "0", "Submitted by device", green)
        val failedCard = createStatCard("SMS FAILED", "0", "Send errors", Color.rgb(220, 53, 69))
        val receivedCard = createStatCard("SMS DELIVERED", "—", "Confirmed delivery receipts", green)
        sentCountText = sentCard.getChildAt(1) as TextView
        failedCountText = failedCard.getChildAt(1) as TextView
        deliveredCountText = receivedCard.getChildAt(1) as TextView
        smsStatsRow.addView(sentCard, LinearLayout.LayoutParams(0, dp(132), 1f).apply { marginEnd = dp(5) })
        smsStatsRow.addView(failedCard, LinearLayout.LayoutParams(0, dp(132), 1f).apply { marginStart = dp(5); marginEnd = dp(5) })
        smsStatsRow.addView(receivedCard, LinearLayout.LayoutParams(0, dp(132), 1f).apply { marginStart = dp(5) })
        content.addView(smsStatsRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(8) })
        pendingCountText = textView(
            "Pending: —   •   Delivery is counted only after a confirmed delivery receipt",
            11f, textMuted, false
        ).apply { setPadding(dp(4), 0, dp(4), dp(14)) }
        content.addView(pendingCountText)

        val permissionCard = createStatCard(
            "SMS PERMISSION",
            "CHECKING",
            "Android permission",
            blue
        )

        content.addView(
            permissionCard,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(112)
            ).apply {
                bottomMargin = dp(18)
            }
        )

        // Visible shortcut: open the verified institution's SMS delivery report
        // directly from the Dashboard (not only from the navigation drawer).
        val reportShortcutCard = createCard().apply {
            addView(textView("INSTITUTION SMS REPORT", 13f, navy, true))
            addView(
                textView(
                    "View only the SMS activity for the institution verified on this device. " +
                        "Sent and Delivered are shown as separate results.",
                    12f,
                    textMuted,
                    false
                ).apply {
                    setPadding(0, dp(7), 0, dp(12))
                    setLineSpacing(dp(2).toFloat(), 1f)
                }
            )
            addView(
                actionButton("Open SMS Delivery Report", blue) {
                    showSmsLogsPage()
                },
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(50)
                )
            )
        }
        content.addView(
            reportShortcutCard,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(20)
            }
        )

        // Keep operational status fields initialized for the existing service/status logic.
        // Detailed controls and settings are intentionally moved to the Sidebar so the
        // Dashboard remains a clean overview instead of a long mixed page.
        gatewayText = textView("Gateway service status", 1f, textMuted, false)
        serverText = textView("Server: Not configured", 1f, textMuted, false)
        permissionText = textView("SMS Permission: Checking", 1f, textMuted, false)
        statusText = textView("App Status: Running", 1f, textMuted, false)

        // Footer: developer credit intentionally shown only here
        val footer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(18), dp(10), dp(12))
        }

        footer.addView(
            textView(
                "SS MediaOS SMS Gateway",
                14f,
                navy,
                true
            )
        )

        footer.addView(
            textView(
                "Developed by Shivam Yadav",
                12f,
                textMuted,
                false
            ).apply {
                setPadding(0, dp(6), 0, 0)
            }
        )

        content.addView(footer)

        dashboardScroll.addView(
            content,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        pageContainer.addView(
            dashboardScroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        activePageScroll = ScrollView(this).apply {
            visibility = View.GONE
            isFillViewport = true
            setPadding(0, 0, 0, dp(18))
        }
        activePageContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(18), dp(16), dp(24))
        }
        activePageScroll.addView(activePageContent)
        pageContainer.addView(
            activePageScroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        rootFrame.addView(
            pageContainer,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        buildSharedNavigationDrawer()
        setContentView(rootFrame)
        refreshSmsStats()
    }

    /**
     * Creates exactly one navigation drawer for the whole Activity.
     * Dashboard sections never create their own menu or duplicate sidebar.
     */
    private fun buildSharedNavigationDrawer() {
        val overlay = FrameLayout(this).apply {
            visibility = View.GONE
            setBackgroundColor(Color.TRANSPARENT)
            isClickable = true
            isFocusable = true
        }

        val scrim = View(this).apply {
            setBackgroundColor(0x990B1730.toInt())
            setOnClickListener { closeNavigationMenu() }
        }
        overlay.addView(
            scrim,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        val drawer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(white)
            elevation = dp(18).toFloat()
            isClickable = true
            setPadding(0, 0, 0, dp(12))
        }

        val drawerHeader = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(30), dp(18), dp(24))
            background = roundedGradient(
                intArrayOf(Color.rgb(16, 27, 53), Color.rgb(24, 119, 242)), 0
            )
        }
        drawerHeader.addView(textView("SS MediaOS", 22f, white, true))
        drawerHeader.addView(
            textView("SMS GATEWAY", 11f, Color.rgb(219, 234, 254), true).apply {
                setPadding(0, dp(5), 0, 0)
                letterSpacing = 0.14f
            }
        )
        drawerHeader.addView(
            textView("Secure Organization Messaging", 12f, Color.rgb(230, 239, 255), false).apply {
                setPadding(0, dp(13), 0, 0)
            }
        )
        drawer.addView(drawerHeader)

        val menuScroll = ScrollView(this).apply {
            isFillViewport = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
        }
        val menuItems = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(16), dp(12), dp(12))
        }
        menuScroll.addView(menuItems)

        fun addDrawerItem(label: String, icon: String, action: () -> Unit, enabled: Boolean = true) {
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14), 0, dp(12), 0)
                background = roundedBackground(
                    if (enabled) Color.rgb(247, 249, 253) else Color.rgb(250, 250, 250), 12
                )
                alpha = if (enabled) 1f else 0.58f
                isClickable = true
                isFocusable = true
                minimumHeight = dp(50)
                setOnClickListener {
                    closeNavigationMenu()
                    action()
                }
            }
            item.addView(textView(icon, 18f, blue, true), LinearLayout.LayoutParams(dp(34), dp(48)))
            item.addView(
                textView(label, 14f, textDark, true),
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            )
            if (!enabled) {
                item.addView(textView("Soon", 10f, textMuted, false))
            }
            menuItems.addView(item, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(50)
            ).apply { bottomMargin = dp(7) })
        }

        addDrawerItem("OVERVIEW", "▰", { showDashboard() })
        addDrawerItem("Dashboard", "⌂", { showDashboard() })
        addDrawerItem("Institution SMS Report", "▤", { showSmsLogsPage() })
        addDrawerItem("Reports & Analytics", "▥", { showReportsPage("today") })
        addDrawerItem("GATEWAY", "▣", { openGatewaySettings() })
        addDrawerItem("Gateway Settings", "⚙", { openGatewaySettings() })
        addDrawerItem("Start Gateway Service", "▶", { startGatewayService() })
        addDrawerItem("Stop Gateway Service", "■", { stopGatewayService() })
        addDrawerItem("Server & API Settings", "⇄", { openGatewaySettings() })
        addDrawerItem("SMS Permissions", "♙", {
            val sendGranted = checkSelfPermission(Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
            val phoneGranted = checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
            val details = "SEND_SMS: ${if (sendGranted) "Granted" else "Not granted"}\n" +
                "READ_PHONE_STATE: ${if (phoneGranted) "Granted" else "Not granted"}\n\n" +
                "SEND_SMS is required to send authorized outgoing messages. " +
                "READ_PHONE_STATE is used to inspect the selected SIM/subscription for multi-SIM routing. " +
                "Incoming SMS and inbox access are not enabled."
            showPolicyDialog("SMS Permissions", details)
            if (!sendGranted || !phoneGranted) requestRequiredPermissions()
        })
        addDrawerItem("POLICIES & COMPLIANCE", "▤", { showPoliciesPage() })
        addDrawerItem("Privacy Policy", "🔒", { showPolicyDialog("Privacy Policy", privacyPolicyText()) })
        addDrawerItem("Telecom & SMS Policy", "◉", { showPolicyDialog("Telecom & SMS Policy", telecomSmsPolicyText()) })
        addDrawerItem("DND & Consent Policy", "✓", { showPolicyDialog("DND, Consent & Opt-out Rules", dndPolicyText()) })
        addDrawerItem("Google Play Store Policy", "▶", { showPolicyDialog("Google Play Store Policy", playStorePolicyText()) })
        addDrawerItem("Android Policy", "▣", { showPolicyDialog("Android App Permissions & Policy", androidPolicyText()) })
        addDrawerItem("SYSTEM", "⌘", { showSystemInformation() })
        addDrawerItem("System Information", "ⓘ", { showSystemInformation() })
        addDrawerItem("About", "ⓘ", { showAboutPage() })

        val spacer = View(this)
        menuItems.addView(spacer, LinearLayout.LayoutParams(1, dp(8)))
        addDrawerItem("Logout", "⇥", {
            AlertDialog.Builder(this)
                .setTitle("Logout")
                .setMessage("This app build does not have a server logout endpoint. To avoid clearing device credentials incorrectly, no local logout was performed.")
                .setNegativeButton("Close", null)
                .show()
        })

        drawer.addView(
            menuScroll,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        )
        overlay.addView(
            drawer,
            FrameLayout.LayoutParams(dp(304), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.START)
        )
        rootFrame.addView(
            overlay,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        drawerOverlay = overlay
    }

    private fun showNavigationMenu(anchor: View) {
        drawerOverlay?.visibility = View.VISIBLE
    }

    private fun closeNavigationMenu() {
        drawerOverlay?.visibility = View.GONE
    }

    override fun onBackPressed() {
        when {
            drawerOverlay?.visibility == View.VISIBLE -> closeNavigationMenu()
            activePageScroll.visibility == View.VISIBLE -> showDashboard()
            else -> super.onBackPressed()
        }
    }

    private fun scrollToSection(tagName: String) {
        val target = content.findViewWithTag<View>(tagName) ?: return
        dashboardScroll.post {
            dashboardScroll.smoothScrollTo(0, target.top)
        }
    }

    /**
     * SMS report calls share ApiClient's non-poll executor with SMS result and
     * delivery callbacks. Do not run dashboard/report queries while the gateway
     * is active or starting; a slow report request could otherwise delay the
     * result callbacks and stall the next queued SMS job.
     */
    private fun isGatewayActiveOrStarting(): Boolean {
        val prefs = getSharedPreferences(SERVER_STATUS_PREFS, MODE_PRIVATE)
        return gatewayStartRequested || prefs.getBoolean(PREF_SERVICE_RUNNING, false)
    }

    private fun refreshSmsStats() {
        if (!::sentCountText.isInitialized) return

        if (isGatewayActiveOrStarting()) {
            sentCountText.text = "—"
            failedCountText.text = "—"
            deliveredCountText.text = "—"
            pendingCountText.text = "Live SMS reporting is paused while the Gateway is running. Open Reports after stopping the Gateway."
            return
        }

        if (smsStatsRequestInFlight) return

        if (!SecureConfig.isOrganizationVerified(this)) {
            sentCountText.text = "—"
            failedCountText.text = "—"
            deliveredCountText.text = "—"
            pendingCountText.text = "Verify institution to view its SMS totals."
            return
        }

        val institutionId = SecureConfig.getClientInstitutionId(this)
        val institutionCode = SecureConfig.getOrganizationCode(this)
        if (institutionId.isBlank() || institutionCode.isBlank()) {
            sentCountText.text = "—"
            failedCountText.text = "—"
            deliveredCountText.text = "—"
            pendingCountText.text = "Verified institution identity is incomplete. Re-verify institution."
            return
        }

        sentCountText.text = "…"
        failedCountText.text = "…"
        deliveredCountText.text = "…"
        pendingCountText.text = "Loading verified institution SMS totals…"

        val reportPath = "/api/v1/sms/device_reports.php?view=summary&period=today" +
            "&client_institution_id=${android.net.Uri.encode(institutionId)}" +
            "&institution_code=${android.net.Uri.encode(institutionCode)}"

        smsStatsRequestInFlight = true
        ApiClient.getConfigured(reportPath) { result ->
            runOnUiThread {
                smsStatsRequestInFlight = false
                result.onSuccess { response ->
                    if (!response.optBoolean("ok", false)) {
                        setDashboardUnavailable(
                            response.optString("message", "SMS report API did not return a valid response.")
                        )
                        return@onSuccess
                    }

                    val responseInstitutionId = response.optString(
                        "client_institution_id",
                        response.optString("institution_id", "")
                    )
                    val responseInstitutionCode = response.optString(
                        "institution_code",
                        response.optString("organization_code", "")
                    )

                    // Do not display a summary unless the API confirms the same
                    // verified institution requested by this Android device.
                    val scopeMatches =
                        responseInstitutionId == institutionId ||
                        (responseInstitutionCode.isNotBlank() &&
                            responseInstitutionCode.equals(institutionCode, ignoreCase = true))

                    if (!scopeMatches) {
                        setDashboardUnavailable(
                            "Main Server did not confirm this verified institution's identity in the report response."
                        )
                        return@onSuccess
                    }

                    val summary = response.optJSONObject("summary") ?: response
                    val sent = summary.optInt(
                        "sent",
                        summary.optInt("submitted", summary.optInt("total_sent", 0))
                    )
                    val failed = summary.optInt(
                        "failed",
                        summary.optInt("total_failed", 0)
                    )
                    val delivered = summary.optInt(
                        "delivered",
                        summary.optInt("total_delivered", 0)
                    )
                    val queued = summary.optInt("queued", summary.optInt("total_queued", 0))
                    val pending = summary.optInt("pending", summary.optInt("total_pending", 0))

                    sentCountText.text = sent.toString()
                    failedCountText.text = failed.toString()
                    deliveredCountText.text = delivered.toString()
                    pendingCountText.text =
                        "Institution: ${SecureConfig.getOrganizationName(this)}  •  " +
                        "Queued: $queued  •  Pending: $pending  •  Period: Today"
                }.onFailure { error ->
                    setDashboardUnavailable(
                        error.message ?: "Could not load Main Server SMS totals."
                    )
                }
            }
        }
    }

    private fun setDashboardUnavailable(reason: String) {
        sentCountText.text = "—"
        failedCountText.text = "—"
        deliveredCountText.text = "—"
        pendingCountText.text = "Main Server report unavailable: ${reason.take(180)}"
    }

    private fun showDashboard() {
        activePageTitle = "Dashboard"
        activePageScroll.visibility = View.GONE
        dashboardScroll.visibility = View.VISIBLE
        refreshSmsStats()
    }

    private fun showPage(title: String, build: (LinearLayout) -> Unit) {
        activePageTitle = title
        dashboardScroll.visibility = View.GONE
        activePageScroll.visibility = View.VISIBLE
        activePageContent.removeAllViews()
        activePageContent.addView(sectionTitle(title, "SS MediaOS SMS Gateway"))
        build(activePageContent)
        activePageScroll.smoothScrollTo(0, 0)
    }

    private fun addPageMessage(target: LinearLayout, message: String, color: Int = textMuted) {
        target.addView(textView(message, 13f, color, false).apply {
            setPadding(dp(12), dp(14), dp(12), dp(14))
            background = roundedBackground(white, 12)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(10)
        })
    }

    private fun showSmsLogsPage() {
        showPage("Institution SMS Report") { page ->
            if (!SecureConfig.isOrganizationVerified(this)) {
                addPageMessage(page, "Institution is not verified. Verify this institution with Main Server before viewing SMS reports.", Color.rgb(185, 28, 28))
                return@showPage
            }

            val institutionId = SecureConfig.getClientInstitutionId(this)
            val institutionCode = SecureConfig.getOrganizationCode(this)
            val institutionName = SecureConfig.getOrganizationName(this)
            if (institutionId.isBlank() || institutionCode.isBlank()) {
                addPageMessage(page, "Verified institution identity is incomplete. Re-verify this institution.", Color.rgb(185, 28, 28))
                return@showPage
            }

            addPageMessage(
                page,
                "Institution: $institutionName\nOnly SMS records belonging to this verified institution are shown. Other institutions are excluded."
            )

            if (isGatewayActiveOrStarting()) {
                addPageMessage(
                    page,
                    "To protect live SMS sending, reports are paused while the Gateway is running. Stop the Gateway before opening this report.",
                    Color.rgb(146, 64, 14)
                )
                return@showPage
            }

            val loading = textView("Loading institution SMS report…", 13f, textMuted, false)
            page.addView(loading)

            val reportPath = "/api/v1/sms/device_reports.php?view=logs&limit=50" +
                "&client_institution_id=${android.net.Uri.encode(institutionId)}" +
                "&institution_code=${android.net.Uri.encode(institutionCode)}"
            ApiClient.getConfigured(reportPath) { result ->
                runOnUiThread {
                    page.removeView(loading)
                    result.onSuccess { response ->
                        if (!response.optBoolean("ok", false)) {
                            addPageMessage(
                                page,
                                response.optString("message", "SMS report API is not available."),
                                Color.rgb(185, 28, 28)
                            )
                        } else {
                            val rows = response.optJSONArray("items")
                            if (rows == null || rows.length() == 0) {
                                addPageMessage(
                                    page,
                                    "No SMS records were returned for this verified Gateway device."
                                )
                            } else {
                                var shownCount = 0
                                for (i in 0 until rows.length()) {
                                    val item = rows.optJSONObject(i) ?: continue

                                    fun firstValue(vararg keys: String, fallback: String = "—"): String {
                                        for (key in keys) {
                                            val value = item.optString(key, "").trim()
                                            if (value.isNotEmpty() && value != "null") return value
                                        }
                                        return fallback
                                    }

                                    val rowInstitutionId = firstValue(
                                        "client_institution_id", "clientInstitutionId",
                                        "institution_id", "institutionId", fallback = ""
                                    )
                                    val rowInstitutionCode = firstValue(
                                        "institution_code", "institutionCode",
                                        "organization_code", "organizationCode", fallback = ""
                                    )
                                    // Fail closed: do not display records whose tenant cannot be
                                    // matched to the locally verified institution identity.
                                    val belongsToVerifiedInstitution =
                                        rowInstitutionId == institutionId ||
                                        (rowInstitutionCode.isNotBlank() &&
                                            rowInstitutionCode.equals(institutionCode, ignoreCase = true))
                                    if (!belongsToVerifiedInstitution) continue
                                    shownCount++

                                    val school = institutionName
                                    val attendance = firstValue(
                                        "attendance_status", "attendance", "event_status",
                                        "submitted_status", "reason"
                                    )
                                    val mobile = firstValue(
                                        "recipient_masked", "mobile_masked", "phone_masked",
                                        "recipient", fallback = "Hidden"
                                    )
                                    val status = firstValue(
                                        "delivery_status", "gateway_status", "status",
                                        fallback = "UNKNOWN"
                                    ).uppercase()
                                    val apiCode = firstValue("api_code", "code", "error_code")
                                    val httpCode = firstValue("http_status", "http_code", "http", fallback = "—")
                                    val requestId = firstValue("request_id", "requestId")
                                    val createdAt = firstValue("created_at", "requested_at", "timestamp", fallback = "Time unavailable")
                                    val message = firstValue("message", "status_message", "description", fallback = "")
                                    val gatewayResult = firstValue(
                                        "gateway_result", "device_result", "send_result",
                                        fallback = "Not reported"
                                    )
                                    val statusColor = when (status) {
                                        "DELIVERED" -> Color.rgb(21, 128, 61)
                                        "SENT", "SUBMITTED" -> Color.rgb(194, 120, 3)
                                        "FAILED", "REJECTED" -> Color.rgb(185, 28, 28)
                                        else -> Color.rgb(37, 99, 175)
                                    }
                                    val statusMeaning = when (status) {
                                        "ACCEPTED", "QUEUED", "SMS_QUEUED" ->
                                            "Main Server accepted / queued this request. This does NOT prove delivery. Check Android Gateway poll, claim and send result."
                                        "SENT", "SUBMITTED" ->
                                            "Android Gateway confirmed SMS sending. This does NOT prove delivery."
                                        "DELIVERED" ->
                                            "A delivery receipt/status has been confirmed."
                                        "FAILED", "REJECTED" ->
                                            "The request or SMS failed. Check the API code and error message below."
                                        else ->
                                            "Final delivery state is not confirmed yet. Check the latest Gateway result."
                                    }

                                    val card = createCard()
                                    card.addView(
                                        textView(school, 18f, navy, true)
                                    )
                                    card.addView(
                                        textView(
                                            "SMS DELIVERY REPORT",
                                            10f,
                                            textMuted,
                                            true
                                        ).apply { setPadding(0, dp(3), 0, dp(10)) }
                                    )
                                    card.addView(
                                        textView(
                                            status.replace('_', ' '),
                                            16f,
                                            statusColor,
                                            true
                                        )
                                    )
                                    card.addView(
                                        textView(
                                            "Attendance: $attendance\nMobile: $mobile\nTime: $createdAt",
                                            12f,
                                            textDark,
                                            false
                                        ).apply {
                                            setPadding(0, dp(9), 0, dp(9))
                                            setLineSpacing(dp(3).toFloat(), 1f)
                                        }
                                    )
                                    card.addView(
                                        textView(
                                            "API Code: $apiCode    HTTP: $httpCode\nRequest ID: $requestId\nGateway Result: $gatewayResult",
                                            11f,
                                            textMuted,
                                            false
                                        ).apply {
                                            setPadding(0, dp(5), 0, dp(8))
                                            setLineSpacing(dp(3).toFloat(), 1f)
                                        }
                                    )
                                    if (message.isNotBlank()) {
                                        card.addView(
                                            textView(message, 12f, textDark, false).apply {
                                                setPadding(0, dp(3), 0, dp(7))
                                            }
                                        )
                                    }
                                    card.addView(
                                        textView(
                                            statusMeaning,
                                            12f,
                                            statusColor,
                                            true
                                        ).apply {
                                            setPadding(dp(10), dp(9), dp(10), dp(9))
                                            background = roundedBackground(
                                                when (status) {
                                                    "DELIVERED" -> Color.rgb(230, 246, 237)
                                                    "SENT", "SUBMITTED" -> Color.rgb(255, 247, 230)
                                                    "FAILED", "REJECTED" -> Color.rgb(254, 236, 236)
                                                    else -> Color.rgb(235, 244, 255)
                                                },
                                                10
                                            )
                                        }
                                    )
                                    page.addView(
                                        card,
                                        LinearLayout.LayoutParams(
                                            ViewGroup.LayoutParams.MATCH_PARENT,
                                            ViewGroup.LayoutParams.WRAP_CONTENT
                                        ).apply { bottomMargin = dp(12) }
                                    )
                                }
                                if (shownCount == 0) {
                                    addPageMessage(
                                        page,
                                        "No SMS records matched this verified institution. Records without a matching institution ID/code are hidden for tenant isolation."
                                    )
                                }
                            }
                        }
                    }.onFailure { error ->
                        addPageMessage(
                            page,
                            "Could not load SMS delivery report: ${error.message ?: "Network error"}",
                            Color.rgb(185, 28, 28)
                        )
                    }
                }
            }

            page.addView(
                actionButton("Refresh Report", blue) { showSmsLogsPage() },
                buttonLayout()
            )
        }
    }

    private fun showReportsPage(period: String) {
        showPage("Reports & Analytics") { page ->
            val filters = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            listOf("today", "week", "month", "year").forEach { key ->
                filters.addView(actionButton(key.replaceFirstChar { it.uppercase() }, if (key == period) blue else navy) {
                    showReportsPage(key)
                }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(4) })
            }
            page.addView(filters, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)))
            addPageMessage(page, "Period: ${period.uppercase()} • Main Server totals for this verified Gateway device. Delivered means a confirmed delivery receipt, not merely Android send success.")
            if (isGatewayActiveOrStarting()) {
                addPageMessage(
                    page,
                    "To protect live SMS sending, reports are paused while the Gateway is running. Stop the Gateway before opening this report.",
                    Color.rgb(146, 64, 14)
                )
                return@showPage
            }
            val loading = textView("Loading report…", 13f, textMuted, false)
            page.addView(loading)
            if (!SecureConfig.isOrganizationVerified(this)) {
                addPageMessage(page, "Institution is not verified. Verify this institution before viewing reports.", Color.rgb(185, 28, 28))
                return@showPage
            }
            val institutionId = SecureConfig.getClientInstitutionId(this)
            val institutionCode = SecureConfig.getOrganizationCode(this)
            if (institutionId.isBlank() || institutionCode.isBlank()) {
                addPageMessage(page, "Verified institution identity is incomplete. Re-verify this institution.", Color.rgb(185, 28, 28))
                return@showPage
            }
            ApiClient.getConfigured(
                "/api/v1/sms/device_reports.php?view=summary&period=$period" +
                    "&client_institution_id=${android.net.Uri.encode(institutionId)}" +
                    "&institution_code=${android.net.Uri.encode(institutionCode)}"
            ) { result ->
                runOnUiThread {
                    page.removeView(loading)
                    result.onSuccess { response ->
                        if (!response.optBoolean("ok", false)) {
                            addPageMessage(page, response.optString("message", "Reports API is not available."), Color.rgb(185, 28, 28))
                        } else {
                            val summary = response.optJSONObject("summary") ?: response
                            val grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                            val rows = listOf(
                                "Queued" to summary.optInt("queued", 0),
                                "Submitted / Sent" to summary.optInt("sent", summary.optInt("submitted", 0)),
                                "Delivered (confirmed)" to summary.optInt("delivered", 0),
                                "Failed" to summary.optInt("failed", 0),
                                "Pending" to summary.optInt("pending", 0)
                            )
                            rows.forEach { (label, value) ->
                                val card = createStatCard(label.uppercase(), value.toString(), "Main Server report", when (label) {
                                    "Delivered (confirmed)" -> green
                                    "Failed" -> Color.rgb(220, 53, 69)
                                    else -> blue
                                })
                                grid.addView(card, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(94)).apply { bottomMargin = dp(8) })
                            }
                            page.addView(grid)
                            addPageMessage(page, "Report generated from server records. Updated: ${response.optString("generated_at", "server time not supplied")}")
                        }
                    }.onFailure { error ->
                        addPageMessage(page, "Could not load report: ${error.message ?: "Network error"}", Color.rgb(185, 28, 28))
                    }
                }
            }
        }
    }

    private fun showPoliciesPage() {
        showPage("Policies & Compliance") { page ->
            page.addView(actionButton("Privacy Policy", navy) { showPolicyDialog("Privacy Policy", privacyPolicyText()) }, buttonLayout())
            page.addView(actionButton("Telecom & SMS Policy", navy) { showPolicyDialog("Telecom & SMS Policy", telecomSmsPolicyText()) }, buttonLayout())
            page.addView(actionButton("DND & Consent Policy", navy) { showPolicyDialog("DND, Consent & Opt-out Rules", dndPolicyText()) }, buttonLayout())
            page.addView(actionButton("Google Play Store Policy", navy) { showPolicyDialog("Google Play Store Policy", playStorePolicyText()) }, buttonLayout())
            page.addView(actionButton("Android Policy", navy) { showPolicyDialog("Android App Permissions & Policy", androidPolicyText()) }, buttonLayout())
        }
    }

    private fun showSystemInformation() {
        val pm = packageManager
        val version = try { pm.getPackageInfo(packageName, 0).versionName ?: "Unknown" } catch (_: Exception) { "Unknown" }
        showPolicyDialog("System Information", "App: SS MediaOS SMS Gateway\nVersion: $version\nAndroid: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\nDevice: ${Build.MANUFACTURER} ${Build.MODEL}\nSMS permission: ${if (checkSelfPermission(Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) "Granted" else "Not granted"}\nServer: ${SecureConfig.getServerUrl(this) ?: "Not configured"}")
    }

    private fun showAboutPage() {
        showPolicyDialog("About SS MediaOS SMS Gateway", "SS MediaOS SMS Gateway\nSecure, organization-linked SMS delivery through an authorized device and selected active SIM.\n\nThe app processes server-authorized jobs only. Delivery is reported as confirmed only when the server has a valid delivery receipt.")
    }

    private fun showPolicyDialog(title: String, body: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(body)
            .setPositiveButton("Close", null)
            .show()
    }

    private fun privacyPolicyText(): String = """
SS MediaOS SMS Gateway — Privacy Policy

Purpose
The app sends SMS only for authorized jobs received from the configured SS MediaOS API. The organization/entity code is verified by the server; the app must not guess the organization or route messages by itself.

Data handled
Depending on the API features enabled, the app may send the device code, activation/device token, recipient number, message content required to send an authorized job, job/request ID, send result, error details and timestamps to the configured SS MediaOS server. Keep API keys and tokens private.

Incoming SMS
This app is designed not to read, collect, store, or forward personal incoming SMS or OTP inbox messages. It requires SMS-send permission only to perform authorized outbound jobs. Do not add READ_SMS or RECEIVE_SMS permissions without a separate, lawful and explicit product requirement.

Use and retention
The server operator should restrict access to authorized staff, protect data in transit using HTTPS, apply access controls, and retain message data only as long as needed for service, audit, dispute handling or legal obligations. The app's actual retention period depends on the connected server configuration.

Mobile number and support
Any registered mobile-number change must be verified by the server before it is accepted. Do not treat a number as changed solely because a user typed it into the app.

Security and contact
Do not share device tokens or API keys. For access, correction, deletion or privacy questions, contact the organization that operates your SS MediaOS account. The operator should publish its verified support contact and applicable retention details before production use.

This in-app notice is a product summary, not a legal opinion. The service operator must review it against its actual backend practices and applicable law before public release.
    """.trimIndent()

    private fun telecomSmsPolicyText(): String = """
Telecom & SMS Usage Policy

1. Authorized use only
SMS may be sent only for a valid, server-authorized job linked to a verified organization/entity account. Never use this gateway for spam, fraud, impersonation, harassment, unsolicited bulk messaging or unlawful content.

2. Sender and message compliance
The organization operating the service is responsible for using the appropriate telecom provider, registered sender/header, approved content template, principal-entity registration and other requirements that apply to its message category. Where Indian TRAI/DLT rules apply, follow the current rules and directions of TRAI, the Department of Telecommunications, the access provider and applicable law.

3. Consent and purpose
Keep evidence of recipient consent where required. Send only messages relevant to the stated purpose and permitted category. Do not reuse contact numbers for unrelated marketing.

4. Delivery status
Queued, submitted/sent, failed and delivered are different states. The app can report a device send result; a message must be marked Delivered only when a reliable delivery receipt/status is received from the telecom provider or connected API. A successful device submission is not proof of delivery.

5. Security and abuse prevention
Use HTTPS, protect tokens, limit request rates, validate recipient numbers and message lengths, maintain auditable server logs, and suspend compromised or misused devices. Never bypass telecom controls or provider restrictions.

The server owner and messaging organization are responsible for legal compliance and provider configuration. This policy does not itself register a sender, header, template or DLT entity.
    """.trimIndent()

    private fun dndPolicyText(): String = """
DND, Consent & Opt-out Rules

• Respect recipient preferences and Do Not Disturb (DND) registrations according to the message category and current telecom rules.
• Do not assume that a message is allowed merely because a phone number is available. Confirm the lawful basis/consent and permitted category before queuing it.
• Promotional messages must follow applicable TRAI commercial-communications/DLT requirements, including sender/header, template, consent and time-window rules where applicable.
• Service, transactional, OTP and other permitted messages must still meet the conditions applicable to that category; do not mislabel promotional content to avoid DND restrictions.
• Provide a clear opt-out or preference-management path for messages where required, process opt-outs promptly, and keep suppression lists on the server so blocked recipients are not queued again.
• Never use this app to evade DND, caller/customer preferences, rate limits, telecom-provider blocks or legal restrictions.
• If consent, category, sender registration or eligibility cannot be verified by the API, the server should reject the job rather than send it.

These are safety/compliance principles, not a substitute for checking the latest official TRAI and telecom-provider requirements for your exact SMS category before launch.
    """.trimIndent()

    private fun playStorePolicyText(): String = """
Google Play Store & App Distribution Policy

• Request only permissions essential to the app's disclosed core functionality.
• Explain the SEND_SMS use clearly in the store listing and in-app disclosure. Do not request SMS permissions for unrelated features.
• Do not read or collect incoming SMS/OTP inbox data. This app build is designed for outgoing SMS jobs and does not declare READ_SMS or RECEIVE_SMS in the manifest previously reviewed.
• Provide an accurate privacy policy, data-safety disclosures, support contact and account/data deletion information where applicable.
• Do not claim Play approval or policy compliance until the final manifest, app behavior, Data safety form and Play Console declarations have been reviewed against current Google requirements.

This is an implementation checklist, not a statement that Google Play has approved the app.
    """.trimIndent()

    private fun androidPolicyText(): String = """
Android Permissions & Platform Policy

• SEND_SMS is a sensitive permission. It must be requested at runtime and used only for the app's disclosed outgoing-SMS feature.
• POST_NOTIFICATIONS is requested on Android 13+ for visible notifications where required.
• Do not add READ_SMS, RECEIVE_SMS, call-log, contacts or location permissions unless a separate legitimate feature requires them and the applicable platform/store rules permit them.
• Keep gateway work visible through the appropriate foreground-service notification and comply with Android foreground-service type and start restrictions for the target SDK.
• Handle permission denial gracefully; never bypass Android permission controls.
• Protect API keys, activation tokens and device tokens. Use HTTPS and avoid logging secrets.

Review the final target SDK, manifest and service implementation before release; requirements can change between Android versions.
    """.trimIndent()

    private fun createStatCard(
        label: String,
        value: String,
        description: String,
        accent: Int
    ): LinearLayout {
        val card = createCard()

        card.addView(
            textView(
                label,
                11f,
                textMuted,
                true
            )
        )

        val valueText = textView(
            value,
            20f,
            accent,
            true
        ).apply {
            tag = when (label) {
                "SMS PERMISSION" -> "value_SMS_PERMISSION"
                else -> "value_$label"
            }

            setPadding(0, dp(10), 0, dp(4))
        }

        card.addView(valueText)

        card.addView(
            textView(
                description,
                12f,
                textMuted,
                false
            )
        )

        return card
    }

    private fun sectionTitle(
        title: String,
        subtitle: String
    ): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(2), 0, 0, dp(12))

            addView(
                textView(
                    title,
                    20f,
                    navy,
                    true
                )
            )

            addView(
                textView(
                    subtitle,
                    12f,
                    textMuted,
                    false
                ).apply {
                    setPadding(0, dp(4), 0, 0)
                }
            )
        }
    }

    private fun createCard(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            background = roundedBackground(white, 18)
            elevation = dp(2).toFloat()
        }
    }

    private fun textView(
        value: String,
        size: Float,
        color: Int,
        bold: Boolean
    ): TextView {
        return TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)

            if (bold) {
                setTypeface(null, Typeface.BOLD)
            }

            gravity = Gravity.CENTER_VERTICAL
        }
    }

    private fun actionButton(
        label: String,
        color: Int,
        onClick: () -> Unit
    ): Button {
        return Button(this).apply {
            text = label
            textSize = 14f
            setTextColor(white)
            isAllCaps = false
            background = roundedBackground(color, 12)
            setPadding(dp(12), dp(6), dp(12), dp(6))

            setOnClickListener {
                onClick()
            }
        }
    }

    private fun buttonLayout(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(50)
        ).apply {
            bottomMargin = dp(10)
        }
    }

    private fun roundedBackground(
        color: Int,
        radius: Int
    ): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
        }
    }

    private fun roundedGradient(
        colors: IntArray,
        radius: Int
    ): GradientDrawable {
        return GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            colors
        ).apply {
            cornerRadius = dp(radius).toFloat()
        }
    }

    private fun requestRequiredPermissions() {
        // SmsHelper requires both permissions: SEND_SMS for outbound messages
        // and READ_PHONE_STATE to inspect the explicitly selected SIM.
        val required = mutableListOf(
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_PHONE_STATE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            required.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missing = required.filter {
            checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            requestPermissions(
                missing.toTypedArray(),
                PERMISSION_REQUEST_CODE
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            updateStatus()
            val sendGranted = checkSelfPermission(Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
            val phoneGranted = checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
            if (sendGranted && phoneGranted) {
                showToast("Required SMS and SIM permissions granted.")
            } else {
                showToast("Permission not granted. Tap Allow Required Permissions at the top of the dashboard to try again.")
            }
        }
    }

    private fun startGatewayService() {
        val smsGranted =
            checkSelfPermission(Manifest.permission.SEND_SMS) ==
                PackageManager.PERMISSION_GRANTED

        val phoneStateGranted =
            checkSelfPermission(Manifest.permission.READ_PHONE_STATE) ==
                PackageManager.PERMISSION_GRANTED

        if (!smsGranted || !phoneStateGranted) {
            updateStatus("Grant SMS and Phone State permissions before starting the gateway.")
            requestRequiredPermissions()
            return
        }

        val serverUrl = SecureConfig.getServerUrl(this)
        val deviceCode = SecureConfig.getDeviceCode(this)
        val activationToken = SecureConfig.getActivationToken(this)
        val deviceToken = SecureConfig.getDeviceToken(this)

        if (
            serverUrl.isNullOrBlank() ||
            deviceCode.isNullOrBlank() ||
            activationToken.isNullOrBlank()
        ) {
            updateStatus(
                "Complete Gateway Settings before starting the gateway."
            )

            showToast(
                "Please save the server URL, Device Code and " +
                    "Activation Token first."
            )
            return
        }

        if (deviceToken.isNullOrBlank()) {
            updateStatus(
                "This device is not activated yet. " +
                    "Activate it in Gateway Settings."
            )

            showToast(
                "Activate this device before starting the gateway."
            )
            return
        }

        try {
            val intent = Intent(
                this,
                GatewayService::class.java
            ).apply {
                action = GatewayService.ACTION_START
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }

            gatewayStartRequested = true

            updateStatus(
                "Gateway service start requested. Waiting for server response."
            )
        } catch (e: Exception) {
            gatewayStartRequested = false

            updateStatus(
                "Could not start service: ${e.localizedMessage}"
            )
        }
    }

    private fun stopGatewayService() {
        // Send the explicit STOP action so GatewayService can persist
        // gateway_enabled_by_user=false before it shuts itself down.
        // Calling stopService() directly would bypass that preference and
        // could allow START_STICKY / boot recovery to restart the Gateway.
        try {
            val stopIntent = Intent(this, GatewayService::class.java).apply {
                action = GatewayService.ACTION_STOP
            }
            startService(stopIntent)
            gatewayStartRequested = false
            updateStatus("Gateway stop requested. Auto-start has been disabled.")
            showToast("Gateway stopping. It will stay off until you start it again.")
        } catch (e: Exception) {
            updateStatus("Could not send Gateway stop request: ${e.localizedMessage ?: "Unknown error"}")
            showToast("Could not stop Gateway. Open the app and try again.")
        }
    }

    private fun openGatewaySettings() {
        try {
            startActivity(
                Intent(
                    this,
                    GatewaySettingsActivity::class.java
                )
            )
        } catch (e: Exception) {
            showToast(
                "Could not open Gateway Settings. Check AndroidManifest.xml. " +
                    (e.localizedMessage ?: "")
            )
        }
    }

    /**
     * SERVER status is based on the latest result recorded by GatewayService.
     *
     * A saved device token alone does not mean the server is currently reachable.
     * GatewayService must write these preferences after its actual polling result:
     *
     * ss_mediaos_gateway_status:
     *   service_running  : Boolean
     *   server_state     : CONNECTED / FAILED / CONNECTING
     *   server_check_at  : Long (System.currentTimeMillis())
     */
    private fun updateStatus(message: String? = null) {
        val smsGranted =
            checkSelfPermission(Manifest.permission.SEND_SMS) ==
                PackageManager.PERMISSION_GRANTED

        val serverUrl = SecureConfig.getServerUrl(this)
        val deviceCode = SecureConfig.getDeviceCode(this)
        val activationToken = SecureConfig.getActivationToken(this)
        val deviceToken = SecureConfig.getDeviceToken(this)

        val serverPrefs = getSharedPreferences(
            SERVER_STATUS_PREFS,
            MODE_PRIVATE
        )

        val serviceRunning = serverPrefs.getBoolean(
            PREF_SERVICE_RUNNING,
            false
        )

        val serverState = serverPrefs.getString(
            PREF_SERVER_STATE,
            SERVER_STATE_CONNECTING
        ) ?: SERVER_STATE_CONNECTING

        val lastCheckAt = serverPrefs.getLong(
            PREF_SERVER_CHECK_AT,
            0L
        )
        val lastGatewayDetail = serverPrefs.getString(
            PREF_LAST_STATUS_MESSAGE,
            ""
        ).orEmpty().trim()
        val lastGatewayDetailAt = serverPrefs.getLong(
            PREF_LAST_STATUS_AT,
            0L
        )

        val now = System.currentTimeMillis()

        val hasServerConfiguration =
            !serverUrl.isNullOrBlank() &&
                !deviceCode.isNullOrBlank() &&
                !activationToken.isNullOrBlank()

        val hasDeviceActivation = !deviceToken.isNullOrBlank()

        // Do not treat a saved token as proof of a live connection.
        val checkIsFresh =
            lastCheckAt > 0L &&
                now >= lastCheckAt &&
                now - lastCheckAt <= STATUS_FRESHNESS_MS

        val serverStatus = when {
            !hasServerConfiguration -> {
                "NOT ACTIVATED"
            }

            !hasDeviceActivation -> {
                "NOT ACTIVATED"
            }

            serviceRunning &&
                checkIsFresh &&
                serverState.equals(
                    SERVER_STATE_CONNECTED,
                    ignoreCase = true
                ) -> {
                "ACTIVATED"
            }

            serviceRunning &&
                checkIsFresh &&
                serverState.equals(
                    SERVER_STATE_FAILED,
                    ignoreCase = true
                ) -> {
                "CONNECTION FAILED"
            }

            serviceRunning -> {
                "CONNECTING"
            }

            checkIsFresh &&
                serverState.equals(
                    SERVER_STATE_FAILED,
                    ignoreCase = true
                ) -> {
                "CONNECTION FAILED"
            }

            else -> {
                "CONNECTING"
            }
        }

        serverText.text =
            "Server: ${serverUrl ?: "Not configured"}"

        val phoneStateGranted = checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
        permissionText.text = if (smsGranted && phoneStateGranted) {
            "SMS & SIM Permission: Granted"
        } else {
            "Required permission missing"
        }

        val notificationGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val allRequiredGranted = smsGranted && phoneStateGranted && notificationGranted
        permissionActionCard?.visibility = if (allRequiredGranted) View.GONE else View.VISIBLE
        permissionActionMessage?.text = when {
            !smsGranted && !phoneStateGranted ->
                "Allow SMS and SIM permissions to send outgoing messages using the selected SIM."
            !smsGranted ->
                "SMS permission is needed to send authorized outgoing messages."
            !phoneStateGranted ->
                "Phone/SIM permission is needed to identify and use the selected SIM."
            else ->
                "Allow notifications so the gateway can show its foreground service status."
        }
        permissionActionButton?.text = if (allRequiredGranted) {
            "Permissions Granted"
        } else {
            "Allow Required Permissions"
        }
        permissionActionButton?.isEnabled = !allRequiredGranted

        statusText.text = buildString {
            appendLine("App Status: Running")

            appendLine(
                if (smsGranted && phoneStateGranted) {
                    "SMS sending and SIM inspection permissions are granted."
                } else {
                    "Grant SEND_SMS and READ_PHONE_STATE permissions before starting the gateway."
                }
            )

            appendLine("Server Status: $serverStatus")

            if (lastCheckAt > 0L) {
                appendLine(
                    "Last server check: " +
                        android.text.format.DateFormat.format(
                            "dd MMM yyyy, hh:mm:ss a",
                            lastCheckAt
                        )
                )
            } else {
                appendLine("Last server check: Not available yet")
            }

            if (lastGatewayDetail.isNotBlank()) {
                appendLine("Gateway detail: $lastGatewayDetail")
                if (lastGatewayDetailAt > 0L) {
                    appendLine(
                        "Detail updated: " +
                            android.text.format.DateFormat.format(
                                "dd MMM yyyy, hh:mm:ss a",
                                lastGatewayDetailAt
                            )
                    )
                }
            }

            if (message != null) {
                appendLine()
                append(message)
            }
        }

        // Dashboard SERVER card
        val serverValue = content.findViewWithTag<TextView>(
            "value_SERVER"
        )

        serverValue?.text = serverStatus

        // Dashboard permission card
        val permissionValue = content.findViewWithTag<TextView>(
            "value_SMS_PERMISSION"
        )

        permissionValue?.text = if (smsGranted && phoneStateGranted) {
            "GRANTED"
        } else {
            "NOT GRANTED"
        }

        gatewayText.text = buildString {
            appendLine(
                if (serviceRunning || gatewayStartRequested) {
                    "Gateway service is running or starting."
                } else {
                    "Gateway service is stopped."
                }
            )

            appendLine(
                if (smsGranted && phoneStateGranted) {
                    "SMS and SIM permissions are available."
                } else {
                    "Grant SEND_SMS and READ_PHONE_STATE before starting the gateway."
                }
            )

            appendLine(
                if (serverUrl.isNullOrBlank()) {
                    "Server URL is not configured."
                } else {
                    "Server URL is configured."
                }
            )

            appendLine(
                when {
                    deviceCode.isNullOrBlank() ||
                        activationToken.isNullOrBlank() -> {
                        "Device Code and Activation Token are not configured."
                    }

                    deviceToken.isNullOrBlank() -> {
                        "Device is not activated. Activate it in Gateway Settings."
                    }

                    else -> {
                        "Device activation token is saved."
                    }
                }
            )

            append("Server connection: $serverStatus")
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(
            this,
            message,
            Toast.LENGTH_LONG
        ).show()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val PERMISSION_REQUEST_CODE = 1001

        // These names must match the values written by GatewayService.kt.
        private const val SERVER_STATUS_PREFS =
            "ss_mediaos_gateway_status"

        private const val PREF_SERVICE_RUNNING =
            "service_running"

        private const val PREF_SERVER_STATE =
            "server_state"

        private const val PREF_SERVER_CHECK_AT =
            "server_check_at"

        private const val PREF_LAST_STATUS_MESSAGE =
            "last_status_message"

        private const val PREF_LAST_STATUS_AT =
            "last_status_at"

        private const val SERVER_STATE_CONNECTED =
            "CONNECTED"

        private const val SERVER_STATE_FAILED =
            "FAILED"

        private const val SERVER_STATE_CONNECTING =
            "CONNECTING"

        // A server check older than 30 seconds is considered stale.
        private const val STATUS_FRESHNESS_MS = 30_000L
    }
}
