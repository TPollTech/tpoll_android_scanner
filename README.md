# TPoll Android App Scanner

Scanner simples para bancada de assistência técnica. Ele usa ADB para listar apps instalados em um celular Android conectado no PC, calcula uma pontuação de risco e permite remover apps suspeitos com confirmação.

## O que faz

- Detecta celulares Android conectados via ADB.
- Lista apps instalados, com foco em apps baixados pelo usuário.
- Analisa nomes/pacotes suspeitos, permissões sensíveis, app-ops e origem do instalador.
- Classifica apps em BAIXO, MÉDIO e ALTO risco.
- Permite selecionar um ou vários apps e remover pelo comando:

```bat
adb shell pm uninstall --user 0 nome.do.pacote
```

- Possui botão **Selecionar alto risco** para marcar automaticamente os apps classificados como ALTO.
- Permite copiar os comandos de remoção para executar manualmente, se preferir.
- Permite forçar parada do app selecionado.
- Exporta relatório em CSV, JSON ou TXT.

## Login com Google

A branch `feature/google-login` já deixa o app preparado para login com Google usando Firebase Authentication + FirebaseUI.

Para ativar de verdade, falta apenas criar o projeto no Firebase, ativar o provedor Google e colocar o arquivo real:

```txt
android_app/app/google-services.json
```

Veja o passo a passo completo em:

```txt
android_app/FIREBASE_LOGIN_SETUP.md
```

## Como usar no Windows

1. Instale Python 3.
2. Baixe o Android SDK Platform Tools, ou coloque o `adb.exe` na pasta do programa.
3. Ative no celular:
   - Opções do desenvolvedor
   - Depuração USB
4. Conecte o celular no PC.
5. Autorize a depuração USB na tela do celular.
6. Execute:

```bat
run.bat
```

7. Clique em **Atualizar**.
8. Clique em **Escanear apps**.
9. Revise os apps marcados como MÉDIO/ALTO.
10. Selecione o app e clique em **Remover selecionado(s)**.

## Segurança

O programa não remove nada sozinho. Ele exige seleção e confirmação antes de desinstalar.

Nem todo app com permissão sensível é vírus. Apps de banco, launcher, VPN, teclado, antivírus, acessibilidade e apps de fabricante podem aparecer com risco por terem permissões fortes. Revise antes de remover.

## Personalização das regras

O arquivo `rules.json` permite ajustar:

- Palavras suspeitas no nome do pacote.
- Instaladores considerados confiáveis.
- Permissões que aumentam a pontuação.
- App-ops que aumentam a pontuação.
