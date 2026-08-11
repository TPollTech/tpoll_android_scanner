// Copyright (c) 2026 TPoll Tech. Todos os direitos reservados.
// Este código é propriedade exclusiva da TPoll Tech.
// É proibida a cópia, distribuição, modificação ou uso comercial
// sem autorização expressa por escrito do titular dos direitos autorais.
package com.tpoll.scanner.webguard

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.tpoll.scanner.TPollApp
import kotlinx.coroutines.*

class URLMonitorService : AccessibilityService() {

    companion object {
        private var instance: URLMonitorService? = null
        private var isMonitoring = false
        private var blockedToday = 0
        private val BROWSER_PACKAGES = setOf(
            "com.android.chrome",
            "com.chrome.beta",
            "org.mozilla.firefox",
            "org.mozilla.firefox_beta",
            "com.sec.android.app.sbrowser",
            "com.microsoft.emmx",
            "com.opera.browser",
            "com.opera.mini.native",
            "com.brave.browser",
            "com.brave.browser_beta",
            "com.UCMobile.intl",
            "com.UCMobile",
            "com.yandex.browser",
            "com.naver.whale",
            "com.kiwibrowser.browser",
            "com.duckduckgo.mobile.android",
            "com.vivaldi.browser",
            "com.ecosia.android",
            "com.amazon.cloud9"
        )

        private val URL_PATTERNS = listOf(
            "http://", "https://", "www.",
            "google.com", "youtube.com", "facebook.com", "twitter.com",
            "instagram.com", "tiktok.com", "reddit.com"
        )

        fun isMonitoring(): Boolean = isMonitoring
        fun getBlockedToday(): Int = blockedToday

        fun openAccessibilitySettings(context: Context) {
            val intent = Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }

        fun isAccessibilityEnabled(context: Context): Boolean {
            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager
            val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC)
            return enabledServices.any {
                it.resolveInfo.serviceInfo?.name == "com.tpoll.scanner.webguard.URLMonitorService"
            }
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var blocklistDb: URLBlocklistDatabase? = null
    private var lastCheckedUrl = ""
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var debounceJob: Job? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        blocklistDb = URLBlocklistDatabase.getInstance(this)

        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_DEFAULT or
                AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 200
        }

        isMonitoring = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!WebProtectionConfig.isAccessibilityEnabled(this)) return

        event?.let { processEvent(it) }
    }

    private fun processEvent(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString() ?: return

        if (packageName == this.packageName) return
        if (!isBrowserPackage(packageName)) return

        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(300)
            checkCurrentUrl(event, packageName)
        }
    }

    private fun checkCurrentUrl(event: AccessibilityEvent, packageName: String) {
        try {
            val rootNode = rootInActiveWindow ?: return
            val url = extractUrlFromNode(rootNode) ?: extractUrlFromEventData(event)
            rootNode.recycle()

            if (url.isNullOrBlank()) return
            if (url == lastCheckedUrl) return
            lastCheckedUrl = url

            val domain = DomainCategorizer.extractDomain(url) ?: return
            val blockResult = blocklistDb?.checkDomain(domain)

            if (blockResult != null) {
                blockedToday++
                showWarning(blockResult)
            }
        } catch (_: Exception) {}
    }

    private fun extractUrlFromNode(node: AccessibilityNodeInfo): String? {
        val urlPatterns = listOf(
            "url_bar", "url", "search", "address", "location_bar",
            "omnibox", "edit_url", "title_bar", "web_address"
        )

        for (pattern in urlPatterns) {
            val nodes = node.findAccessibilityNodeInfosByViewId(pattern)
            for (urlNode in nodes) {
                val text = urlNode.text?.toString()
                if (!text.isNullOrBlank() && looksLikeUrl(text)) {
                    return text
                }
            }
        }

        extractUrlFromTextNodes(node)?.let { return it }

        return null
    }

    private fun extractUrlFromTextNodes(node: AccessibilityNodeInfo): String? {
        if (node.text != null) {
            val text = node.text.toString()
            if (looksLikeUrl(text)) return text
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = extractUrlFromTextNodes(child)
            if (result != null) return result
        }

        return null
    }

    private fun extractUrlFromEventData(event: AccessibilityEvent): String? {
        val text = event.text?.joinToString(" ") ?: return null
        if (looksLikeUrl(text)) return text

        val description = event.contentDescription?.toString()
        if (description != null && looksLikeUrl(description)) return description

        return null
    }

    private fun looksLikeUrl(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.length < 5) return false
        if (trimmed.contains(" ") && !trimmed.contains("://")) return false
        return URL_PATTERNS.any { trimmed.lowercase().contains(it) } ||
            trimmed.matches(Regex("^https?://.*")) ||
            trimmed.matches(Regex("^www\\..*\\.[a-z]{2,}.*"))
    }

    private fun isBrowserPackage(packageName: String): Boolean {
        return BROWSER_PACKAGES.contains(packageName)
    }

    private fun showWarning(blockResult: BlockResult) {
        handler.post {
            try {
                val intent = WebWarningActivity.createIntent(
                    context = this,
                    domain = blockResult.domain,
                    category = blockResult.category,
                    description = blockResult.categoryDescription,
                    severity = blockResult.severity,
                    byVpn = WebBlockerVPNService.isRunning(),
                    byAccessibility = true
                )
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            } catch (_: Exception) {}
        }

        try {
            val app = application as TPollApp
            app.notificationHelper.showWebProtectionAlert(
                domain = blockResult.domain,
                category = blockResult.categoryDescription,
                severity = blockResult.severity
            )
        } catch (_: Exception) {}

        logBlockedAccess(blockResult)
    }

    private fun logBlockedAccess(blockResult: BlockResult) {
        try {
            val prefs = getSharedPreferences("webprotection_log", MODE_PRIVATE)
            val entries = prefs.getString("blocked_entries", "") ?: ""
            val timestamp = System.currentTimeMillis()
            val entry = "$timestamp|${blockResult.domain}|${blockResult.category}|false"
            val updated = if (entries.isEmpty()) entry else "$entries;$entry"
            prefs.edit().putString("blocked_entries", updated).apply()
            prefs.edit().putInt("blocked_today", blockedToday).apply()
        } catch (_: Exception) {}
    }

    override fun onInterrupt() {
        isMonitoring = false
    }

    override fun onDestroy() {
        super.onDestroy()
        isMonitoring = false
        instance = null
        scope.cancel()
    }
}
