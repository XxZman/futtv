@echo off
title FutTV - Release

echo.
echo  ====================================
echo   FutTV - Publicar nueva version
echo  ====================================
echo.

:: Leer versionName desde build.gradle.kts usando findstr (sin PowerShell)
for /f "tokens=2 delims==" %%a in ('findstr /C:"versionName = " app\build.gradle.kts') do set VERRAW=%%a

if "%VERRAW%"=="" (
    echo ERROR: No se pudo leer la version desde build.gradle.kts
    pause
    exit /b 1
)

:: Limpiar comillas y espacios  (ej: " "1.2"" -> 1.2)
set VERSION=%VERRAW:"=%
set VERSION=%VERSION: =%

if "%VERSION%"=="" (
    echo ERROR: Version vacia. Revisa versionName en build.gradle.kts
    pause
    exit /b 1
)

echo  Version detectada: v%VERSION%
echo.

:: Bajar tags del remoto para detectar si ya existe alli tambien
echo  Verificando tags en GitHub...
git fetch --tags --quiet >nul 2>&1

git rev-parse "v%VERSION%" >nul 2>&1
if %errorlevel% equ 0 (
    echo.
    echo  La version v%VERSION% ya fue publicada.
    echo.
    echo  Sube el numero en app\build.gradle.kts:
    echo    versionCode = X       (suma 1)
    echo    versionName = "X.X"   (nueva version)
    echo.
    echo  Luego volvé a abrir este script.
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
