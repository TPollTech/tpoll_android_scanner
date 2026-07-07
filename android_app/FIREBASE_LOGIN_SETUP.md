# Configurar login com Google no TPoll Scanner

O app está preparado para login com Google usando Firebase Authentication + FirebaseUI.

A parte que precisa ser feita manualmente é criar o projeto no Firebase e adicionar o arquivo real `google-services.json`.

## 1. Criar projeto no Firebase

1. Acesse o Firebase Console.
2. Crie um projeto para o TPoll Scanner.
3. Vá em **Authentication**.
4. Entre em **Sign-in method**.
5. Ative o provedor **Google**.

## 2. Adicionar app Android no Firebase

No Firebase, adicione um app Android com este package name:

```txt
com.tpoll.scanner
```

Depois cadastre o SHA-1 e, se possível, também o SHA-256 da assinatura usada para gerar o APK.

## 3. Baixar o google-services.json

Baixe o arquivo `google-services.json` gerado pelo Firebase e coloque exatamente em:

```txt
android_app/app/google-services.json
```

Este arquivo real não deve ser enviado para o GitHub. Ele está no `.gitignore` por segurança.

## 4. Compilar o app

Depois de colocar o arquivo real, rode o build normalmente:

```bat
cd android_app
gradlew assembleDebug
```

Para release, use seu processo atual de assinatura.

## 5. Como o app se comporta

- Sem `google-services.json`: o app abre uma tela avisando que a configuração Firebase está pendente.
- Com `google-services.json`: o app mostra a tela **Entrar com Google**.
- Após login: libera o `MainScreen` normal do TPoll Scanner.
- No topo do app aparece um botão de sair.
- Se o login falhar, o app mostra o erro na própria tela de login.

## 6. Se clicar em Entrar com Google e voltar sem logar

Quase sempre é um destes pontos:

1. O provedor **Google** não foi ativado no Firebase Authentication.
2. O package name no Firebase não é exatamente `com.tpoll.scanner`.
3. O SHA-1 do APK debug não foi cadastrado no Firebase.
4. O SHA-256 do APK debug não foi cadastrado no Firebase.
5. O `google-services.json` foi baixado antes de cadastrar o SHA e precisa ser baixado novamente.

Para pegar o SHA-1/SHA-256 do debug:

```bat
cd android_app
gradlew signingReport
```

Procure a seção:

```txt
Variant: debug
```

Copie os valores:

```txt
SHA1: ...
SHA-256: ...
```

Depois vá no Firebase:

```txt
Configurações do projeto > Seus apps > Android > Impressões digitais SHA
```

Adicione o SHA-1 e o SHA-256, salve, baixe novamente o `google-services.json` e substitua o arquivo em:

```txt
android_app/app/google-services.json
```

Depois rode de novo:

```bat
cd android_app
gradlew clean assembleDebug
```

## Arquivos alterados

- `android_app/build.gradle.kts`
- `android_app/app/build.gradle.kts`
- `android_app/app/src/main/java/com/tpoll/scanner/MainActivity.kt`
- `.gitignore`
- `android_app/app/google-services.json.example`

## Observação importante

Esse login impede o uso normal do app por quem não estiver logado, mas não é uma proteção forte contra APK modificado. Para bloquear por e-mail autorizado, o ideal é adicionar uma lista de e-mails permitidos no Firebase/Firestore ou em um backend próprio.
