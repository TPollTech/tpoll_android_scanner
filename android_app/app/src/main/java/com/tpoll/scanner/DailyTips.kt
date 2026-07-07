// Copyright (c) 2025 TPoll Tech. Todos os direitos reservados.
// Este código é propriedade exclusiva da TPoll Tech.
// É proibida a cópia, distribuição, modificação ou uso comercial
// sem autorização expressa por escrito do titular dos direitos autorais.
package com.tpoll.scanner

import android.content.Context
import android.content.pm.PackageManager

object DailyTips {

    data class Tip(val title: String, val text: String, val icon: String = "lightbulb")

    fun getRandomTip(context: Context): Tip {
        val tips = mutableListOf(
            Tip("Mantenha-se seguro", "Só baixe apps da Play Store. Apps de fontes desconhecidas podem conter vírus."),
            Tip("Cuidado com permissões", "Um app de lanterna não precisa acessar seus contatos. Revise as permissões regularmente."),
            Tip("Mantenha o Shield ativo", "O Shield em tempo real te protege contra apps que tentam se esconder nas configurações do Android."),
            Tip("Atualizações importantes", "Manter seus apps atualizados corrige falhas de segurança que criminosos usam."),
            Tip("Cuidado com SMS", "Não clique em links de mensagens de números desconhecidos. É a forma mais comum de golpe."),
            Tip("Proteja sua tela", "Use PIN ou senha forte no bloqueio de tela. Padrões são fáceis de descobrir."),
            Tip("Wi-Fi público", "Evite fazer compras ou acessar bancos em redes Wi-Fi abertas. Use VPN se possível."),
            Tip("2FA salva vidas", "Ative verificação em duas etapas no WhatsApp e Google. Impede invasões mesmo se sua senha vazar."),
            Tip("Cuidado com ligações", "Golpistas se passam por bancos por telefone. Nunca forneça senhas ou códigos."),
            Tip("App falso?", "Sempre confira o nome do desenvolvedor antes de instalar. Apps falsos usam nomes parecidos com os originais.")
        )

        try {
            val pm = context.packageManager
            val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            val unknownSources = apps.count {
                try {
                    (it.flags and android.content.pm.ApplicationInfo.FLAG_EXTERNAL_STORAGE) != 0 ||
                    it.packageName.startsWith("com.android.") == false
                } catch (_: Exception) { false }
            }
            if (unknownSources > 3) {
                tips.add(Tip("Você tem apps de fora da Play Store", "$unknownSources apps instalados fora da Play Store podem conter malware. Revise os que não usa mais.", "warning"))
            }

            val hasSmsApps = apps.count { pkg ->
                try {
                    val info = pm.getPackageInfo(pkg.packageName, PackageManager.GET_PERMISSIONS)
                    info?.requestedPermissions?.contains("android.permission.READ_SMS") == true
                } catch (_: Exception) { false }
            }
            if (hasSmsApps > 5) {
                tips.add(Tip("Muitos apps com acesso a SMS", "$hasSmsApps apps podem ler seus SMS. Golpistas usam isso para capturar códigos 2FA.", "warning"))
            }
        } catch (_: Exception) { }

        return tips.random()
    }
}
