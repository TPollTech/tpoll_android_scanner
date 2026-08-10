# TPoll Scanner - App Android

App Android nativo em Kotlin que detecta e remove automaticamente apps maliciosos.

## Funcionalidades

- **Scan automático**: Roda em background a cada X horas (configurável)
- **Detecção dupla**: Heurísticas + base de dados de vírus conhecidos
- **Remoção automática**: Apps de alto risco removidos sem confirmação
- **Notificações**: Alerta quando ameaças são detectadas e removidas
- **Interface Material3**: Design moderno com Jetpack Compose

## Requisitos

- Android 8.0+ (API 26)
- Permissão de notificações (Android 13+)
- Permissão QUERY_ALL_PACKAGES

## Estrutura

```
app/
├── src/main/java/com/tpoll/scanner/
│   ├── MainActivity.kt          # Tela principal
│   ├── TPollApp.kt              # Application class
│   ├── ScanService.kt           # Foreground Service
│   ├── ScanWorker.kt            # WorkManager worker
│   ├── BootReceiver.kt          # Receiver para boot
│   ├── scanner/
│   │   ├── AppAnalyzer.kt       # Análise de apps
│   │   └── RiskScorer.kt        # Motor de scoring
│   ├── remover/
│   │   └── AppRemover.kt        # Remoção de apps
│   ├── model/
│   │   ├── AppFinding.kt        # Modelo de dados
│   │   └── ScanResult.kt        # Resultado do scan
│   ├── notifications/
│   │   └── NotificationHelper.kt # Notificações
│   └── ui/
│       ├── theme/Theme.kt       # Material3 theme
│       └── screens/
│           ├── DashboardScreen.kt
│           ├── HistoryScreen.kt
│           └── SettingsScreen.kt
├── src/main/assets/
│   ├── rules.json               # Regras heurísticas
│   └── virus_db.json            # Base de vírus conhecidos
└── build.gradle.kts
```

## Como Compilar

1. Abrir no Android Studio
2. Sync Gradle
3. Run no dispositivo ou emulador para desenvolvimento

Para validar exatamente o artefato de produção:

```powershell
.\gradlew.bat clean testReleaseUnitTest lintRelease assembleRelease
```

Builds de produção exigem uma chave de assinatura estável. Veja
`../RELEASE_SIGNING.md`; uma build `release` sem essa configuração falha de
propósito para impedir APKs incompatíveis entre atualizações.

O projeto não publica nem distribui APK `debug`. O CI gera apenas o APK
`release`, testa uma atualização por cima da versão pública anterior e só depois
atualiza o manifesto consumido pelo app.

## Atualizador

- A checagem diária usa qualquer conexão e transfere apenas o manifesto pequeno.
- O APK é baixado em um trabalho separado, com rede não tarifada, bateria e
  armazenamento adequados.
- Downloads interrompidos podem continuar do ponto em que pararam.
- O APK só é instalado depois de validar HTTPS, tamanho, SHA-256, pacote,
  `versionCode` e certificado.
- O estado fica persistido e pode ser consultado nas configurações.
- Permissão ou confirmação do sistema gera uma notificação para o usuário.

O Android decide se uma atualização fora da Play Store pode terminar sem ação
do usuário. O app solicita o fluxo com menos interação permitido pela plataforma,
mas não tenta contornar as proteções do sistema.

## Configurações

- **Intervalo de scan**: 1h, 2h, 3h, 6h, 12h, 24h
- **Auto remove HIGH**: Apps com score >= 70 removidos automaticamente
- **Auto remove MEDIUM**: Apps com score 40-69 removidos (opcional)

## Scoring

| Fator | Pontos |
|-------|--------|
| Termos suspeitos no pacote | +12 a +40 |
| Instalador desconhecido | +12 |
| Instalador não confiável | +20 |
| Permissão sensível | +8 a +45 cada |
| AppOp sensível | +15 a +35 cada |
| Combinação perigosa | +20 a +30 |
| Ameaça conhecida | min 80 |

**Thresholds**: >= 70 ALTO | >= 40 MÉDIO | < 40 BAIXO
