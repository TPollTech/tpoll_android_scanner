@echo off
cd /d "%~dp0android_app"
echo Building release APK...
call gradlew.bat assembleRelease
if %ERRORLEVEL% equ 0 (
    cd /d "%~dp0"
    copy /Y "android_app\app\build\outputs\apk\release\app-release.apk" "TPollScanner-release.apk" >nul
    echo.
    echo APK gerado: TPollScanner-release.apk
    echo.
    echo Nao esqueca de:
    echo   1. git add TPollScanner-release.apk update.json android_app/app/build.gradle.kts
    echo   2. git commit -m "release: v1.x.x"
    echo   3. git push
) else (
    echo.
    echo Build falhou!
)
pause
