// Copyright (c) 2026 TPoll Tech. Todos os direitos reservados.
// Este código é propriedade exclusiva da TPoll Tech.
// É proibida a cópia, distribuição, modificação ou uso comercial
// sem autorização expressa por escrito do titular dos direitos autorais.
package com.tpoll.scanner.webguard

import android.net.Uri
import java.net.URL
import java.util.Locale

object DomainCategorizer {

    private val URL_PATTERN = Regex(
        """^(https?://)?([a-zA-Z0-9]([a-zA-Z0-9-]*[a-zA-Z0-9])?\.)+[a-zA-Z]{2,}(:\d+)?(/.*)?$"""
    )

    fun extractDomain(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null

        val normalized = if (!trimmed.contains("://")) {
            "https://$trimmed"
        } else {
            trimmed
        }

        return try {
            val uri = Uri.parse(normalized)
            var host = uri.host?.lowercase(Locale.US) ?: return null
            host = host.removePrefix("www.")
                .removePrefix("m.")
                .removePrefix("mobile.")
                .removePrefix("amp.")
            if (host.isEmpty()) null else host
        } catch (_: Exception) {
            extractDomainViaRegex(trimmed)
        }
    }

    private fun extractDomainViaRegex(input: String): String? {
        val match = URL_PATTERN.find(input) ?: return null
        val domain = match.groupValues[2].lowercase(Locale.US)
        return domain.removePrefix("www.")
            .removePrefix("m.")
            .removePrefix("mobile.")
            .removePrefix("amp.")
            .ifEmpty { null }
    }

    fun normalizeDomain(domain: String): String {
        return domain.lowercase(Locale.US)
            .trim()
            .removePrefix("www.")
            .removePrefix("m.")
            .removePrefix("mobile.")
            .removePrefix("amp.")
    }

    fun isNavigablePackage(packageName: String): Boolean {
        return BROWSER_PACKAGES.contains(packageName)
    }

    fun matchesUrl(url: String, vararg patterns: String): Boolean {
        val lowerUrl = url.lowercase(Locale.US)
        return patterns.any { pattern -> lowerUrl.contains(pattern.lowercase(Locale.US)) }
    }

    private val BROWSER_PACKAGES = setOf(
        "com.android.chrome",
        "com.chrome.beta",
        "com.chrome.dev",
        "com.chrome.canary",
        "org.mozilla.firefox",
        "org.mozilla.firefox_beta",
        "com.mozilla.firefox",
        "com.sec.android.app.sbrowser",
        "com.microsoft.emmx",
        "com.opera.browser",
        "com.opera.mini.native",
        "com.brave.browser",
        "com.brave.browser_beta",
        "com.UCMobile.intl",
        "com.UCMobile",
        "com.UCDev",
        "com.UCService",
        "com.yandex.browser",
        "com.yandex.browser.alpha",
        "com.naver.whale",
        "com.kiwibrowser.browser",
        "com.kiwibrowser.browser.dev",
        "org.chromium.webview_shell",
        "com.amazon.cloud9",
        "com.avast.android.securebrowser",
        "com.duckduckgo.mobile.android",
        "com.ghostery.android",
        "com.google.android.apps.chrome",
        "com.joelapenna.foursquared",
        "com.linkbubble.playstore",
        "com.maxthon.browser",
        "com.mybubble.app",
        "com.nice_browser.nice",
        "com.nero.web.boom",
        "com.onedot.themefree",
        "com.opera.mini.native.beta",
        "com.quantumstar.linkbubble",
        "com.rewynd.silverlight",
        "com.sbrave.sbrave",
        "com.silverpinesoftwaresolutions.linkbubbles",
        "com.slidingexploration.demo",
        "com.tenta.android",
        "com.torbrowser.tordroid",
        "com.vimukti.accountmanager",
        "com.vivaldi.browser",
        "com.vivo.browser",
        "com.whale.browser",
        "com.whale.jupiter",
        "com.wv.browser",
        "com.xBrowser.silver",
        "com.zerobugdev.fennec",
        "jp.co.yahoo.android.ynabrowser",
        "com.heytap.browser",
        "com.transsion.hibrowser",
        "com.aspect.happy",
        "com.appsomniacs.da",
        "com.appsomniacs.da2",
        "com.ecosia.android",
        "com.alohamobile.browser",
        "com.betomocho.app",
        "com.binaryapp.browser",
        "com.browsecvpn.android",
        "com.cloudvpn.android",
        "com.hide.me.vpn",
        "com.hotspotshield.android",
        "com.kryptex.miner",
        "com.luxdevapps.hiddenssh",
        "com.orbot.orbot",
        "com.torproxy.tordroid",
        "com.vpnmaster.unlimited",
        "com.vyprvpn.android",
        "me.doze.dnschanger",
        "com.sec.android.app.sbrowser",
        "com.samsung.android.visionintelligence"
    )

    private val URL_KEYWORD_PATTERNS = mapOf(
        "gambling" to listOf(
            "bet", "casino", "poker", "slots", "jackpot", "vegas", "gamble",
            "lottery", "lotto", "sportsbet", "betsafe", "betting", "odds",
            "bookmaker", "wager", "stake", "spin"
        ),
        "adult" to listOf(
            "porn", "xxx", "sex", "nude", "nsfw", "hentai", "adult",
            "erotic", "fetish", "camgirl", "webcam", "onlyfans", "chaturbate",
            "strip", "escort", "hookup", "dating"
        ),
        "phishing" to listOf(
            "secure-login", "verify-account", "confirm-identity", "security-center",
            "account-recovery", "suspicious-activity", "unusual-login",
            "password-reset", "account-unlock", "data-breach", "free-money",
            "free-vbucks", "free-robux", "claim-prize", "you-won"
        ),
        "malware" to listOf(
            "crack", "keygen", "warez", "serial", "patch", "hack",
            "exploit", "trojan", "malware", "virus", "spyware", "keylogger",
            "ransomware", "rootkit", "backdoor", "carding", "phishing-kit"
        )
    )

    fun detectCategoryByKeywords(url: String): String? {
        val lowerUrl = url.lowercase(Locale.US)
        for ((category, keywords) in URL_KEYWORD_PATTERNS) {
            if (keywords.any { lowerUrl.contains(it) }) {
                return category
            }
        }
        return null
    }
}
