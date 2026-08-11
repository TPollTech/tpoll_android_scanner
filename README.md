# TPoll Scanner

App Android da TPollTech com foco em segurança, privacidade e limpeza inteligente para usuários comuns.

A direção do produto é evoluir para um app do tipo:

```txt
Antivírus + Limpeza + Privacidade
```

## O que o app já faz

- Analisa apps instalados.
- Classifica risco de apps suspeitos.
- Usa regras locais em `rules.json`.
- Usa base local `virus_db.json`.
- Mostra permissões sensíveis.
- Tem tela de saúde do dispositivo.
- Tem histórico de scans.
- Tem proteção/Shield em segundo plano.
- Tem login Google via Firebase/FirebaseUI.

## Novo módulo: Limpeza inteligente

A branch `feature/consumer-toolkit-roadmap` adiciona uma nova aba de limpeza para usuário comum.

Ela encontra:

- arquivos grandes;
- fotos duplicadas prováveis;
- vídeos duplicados prováveis;
- mídias do WhatsApp;
- prints/capturas de tela;
- downloads antigos;
- APKs baixados;
- estimativa de espaço recuperável.

Nesta primeira versão, o módulo é seguro e não apaga nada automaticamente. Ele mostra o que revisar. A exclusão com seleção, confirmação e lixeira temporária deve entrar em uma próxima versão.

## Roadmap comercial

Veja o plano completo em:

```txt
ROADMAP_CONSUMER_TOOLKIT.md
```

Resumo da visão:

```txt
TPoll Scanner / TPoll Guard
Antivírus, limpeza e privacidade em um só app.
```

## Login com Google

O app está preparado para login com Google usando Firebase Authentication + FirebaseUI.

Para ativar de verdade, falta apenas criar o projeto no Firebase, ativar o provedor Google e colocar o arquivo real:

```txt
android_app/app/google-services.json
```

Veja o passo a passo completo em:

```txt
android_app/FIREBASE_LOGIN_SETUP.md
```

Para usuário comum, o login deve ser opcional no começo. A conta Google deve ser usada principalmente para histórico, premium, sincronização e recursos futuros.

## Segurança

O app não deve prometer remoção total de vírus ou proteção 100% garantida. O posicionamento recomendado é:

```txt
Análise de apps suspeitos, permissões perigosas e arquivos que ocupam espaço.
```

Sempre que houver limpeza/exclusão, o fluxo deve ser:

1. Analisar.
2. Mostrar achados.
3. Explicar em linguagem simples.
4. Usuário seleciona.
5. Confirmar.
6. Só então apagar ou mover para lixeira.

## Build Android

```bat
cd android_app
gradlew clean testReleaseUnitTest lintRelease assembleRelease
```

Builds `release` exigem a chave definitiva e nunca usam chave de debug. Consulte
[`RELEASE_SIGNING.md`](RELEASE_SIGNING.md) para criar a chave uma única vez,
configurar os secrets do GitHub Actions e entender a migração das versões
antigas.

## Publicação e atualização

O APK de produção é sempre o `release` assinado. A publicação oficial acontece
no GitHub Actions quando um commit de código chega à `main`:

1. executa testes, lint e build `release`;
2. confere pacote, versão, SHA-256 e certificado;
3. instala a versão pública anterior em um emulador Android, grava dados de teste
   e atualiza por cima, confirmando que arquivos e configurações foram preservados;
4. cria uma GitHub Release imutável com o novo APK e valida novamente o arquivo
   baixado pela URL pública;
5. publica `update.json` por último, somente depois de validar tamanho, hash e
   certificado do ativo publicado.

O APK na raiz do repositório não é mais a fonte de distribuição. Cada GitHub
Release publica um nome exclusivo, como `TPollScanner-1.8.13-release.apk`, e a
página consulta `update.json` para apontar ao arquivo da versão atual. Isso evita
confundir uma APK antiga com a atual quando ambas ficam salvas no mesmo aparelho
ou em uma pasta compartilhada de emulador. Falhas de build ficam no resumo e em
um artefato temporário do Actions; elas não geram commits no código.

No app, a verificação acontece ao abrir, ao voltar ao primeiro plano e também em
segundo plano, respeitando cerca de seis horas. O download pesado espera uma rede
não tarifada, bateria e armazenamento adequados, mostra progresso real e valida
tamanho, hash, pacote, versão e assinatura. Só então o APK em cache privado é
compartilhado com o instalador oficial por uma URI `content://` temporária. Em
aparelhos comuns o Android pode exigir a permissão “instalar apps desconhecidos”
e a confirmação final; o app não promete instalação silenciosa.

## Personalização das regras

O arquivo `rules.json` permite ajustar:

- palavras suspeitas no nome do pacote;
- instaladores considerados confiáveis;
- permissões que aumentam a pontuação;
- app-ops que aumentam a pontuação.
