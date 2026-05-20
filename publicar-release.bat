@echo off
title FutTV - Release

echo.
echo  ====================================
echo   FutTV - Publicar nueva version
echo  ====================================
echo.

:: Leer versionName desde build.gradle.kts
powershell -NoProfile -Command "$c=Get-Content 'app/build.gradle.kts' -Raw; if ($c -match 'versionName\s*=\s*""([^""]+)""') { $matches[1] | Out-File '.tmp_ver.txt' -Encoding ASCII -NoNewline }" 2>nul

if not exist .tmp_ver.txt (
    echo ERROR: No se pudo leer la version desde build.gradle.kts
    pause
    exit /b 1
)

set /p VERSION=<.tmp_ver.txt
del .tmp_ver.txt >nul 2>&1

if "%VERSION%"=="" (
    echo ERROR: Version vacia. Revisa build.gradle.kts
    pause
    exit /b 1
)

echo  Version detectada: v%VERSION%
echo.

:: Verificar si el tag ya existe
git rev-parse "v%VERSION%" >nul 2>&1
if %errorlevel% equ 0 (
    echo AVISO: El tag v%VERSION% ya existe.
    echo.
    echo Para republicar, primero ejecuta:
    echo   git tag -d v%VERSION%
    echo   git push origin :refs/tags/v%VERSION%
    echo.
    pause
    exit /b 1
)

:: Commitear build.gradle.kts si tiene cambios sin commitear
git diff --quiet -- app/build.gradle.kts
if %errorlevel% neq 0 (
    echo  Commiteando cambios de version...
    git add app/build.gradle.kts
    git commit -m "bump version to %VERSION%"
    if %errorlevel% neq 0 (
        echo ERROR: Fallo el commit
        pause
        exit /b 1
    )
    echo.
)

:: Push main
echo  Pusheando rama main...
git push origin main
if %errorlevel% neq 0 (
    echo ERROR: No se pudo pushear main
    pause
    exit /b 1
)

:: Crear tag y pushearlo - dispara GitHub Actions
echo.
echo  Creando tag v%VERSION%...
git tag v%VERSION%

echo  Pusheando tag...
git push origin v%VERSION%
if %errorlevel% neq 0 (
    echo ERROR: No se pudo pushear el tag
    git tag -d v%VERSION% >nul 2>&1
    pause
    exit /b 1
)

echo.
echo  ====================================
echo   Listo! GitHub Actions compilando.
echo   https://github.com/XxZman/futtv/actions
echo.
echo   Cuando termine la app va a mostrar
echo   la actualizacion automaticamente.
echo  ====================================
echo.
pause
