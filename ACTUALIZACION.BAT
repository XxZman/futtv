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

:: Bajar todos los tags del remoto
echo  Verificando en GitHub...
git fetch --tags --quiet >nul 2>&1
echo.

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
    echo  v!VERSION! ya publicada -^> subiendo a v!NEWVERSION!

    :: Escribir script PS a archivo temporal para evitar problemas de escaping
    echo $c = Get-Content 'app/build.gradle.kts' > .tmp_ver.ps1
    echo $c = $c -replace 'versionCode = \d+', 'versionCode = !NEWVCODE!' >> .tmp_ver.ps1
    echo $c = $c -replace 'versionName = "[0-9.]+"', 'versionName = "!NEWVERSION!"' >> .tmp_ver.ps1
    echo [System.IO.File]::WriteAllLines('app/build.gradle.kts', $c, [System.Text.UTF8Encoding]::new($false)) >> .tmp_ver.ps1
    powershell -NoProfile -ExecutionPolicy Bypass -File .tmp_ver.ps1
    del .tmp_ver.ps1 >nul 2>&1

    set VERSION=!NEWVERSION!
    set VCODE=!NEWVCODE!
    goto checkloop
)

echo  Publicando v!VERSION!...
echo.

:: Commitear build.gradle.kts si tiene cambios
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
