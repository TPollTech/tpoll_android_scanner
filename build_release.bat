@echo off
cd /d "%~dp0android_app"
echo Building release APK...
call gradlew.bat assembleRelease
if %ERRORLEVEL% equ 0 (
    echo.
    echo Success! APK generated.
    copy /Y "app\build\outputs\apk\release\app-release.apk" "..\TPollScanner-release.apk" >nul
    echo Copied to: TPollScanner-release.apk
    echo.
    echo IMPORTANTE: Antes de distribuir, crie uma GitHub Release:
    echo 1. Faça upload do TPollScanner-release.apk
    echo 2. Atualize o update.json com a nova versao e changelog
    echo 3. O app detectara a atualizacao automaticamente
) else (
    echo.
    echo Build failed. Check errors above.
)
pause
