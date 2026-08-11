// Copyright (c) 2026 TPoll Tech. Todos os direitos reservados.
// Este código é propriedade exclusiva da TPoll Tech.
// É proibida a cópia, distribuição, modificação ou uso comercial
// sem autorização expressa por escrito do titular dos direitos autorais.
package com.tpoll.scanner.webguard

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.tpoll.scanner.R
import kotlinx.coroutines.runBlocking

class WebWarningActivity : Activity() {

    companion object {
        const val EXTRA_DOMAIN = "web_warning_domain"
        const val EXTRA_CATEGORY = "web_warning_category"
        const val EXTRA_DESCRIPTION = "web_warning_description"
        const val EXTRA_SEVERITY = "web_warning_severity"
        const val EXTRA_BY_VPN = "web_warning_by_vpn"
        const val EXTRA_BY_ACCESSIBILITY = "web_warning_by_accessibility"

        fun createIntent(
            context: Context,
            domain: String,
            category: String,
            description: String,
            severity: String,
            byVpn: Boolean = false,
            byAccessibility: Boolean = true
        ): Intent {
            return Intent(context, WebWarningActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                putExtra(EXTRA_DOMAIN, domain)
                putExtra(EXTRA_CATEGORY, category)
                putExtra(EXTRA_DESCRIPTION, description)
                putExtra(EXTRA_SEVERITY, severity)
                putExtra(EXTRA_BY_VPN, byVpn)
                putExtra(EXTRA_BY_ACCESSIBILITY, byAccessibility)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupWindow()
        setContentView(buildView())
    }

    private fun setupWindow() {
        window.apply {
            setFlags(
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
    }

    private fun buildView(): ScrollView {
        val domain = intent.getStringExtra(EXTRA_DOMAIN) ?: "desconhecido"
        val category = intent.getStringExtra(EXTRA_CATEGORY) ?: "unknown"
        val description = intent.getStringExtra(EXTRA_DESCRIPTION) ?: "Conteúdo potencialmente prejudicial"
        val severity = intent.getStringExtra(EXTRA_SEVERITY) ?: "medium"
        val byVpn = intent.getBooleanExtra(EXTRA_BY_VPN, false)
        val byAccessibility = intent.getBooleanExtra(EXTRA_BY_ACCESSIBILITY, true)

        val primaryColor = ContextCompat.getColor(this, R.color.web_warning_primary)
        val dangerColor = ContextCompat.getColor(this, R.color.web_warning_danger)
        val textPrimary = ContextCompat.getColor(this, R.color.web_warning_text_primary)
        val textSecondary = ContextCompat.getColor(this, R.color.web_warning_text_secondary)
        val bgColor = ContextCompat.getColor(this, R.color.web_warning_background)
        val cardBg = ContextCompat.getColor(this, R.color.web_warning_card)

        val scrollView = ScrollView(this).apply {
            setBackgroundColor(bgColor)
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(48), dp(24), dp(24))
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val warningIcon = TextView(this).apply {
            text = getWarningEmoji(category)
            textSize = 64f
            gravity = Gravity.CENTER
            setPadding(0, dp(16), 0, dp(8))
        }
        container.addView(warningIcon)

        val titleText = TextView(this).apply {
            text = "Site Bloqueado"
            textSize = 26f
            setTextColor(dangerColor)
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(4))
        }
        container.addView(titleText)

        val categoryBadge = TextView(this).apply {
            text = " ${getCategoryLabel(category)} "
            textSize = 14f
            setTextColor(getCategoryColor(category))
            setBackgroundColor(getCategoryColor(category) and 0x1FFFFFFF or 0x30000000)
            setPadding(dp(16), dp(6), dp(16), dp(6))
            gravity = Gravity.CENTER
        }
        val badgeParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.CENTER
            setMargins(0, dp(4), 0, dp(16))
        }
        container.addView(categoryBadge, badgeParams)

        val cardView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(cardBg)
            setPadding(dp(20), dp(20), dp(20), dp(20))
        }
        val cardParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(0, dp(8), 0, dp(8))
        }
        container.addView(cardView, cardParams)

        val domainLabel = TextView(this).apply {
            text = "Site detectado:"
            textSize = 12f
            setTextColor(textSecondary)
        }
        cardView.addView(domainLabel)

        val domainText = TextView(this).apply {
            text = domain
            textSize = 18f
            setTextColor(textPrimary)
            setPadding(0, dp(4), 0, dp(16))
        }
        cardView.addView(domainText)

        val reasonLabel = TextView(this).apply {
            text = "Motivo:"
            textSize = 12f
            setTextColor(textSecondary)
        }
        cardView.addView(reasonLabel)

        val reasonText = TextView(this).apply {
            text = description
            textSize = 14f
            setTextColor(textPrimary)
            setPadding(0, dp(4), 0, dp(8))
        }
        cardView.addView(reasonText)

        val sourceText = TextView(this).apply {
            text = "Detectado por: ${if (byVpn) "VPN + Monitoramento" else "Monitoramento de navegador"}"
            textSize = 11f
            setTextColor(textSecondary)
        }
        cardView.addView(sourceText)

        val severityText = TextView(this).apply {
            val severityLabel = when (severity) {
                "critical" -> "GRAVIDADE: CRÍTICA"
                "high" -> "GRAVIDADE: ALTA"
                "medium" -> "GRAVIDADE: MÉDIA"
                else -> "GRAVIDADE: BAIXA"
            }
            text = severityLabel
            textSize = 12f
            setTextColor(dangerColor)
            setPadding(0, dp(4), 0, dp(0))
        }
        cardView.addView(severityText)

        val safeButton = Button(this).apply {
            text = "Voltar à Segurança"
            setTextColor(ContextCompat.getColor(this@WebWarningActivity, android.R.color.white))
            setBackgroundColor(primaryColor)
            setPadding(dp(16), dp(12), dp(16), dp(12))
            textSize = 16f
            setOnClickListener {
                logBlockedAccess(domain, category, bypassed = false)
                finish()
            }
        }
        val safeParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(0, dp(24), 0, dp(12))
        }
        container.addView(safeButton, safeParams)

        val bypassButton = Button(this).apply {
            text = "Continuar Mesmo Assim"
            setTextColor(dangerColor)
            setBackgroundColor(0x00000000)
            textSize = 14f
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setOnClickListener {
                showBypassConfirmation(domain, category, severity)
            }
        }
        container.addView(bypassButton)

        val disclaimerText = TextView(this).apply {
            text = "O TPoll Guard recomenda não acessar este site.\n" +
                "Sites bloqueados podem conter conteúdo prejudicial,\n" +
                "golpes ou vírus que comprometem seu dispositivo."
            textSize = 11f
            setTextColor(textSecondary and 0x99FFFFFF.toInt())
            gravity = Gravity.CENTER
            setPadding(0, dp(24), 0, dp(16))
        }
        container.addView(disclaimerText)

        scrollView.addView(container)
        return scrollView
    }

    private fun showBypassConfirmation(domain: String, category: String, severity: String) {
        val builder = android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
        builder.setTitle("Aviso de Segurança")
        builder.setMessage(
            "Você tem certeza que deseja acessar \"$domain\"?\n\n" +
            "Esta categoria ($category) é potencialmente perigosa.\n" +
            "O TPoll Guard não se responsabiliza por danos causados\n" +
            "por sites acessados contra a recomendação."
        )
        builder.setPositiveButton("Acessar mesmo assim") { _, _ ->
            logBlockedAccess(domain, category, bypassed = true)
            Toast.makeText(this, "Acesso permitido pelo usuário", Toast.LENGTH_SHORT).show()
            finish()
        }
        builder.setNegativeButton("Voltar") { _, _ ->
            finish()
        }
        builder.setOnCancelListener { finish() }
        builder.show()
    }

    private fun logBlockedAccess(domain: String, category: String, bypassed: Boolean) {
        try {
            val prefs = getSharedPreferences("webprotection_log", Context.MODE_PRIVATE)
            val entries = prefs.getString("blocked_entries", "") ?: ""
            val timestamp = System.currentTimeMillis()
            val entry = "$timestamp|$domain|$category|$bypassed"
            val updated = if (entries.isEmpty()) entry else "$entries;$entry"
            prefs.edit().putString("blocked_entries", updated).apply()

            val todayCount = prefs.getInt("blocked_today", 0) + 1
            prefs.edit().putInt("blocked_today", todayCount).apply()

            val app = application as? com.tpoll.scanner.TPollApp
            app?.notificationHelper?.logWebProtectionEvent(domain, category, bypassed)
        } catch (_: Exception) {}
    }

    private fun getWarningEmoji(category: String): String {
        return when (category) {
            "gambling" -> "\uD83C\uDFB0"
            "adult" -> "\u26A0\uFE0F"
            "phishing" -> "\uD83D\uDEE1\uFE0F"
            "malware" -> "\uD83E\uDDA0"
            "fake_social" -> "\uD83D\uDC7E"
            else -> "\u26A0\uFE0F"
        }
    }

    private fun getCategoryLabel(category: String): String {
        return when (category) {
            "gambling" -> "CASSINO / APOSTAS"
            "adult" -> "CONTEÚDO EXPLÍCITO"
            "phishing" -> "PHISHING / GOLPE"
            "malware" -> "MALWARE / VÍRUS"
            "fake_social" -> "REDE SOCIAL FALSA"
            else -> "SUSPEITO"
        }
    }

    private fun getCategoryColor(category: String): Int {
        return when (category) {
            "gambling" -> 0xFFFFA000.toInt()
            "adult" -> 0xFFE91E63.toInt()
            "phishing" -> 0xFFF44336.toInt()
            "malware" -> 0xFFD32F2F.toInt()
            "fake_social" -> 0xFFFF5722.toInt()
            else -> 0xFFFF9800.toInt()
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}
