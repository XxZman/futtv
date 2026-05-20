@echo off
chcp 65001 >nul
title FutTV - Publicar actualizacion

echo.
echo  =====================================
echo   FutTV - Publicar nueva actualizacion
echo  =====================================
echo.

:: ── Leer versionName desde build.gradle.kts ──────────────────────────────────
powershell -NoProfile -Command ^
  "$c = Get-Content 'app/build.gradle.kts' -Raw;" ^
  "if ($c -match 'versionName\s*=\s*""([^""]+)""') { $matches[1] | Out-File '.tmp_ver.txt' -Encoding ASCII -NoNewline }"

if not exist .tmp_ver.txt (
    echo [ERROR] No se pudo leer la version desde app/build.gradle.kts
    pause
    exit /b 1
)

set /p VERSION=<.tmp_ver.txt
del .tmp_ver.txt >nul 2>&1

if "%VERSION%"=="" (
    echo [ERROR] La version esta vacia. Revisá build.gradle.kts
    pause
    exit /b 1
)

echo  Version detectada: v%VERSION%
echo.

:: ── Verificar que no exista ya ese tag ───────────────────────────────────────
git rev-parse "v%VERSION%" >nul 2>&1
if %errorlevel% equ 0 (
    echo [AVISO] El tag v%VERSION% ya existe.
    echo.
    echo  Si queres republicar esa version, primero ejecutá:
    echo    git tag -d v%VERSION%
    echo    git push origin :refs/tags/v%VERSION%
    echo.
    pause
    exit /b 1
)

:: ── Commitear build.gradle.kts si tiene cambios sin commitear ────────────────
git diff --quiet -- app/build.gradle.kts
if %errorlevel% neq 0 (
    echo  Hay cambios de version sin commitear. Commiteando...
    git add app/build.gradle.kts
    git commit -m "bump version to %VERSION%"
    if %errorlevel% neq 0 (
        echo [ERROR] Fallo el commit. Revisá el estado de git.
        pause
        exit /b 1
    )
    echo.
)

:: ── Push de main para que el commit llegue antes que el tag ──────────────────
echo  Pusheando rama main...
git push origin main
if %errorlevel% neq 0 (
    echo [ERROR] No se pudo pushear main. Verificá la conexion.
    pause
    exit /b 1
)

:: ── Crear tag y pushearlo (esto dispara GitHub Actions) ──────────────────────
echo.
echo  Creando tag v%VERSION%...
git tag v%VERSION%

echo  Pusheando tag a GitHub (esto dispara la compilacion)...
git push origin v%VERSION%
if %errorlevel% neq 0 (
    echo.
    echo [ERROR] No se pudo pushear el tag.
    git tag -d v%VERSION% >nul 2>&1
    pause
    exit /b 1
)

:: ── Listo ────────────────────────────────────────────────────────────────────
echo.
echo  =========================================
echo   Listo! GitHub Actions esta compilando.
echo.
echo   Seguí el progreso en:
echo   https://github.com/XxZman/futtv/actions
echo.
echo   Cuando termine, la app va a ofrecer la
echo   actualizacion automaticamente.
echo  =========================================
echo.
pause
