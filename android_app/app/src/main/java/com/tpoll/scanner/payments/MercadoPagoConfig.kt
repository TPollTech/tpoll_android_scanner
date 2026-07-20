// Copyright (c) 2026 TPollTech. Todos os direitos reservados.
package com.tpoll.scanner.payments

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Configuração pública da integração Mercado Pago.
 *
 * CLIENT_ID e PUBLIC_KEY podem existir no aplicativo.
 * Nunca coloque ACCESS_TOKEN ou CLIENT_SECRET aqui: pagamentos devem ser criados no backend.
 */
object MercadoPagoConfig {
    const val CLIENT_ID: String = ""
    const val PUBLIC_KEY: String = ""

    /**
     * URL HTTPS do backend/página que cria a preferência e redireciona ao Checkout Pro.
     * Exemplo: https://seu-dominio.com/checkout/tpoll-premium
     */
    const val CHECKOUT_URL: String = ""

    val isCheckoutConfigured: Boolean
        get() = CHECKOUT_URL.startsWith("https://")
}

object MercadoPagoCheckout {
    fun open(context: Context): Boolean {
        if (!MercadoPagoConfig.isCheckoutConfigured) return false

        return runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(MercadoPagoConfig.CHECKOUT_URL)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }
}
