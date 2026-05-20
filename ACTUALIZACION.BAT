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
    echo ERROR: No se pudo leer la version desde build.gradle.kts
    pause
    exit /b 1
)

:: Bajar tags del remoto para saber si la version actual ya fue publicada
echo  Verificando en GitHub...
git fetch --tags --quiet >nul 2>&1

git rev-parse "v!VERSION!" >nul 2>&1
if !errorlevel! equ 0 (

    :: El tag ya existe - subir version automaticamente
    set /a NEWVCODE=!VCODE!+1

    for /f "tokens=1,2 delims=." %%a in ("!VERSION!") do (
        set VMAJOR=%%a
        set VMINOR=%%b
    )
    set /a NEWVMINOR=!VMINOR!+1
    set NEWVERSION=!VMAJOR!.!NEWVMINOR!

    echo  v!VERSION! -^> v!NEWVERSION!
    echo.

    :: Actualizar build.gradle.kts con la nueva version
    powershell -NoProfile -Command "(Get-Content 'app/build.gradle.kts') -replace 'versionCode = \d+','versionCode = !NEWVCODE!' -replace 'versionName = ""[0-9.]+""','versionName = ""!NEWVERSION!""' | Set-Content 'app/build.gradle.kts'"

    set VERSION=!NEWVERSION!
    set VCODE=!NEWVCODE!
)

echo  Publicando v!VERSION!...
echo.

:: Commitear si hay cambios
git diff --quiet -- app/build.gradle.kts
if !errorlevel! neq 0 (
    git add app/build.gradle.kts
    git commit -m "bump version to !VERSION!"
    echo.
)

:: Push main
echo  Pusheando main...
git push origin main
if !errorlevel! neq 0 (
    echo ERROR: No se pudo pushear main
    pause
    exit /b 1
)

:: Crear tag y pushear - dispara GitHub Actions
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
