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
  -alias tpoll-release `
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
RELEASE_KEY_ALIAS=tpoll-release
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
keytool -list -v -keystore android_app\release.keystore -alias tpoll-release
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

## 4. Migração das versões antigas

As versões 1.8.3, 1.8.4 e 1.8.5 publicadas pelo workflow foram assinadas por
chaves de debug diferentes. Como as chaves privadas temporárias dos runners não
foram preservadas, esses aparelhos precisam desinstalar o app uma única vez e
instalar o primeiro APK assinado pela nova chave definitiva.

Desinstalar apaga os dados locais do app. Depois dessa migração, não troque mais
a chave: versões seguintes, com `versionCode` maior, instalarão por cima da
versão existente.

## Atualização automática no app

- A opção **Atualizar automaticamente** vem habilitada e agenda uma verificação
  diária com WorkManager.
- O download acontece apenas em rede Wi-Fi/não tarifada e com bateria suficiente.
- Antes da instalação, o app confere HTTPS, pacote, `versionCode` e certificado.
- No Android 12 ou superior, o app solicita instalação sem interação. O Android
  ainda pode exigir confirmação conforme a versão e a política do aparelho.
- Quando houver confirmação ou permissão pendente, uma notificação leva o
  usuário à tela correta do sistema.
- No Android 11 ou anterior, a confirmação de instalação é obrigatória.

Para atualizações totalmente gerenciadas em aparelhos de consumidores, a opção
mais previsível é publicar pela Google Play com Play App Signing e atualizações
automáticas. Se o mesmo app também continuar disponível como APK direto,
configure o Play App Signing com a sua própria chave para manter a mesma
assinatura nos dois canais.
