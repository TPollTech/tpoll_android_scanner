// Copyright (c) 2026 TPollTech. Todos os direitos reservados.
// Este código é propriedade exclusiva da TPollTech.
// É proibida a cópia, distribuição, modificação ou uso comercial
// sem autorização expressa por escrito do titular dos direitos autorais.

package com.tpoll.scanner.security

import java.text.Normalizer
import java.util.Locale

class ScamDetector {

    fun analyze(input: String): ScamAnalysisReport {
        val original = input.trim()
        val text = normalize(original)
        val findings = mutableListOf<ScamFinding>()
        val recommendations = mutableListOf<String>()
        var score = 0

        fun add(points: Int, title: String, detail: String) {
            score += points
            findings.add(ScamFinding(title, detail, points))
        }

        if (original.isBlank()) {
            return ScamAnalysisReport(
                score = 0,
                level = ScamRiskLevel.LOW,
                title = "Cole uma mensagem para analisar",
                summary = "O detector procura sinais comuns de golpe, como urgência, links suspeitos, pedido de senha, Pix e falsa premiação.",
                findings = emptyList(),
                recommendations = listOf("Cole uma mensagem, SMS, e-mail ou texto recebido no WhatsApp para começar.")
            )
        }

        val linkMatches = LINK_REGEX.findAll(original).toList()
        if (linkMatches.isNotEmpty()) {
            add(22, "Contém link", "Golpes costumam tentar levar a vítima para páginas falsas.")
            val lowerLinks = linkMatches.joinToString(" ") { it.value.lowercase(Locale.ROOT) }
            if (SHORTENER_TERMS.any { lowerLinks.contains(it) }) {
                add(18, "Link encurtado", "Links encurtados escondem o endereço real do site.")
            }
        }

        if (URGENT_TERMS.any { text.contains(it) }) {
            add(14, "Usa urgência", "Mensagens com pressa tentam fazer a pessoa agir sem pensar.")
        }

        if (MONEY_TERMS.any { text.contains(it) }) {
            add(14, "Fala de dinheiro, prêmio ou benefício", "Promessas financeiras são comuns em golpes por SMS e WhatsApp.")
        }

        if (BANK_TERMS.any { text.contains(it) }) {
            add(16, "Finge ser banco, cartão ou conta", "Golpes costumam imitar bancos e serviços conhecidos.")
        }

        if (SECRET_TERMS.any { text.contains(it) }) {
            add(24, "Pede senha, código ou dado sensível", "Nunca informe senha, token, código SMS ou dados pessoais por link/mensagem.")
        }

        if (PAYMENT_TERMS.any { text.contains(it) }) {
            add(16, "Cita Pix, boleto ou pagamento", "Cobranças por mensagem precisam ser conferidas direto no app oficial da empresa/banco.")
        }

        if (INSTALL_TERMS.any { text.contains(it) }) {
            add(20, "Pede instalação ou atualização", "Golpes podem pedir APK/app falso para roubar dados.")
        }

        if (SUPPORT_TERMS.any { text.contains(it) }) {
            add(10, "Finge atendimento ou suporte", "Confirme sempre pelos canais oficiais antes de clicar ou responder.")
        }

        if (text.length < 45 && linkMatches.isNotEmpty()) {
            add(10, "Mensagem curta com link", "Textos curtos com link e pouca explicação podem ser isca.")
        }

        if (findings.isEmpty()) {
            recommendations.add("Não encontramos sinais fortes de golpe, mas confira o remetente e evite clicar em links desconhecidos.")
        } else {
            recommendations.add("Não clique no link antes de confirmar a origem por canal oficial.")
            recommendations.add("Não envie senha, código SMS, token, CPF completo ou dados bancários.")
            recommendations.add("Abra o app oficial do banco/empresa manualmente, sem usar o link recebido.")
            recommendations.add("Se tiver dúvida, apague a mensagem ou peça ajuda para alguém de confiança.")
        }

        val level = when {
            score >= 70 -> ScamRiskLevel.HIGH
            score >= 40 -> ScamRiskLevel.SUSPICIOUS
            score >= 18 -> ScamRiskLevel.ATTENTION
            else -> ScamRiskLevel.LOW
        }

        val title = when (level) {
            ScamRiskLevel.LOW -> "Baixo risco aparente"
            ScamRiskLevel.ATTENTION -> "Atenção: sinais suspeitos"
            ScamRiskLevel.SUSPICIOUS -> "Possível golpe"
            ScamRiskLevel.HIGH -> "Alto risco de golpe"
        }

        val summary = when (level) {
            ScamRiskLevel.LOW -> "A mensagem não tem muitos sinais clássicos de golpe, mas ainda vale conferir a origem."
            ScamRiskLevel.ATTENTION -> "A mensagem tem alguns sinais que merecem cuidado antes de clicar ou responder."
            ScamRiskLevel.SUSPICIOUS -> "A mensagem combina vários sinais comuns de golpe. Evite clicar e confirme por canais oficiais."
            ScamRiskLevel.HIGH -> "A mensagem tem forte combinação de sinais de golpe. Não clique, não pague e não informe dados."
        }

        return ScamAnalysisReport(
            score = score.coerceAtMost(100),
            level = level,
            title = title,
            summary = summary,
            findings = findings.sortedByDescending { it.points },
            recommendations = recommendations.distinct()
        )
    }

    private fun normalize(value: String): String {
        return Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace("\\p{Mn}+".toRegex(), "")
            .replace("\n", " ")
            .replace("\t", " ")
    }

    companion object {
        private val LINK_REGEX = Regex("(https?://\\S+|www\\.\\S+|\\b\\S+\\.(com|net|org|br|io|app)\\S*)", RegexOption.IGNORE_CASE)
        private val SHORTENER_TERMS = listOf("bit.ly", "tinyurl", "cutt.ly", "t.co", "encurtador", "is.gd", "rebrand.ly")
        private val URGENT_TERMS = listOf("urgente", "agora", "imediato", "ultima chance", "24 horas", "bloqueado", "suspenso", "cancelado", "regularize", "evite bloqueio")
        private val MONEY_TERMS = listOf("premio", "ganhou", "sorteio", "beneficio", "dinheiro", "reembolso", "resgate", "liberado", "cashback", "indenizacao")
        private val BANK_TERMS = listOf("banco", "cartao", "conta", "nubank", "itau", "bradesco", "santander", "caixa", "banrisul", "serasa", "receita federal")
        private val SECRET_TERMS = listOf("senha", "codigo", "token", "cpf", "rg", "dados", "confirme seus dados", "validar conta", "reconhecimento facial")
        private val PAYMENT_TERMS = listOf("pix", "boleto", "pagamento", "taxa", "multa", "devolucao", "chave pix", "qr code")
        private val INSTALL_TERMS = listOf("instale", "baixar app", "baixe o app", "apk", "atualizacao obrigatoria", "atualize agora")
        private val SUPPORT_TERMS = listOf("suporte", "atendente", "central", "sac", "verificacao", "protocolo")
    }
}

data class ScamAnalysisReport(
    val score: Int,
    val level: ScamRiskLevel,
    val title: String,
    val summary: String,
    val findings: List<ScamFinding>,
    val recommendations: List<String>
)

data class ScamFinding(
    val title: String,
    val detail: String,
    val points: Int
)

enum class ScamRiskLevel(val label: String) {
    LOW("Baixo"),
    ATTENTION("Atenção"),
    SUSPICIOUS("Suspeito"),
    HIGH("Alto risco")
}
