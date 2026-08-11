# Assinatura e atualizações do Android

## Regra obrigatória

Todos os APKs de produção do pacote `com.tpoll.scanner` precisam usar a mesma
chave de assinatura durante toda a vida do app. Uma build `release` sem a chave
oficial falha de propósito; o projeto não usa mais uma chave de debug como
fallback.

## 1. Criar a chave definitiva uma única vez

Execute em um terminal seguro. O `keytool` pedirá as senhas e os dados do
certificado sem gravá-los no repositório.

```powershell
cd android_app
keytool -genkeypair -v `
  -keystore release.keystore `
  -alias tpoll `
  -keyalg RSA `
  -keysize 4096 `
  -validity 10000
```

Faça imediatamente pelo menos dois backups criptografados, em locais
independentes, da chave e das senhas. Perder essa chave impede a publicação de
novas atualizações do APK distribuído fora da Play Store. O arquivo
`android_app/release.keystore` é ignorado pelo Git e nunca deve ser commitado.

## 2. Configurar builds locais

Adicione estas propriedades ao arquivo pessoal
`%USERPROFILE%\.gradle\gradle.properties`:

```properties
RELEASE_STORE_FILE=C:/Projetos git/tpoll_android_scanner/android_app/release.keystore
RELEASE_STORE_PASSWORD=SENHA_DO_ARQUIVO
RELEASE_KEY_ALIAS=tpoll
RELEASE_KEY_PASSWORD=SENHA_DA_CHAVE
```

Depois, valide:

```powershell
cd android_app
.\gradlew.bat clean assembleRelease
```

## 3. Configurar o GitHub Actions

Veja a impressão digital pública da chave:

```powershell
keytool -list -v -keystore android_app\release.keystore -alias tpoll
```

Copie o valor `SHA256` e configure os cinco secrets abaixo no repositório. O
valor Base64 pode ser produzido sem alterar o arquivo original:

```powershell
$keystoreBase64 = [Convert]::ToBase64String(
  [IO.File]::ReadAllBytes((Resolve-Path 'android_app\release.keystore'))
)
$keystoreBase64 | gh secret set RELEASE_KEYSTORE_BASE64
gh secret set RELEASE_STORE_PASSWORD
gh secret set RELEASE_KEY_ALIAS
gh secret set RELEASE_KEY_PASSWORD
gh secret set RELEASE_CERT_SHA256
```

Secrets obrigatórios:

- `RELEASE_KEYSTORE_BASE64`
- `RELEASE_STORE_PASSWORD`
- `RELEASE_KEY_ALIAS`
- `RELEASE_KEY_PASSWORD`
- `RELEASE_CERT_SHA256`

O workflow valida o alias, recusa certificados `Android Debug` e compara o
certificado do APK pronto com `RELEASE_CERT_SHA256` antes de publicar.

O certificado oficial atual tem SHA-256 público:

```text
603A48C1B31271FE37CF0502F083A741CF5532F170A3FA3617F48CB6A5F0B6D5
```

Compare esse valor com o APK instalado e com cada artefato novo. A impressão
digital não é secreta; a chave privada e as senhas são.

## 4. Processo automático de publicação

Um push de código na `main` executa o workflow de produção. Ele:

> A opção **Enable release immutability** precisa permanecer habilitada nas
> configurações do repositório. O workflow verifica a atestação antes de publicar
> o manifesto.

`release-config.json` declara a versão, a obrigatoriedade e as notas da próxima
publicação. Se `versionCode` ou `versionName` não corresponder à próxima versão
calculada, o processo falha antes de publicar para impedir o reaproveitamento
acidental de metadados antigos.

1. calcula o próximo `versionCode` e a próxima versão semântica;
2. executa testes, lint, R8 e `assembleRelease`;
3. valida pacote, versão, certificado, tamanho e SHA-256;
4. baixa o APK público anterior, instala-o em um emulador, grava dados e uma
   configuração de teste e executa `adb install -r` com o APK novo;
5. abre o app atualizado e confirma que os dados e a configuração permaneceram;
6. cria primeiro uma release em rascunho, anexa e valida o APK, então publica a
   GitHub Release imutável `vX.Y.Z` com o arquivo
   `TPollScanner-X.Y.Z-release.apk`;
7. baixa o ativo pela URL pública, revalida tamanho, SHA-256, certificado e a
   atestação da release imutável;
8. publica `update.json` por último.

Se qualquer etapa falhar, o manifesto público não muda. O log é anexado ao
Actions e não é commitado. APKs e `BUILD_FAILURE.txt` não são artefatos do
repositório; a fonte oficial é o arquivo versionado da GitHub Release.

## 5. Migração das versões antigas

As versões 1.8.3, 1.8.4 e 1.8.5 publicadas pelo workflow foram assinadas por
chaves de debug diferentes. Como as chaves privadas temporárias dos runners não
foram preservadas, esses aparelhos precisam desinstalar o app uma única vez e
instalar o primeiro APK assinado pela nova chave definitiva.

Desinstalar apaga os dados locais do app. Depois dessa migração, não troque mais
a chave: versões seguintes, com `versionCode` maior, instalarão por cima da
versão existente.

## Atualização automática no app

- A opção **Atualizar automaticamente** vem habilitada e agenda uma verificação
  a cada seis horas com WorkManager. Abrir ou retomar o app também verifica se o
  intervalo já venceu.
- O download acontece apenas em rede Wi-Fi/não tarifada e com bateria suficiente.
- Antes da instalação, o app confere conclusão HTTP, tamanho, SHA-256, pacote,
  `versionCode` e o certificado oficial embutido.
- O APK fica no cache privado e só é compartilhado com o instalador oficial por
  `content://`, com permissão temporária de leitura.
- O Android pode exigir a autorização “Permitir desta fonte” e sempre pode pedir
  confirmação final. O app não solicita nem promete instalação silenciosa.
- Quando houver confirmação ou permissão pendente, uma notificação leva o
  usuário à tela correta do sistema.
- `update.json` mantém aliases `snake_case` enquanto clientes até a versão 1.8.13
  forem suportados; os campos canônicos novos usam `camelCase`.

Para atualizações totalmente gerenciadas em aparelhos de consumidores, a opção
mais previsível é publicar pela Google Play com Play App Signing e atualizações
automáticas. Se o mesmo app também continuar disponível como APK direto,
configure o Play App Signing com a sua própria chave para manter a mesma
assinatura nos dois canais.
