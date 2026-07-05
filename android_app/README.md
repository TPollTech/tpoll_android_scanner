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
3. Run no dispositivo ou emulador

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
