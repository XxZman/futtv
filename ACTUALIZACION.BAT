@echo off
setlocal enabledelayedexpansion
title FutTV - Release

echo.
echo  ====================================
echo   FutTV - Publicar nueva version
echo  ====================================
echo.

:: Leer versionName y versionCode desde build.gradle.kts
for /f "tokens=2 delims==" %%a in ('findstr /C:"versionName = " app\build.gradle.kts') do set VERRAW=%%a
for /f "tokens=2 delims==" %%a in ('findstr /C:"versionCode = " app\build.gradle.kts') do set VCRAW=%%a

set VERSION=!VERRAW:"=!
set VERSION=!VERSION: =!
set VCODE=!VCRAW: =!

if "!VERSION!"=="" (
    echo ERROR: No se pudo leer la version
    echo Revisa que app\build.gradle.kts tenga el formato correcto
    echo    versionCode = 2
    echo    versionName = "1.1"
    pause
    exit /b 1
)

echo  Version actual: v!VERSION! (versionCode !VCODE!)
echo.

:: Bajar todos los tags del remoto
echo  Verificando en GitHub...
git fetch --tags --quiet >nul 2>&1

:: Loop: seguir subiendo version hasta encontrar una sin publicar
:checkloop
git rev-parse "v!VERSION!" >nul 2>&1
if !errorlevel! equ 0 (
    set /a NEWVCODE=!VCODE!+1
    for /f "tokens=1,2 delims=." %%a in ("!VERSION!") do (
        set VMAJOR=%%a
        set VMINOR=%%b
    )
    set /a NEWVMINOR=!VMINOR!+1
    set NEWVERSION=!VMAJOR!.!NEWVMINOR!
    echo  v!VERSION! ya publicada -^> v!NEWVERSION!

    :: Reescribir build.gradle.kts linea por linea (sin PowerShell)
    if exist app\build.gradle.kts.tmp del app\build.gradle.kts.tmp >nul 2>&1
    for /f "usebackq delims=" %%L in ("app\build.gradle.kts") do (
        set LINE=%%L
        set TRIMMED=!LINE: =!
        if "!TRIMMED:~0,12!"=="versionCode=" (
            echo         versionCode = !NEWVCODE!>> app\build.gradle.kts.tmp
        ) else if "!TRIMMED:~0,12!"=="versionName=" (
            echo         versionName = "!NEWVERSION!">> app\build.gradle.kts.tmp
        ) else (
            echo !LINE!>> app\build.gradle.kts.tmp
        )
    )
    move /y app\build.gradle.kts.tmp app\build.gradle.kts >nul 2>&1

    set VERSION=!NEWVERSION!
    set VCODE=!NEWVCODE!
    goto checkloop
)

echo.
echo  Publicando v!VERSION!...
echo.

:: Commitear si hay cambios
git diff --quiet -- app/build.gradle.kts
if !errorlevel! neq 0 (
    git add app/build.gradle.kts
    git commit -m "bump version to !VERSION!"
    echo.
)

:: Sincronizar con remoto antes de pushear
git pull --rebase origin main >nul 2>&1

:: Push main
echo  Pusheando main...
git push origin main
if !errorlevel! neq 0 (
    echo ERROR: No se pudo pushear main
    pause
    exit /b 1
)

:: Crear tag y pushear
echo  Creando tag v!VERSION!...
git tag v!VERSION!
git push origin v!VERSION!
if !errorlevel! neq 0 (
    echo ERROR: No se pudo pushear el tag
    git tag -d v!VERSION! >nul 2>&1
    pause
    exit /b 1
)

echo.
echo  ====================================
echo   Compilando v!VERSION!
echo   https://github.com/XxZman/futtv/actions
echo.
echo   La app ofrecera la actualizacion
echo   automaticamente al terminar.
echo  ====================================
echo.
pause
