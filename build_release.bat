@echo off
cd /d "%~dp0android_app"
echo Building and validating release APK...
call gradlew.bat clean testReleaseUnitTest lintRelease assembleRelease
if %ERRORLEVEL% equ 0 (
    echo.
    echo APK release validado em:
    echo android_app\app\build\outputs\apk\release\app-release.apk
    echo A publicacao oficial e feita pelo GitHub Actions depois do push na main.
) else (
    echo.
    echo Build release falhou!
)
pause
