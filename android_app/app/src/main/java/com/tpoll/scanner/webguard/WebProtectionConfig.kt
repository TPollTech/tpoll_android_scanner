// Copyright (c) 2026 TPoll Tech. Todos os direitos reservados.
// Este código é propriedade exclusiva da TPoll Tech.
// É proibida a cópia, distribuição, modificação ou uso comercial
// sem autorização expressa por escrito do titular dos direitos autorais.
package com.tpoll.scanner.webguard

import android.content.Context
import android.content.SharedPreferences

class WebProtectionConfig private constructor(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME, Context.MODE_PRIVATE
    )

    fun isVPNEnabled(): Boolean = prefs.getBoolean(KEY_VPN_ENABLED, false)

    fun setVPNEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_VPN_ENABLED, enabled).apply()
    }

    fun isAccessibilityEnabled(): Boolean = prefs.getBoolean(KEY_ACCESSIBILITY_ENABLED, false)

    fun setAccessibilityEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ACCESSIBILITY_ENABLED, enabled).apply()
    }

    fun isCategoryEnabled(category: String): Boolean {
        return prefs.getBoolean("${KEY_CATEGORY_PREFIX}$category", true)
    }

    fun setCategoryEnabled(category: String, enabled: Boolean) {
        prefs.edit().putBoolean("${KEY_CATEGORY_PREFIX}$category", enabled).apply()
    }

    fun getEnabledCategories(): Set<String> {
        val allCategories = setOf("gambling", "adult", "phishing", "malware", "fake_social")
        return allCategories.filter { isCategoryEnabled(it) }.toSet()
    }

    fun isWebProtectionActive(): Boolean {
        return isVPNEnabled() || isAccessibilityEnabled()
    }

    fun getBlockedToday(): Int = prefs.getInt(KEY_BLOCKED_TODAY, 0)

    fun incrementBlockedToday() {
        val current = getBlockedToday()
        prefs.edit().putInt(KEY_BLOCKED_TODAY, current + 1).apply()
    }

    fun resetDailyCount() {
        prefs.edit().putInt(KEY_BLOCKED_TODAY, 0).apply()
    }

    fun isWhitelistedDomain(domain: String): Boolean {
        val whitelist = prefs.getStringSet(KEY_WHITELIST, emptySet()) ?: emptySet()
        return whitelist.contains(domain.lowercase())
    }

    fun addToWhitelist(domain: String) {
        val whitelist = prefs.getStringSet(KEY_WHITELIST, emptySet())?.toMutableSet()
            ?: mutableSetOf()
        whitelist.add(domain.lowercase())
        prefs.edit().putStringSet(KEY_WHITELIST, whitelist).apply()
    }

    fun removeFromWhitelist(domain: String) {
        val whitelist = prefs.getStringSet(KEY_WHITELIST, emptySet())?.toMutableSet()
            ?: mutableSetOf()
        whitelist.remove(domain.lowercase())
        prefs.edit().putStringSet(KEY_WHITELIST, whitelist).apply()
    }

    fun getWhitelist(): Set<String> {
        return prefs.getStringSet(KEY_WHITELIST, emptySet()) ?: emptySet()
    }

    fun getBrowserPackages(): Set<String> {
        return prefs.getStringSet(KEY_BROWSER_PACKAGES, DEFAULT_BROWSER_PACKAGES)
            ?: DEFAULT_BROWSER_PACKAGES
    }

    fun setBrowserPackages(packages: Set<String>) {
        prefs.edit().putStringSet(KEY_BROWSER_PACKAGES, packages).apply()
    }

    companion object {
        private const val PREFS_NAME = "webprotection_config"
        private const val KEY_VPN_ENABLED = "vpn_enabled"
        private const val KEY_ACCESSIBILITY_ENABLED = "accessibility_enabled"
        private const val KEY_CATEGORY_PREFIX = "category_"
        private const val KEY_BLOCKED_TODAY = "blocked_today"
        private const val KEY_WHITELIST = "whitelist_domains"
        private const val KEY_BROWSER_PACKAGES = "browser_packages"

        private val DEFAULT_BROWSER_PACKAGES = setOf(
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
            "com.ecosia.android"
        )

        @Volatile
        private var instance: WebProtectionConfig? = null

        fun getInstance(context: Context): WebProtectionConfig {
            return instance ?: synchronized(this) {
                instance ?: WebProtectionConfig(context.applicationContext).also {
                    instance = it
                }
            }
        }

        fun isVPNEnabled(context: Context): Boolean =
            getInstance(context).isVPNEnabled()

        fun isAccessibilityEnabled(context: Context): Boolean =
            getInstance(context).isAccessibilityEnabled()

        fun isWebProtectionActive(context: Context): Boolean =
            getInstance(context).isWebProtectionActive()

        fun isCategoryEnabled(context: Context, category: String): Boolean =
            getInstance(context).isCategoryEnabled(category)
    }
}
