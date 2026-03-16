@echo off
chcp 65001 >nul
setlocal

net session >nul 2>&1
if %errorlevel% neq 0 (
    echo Требуются права администратора. Запускаю повторно с повышенными правами...
    powershell -NoProfile -ExecutionPolicy Bypass -Command "Start-Process -FilePath '%~f0' -Verb RunAs"
    exit /b
)

echo Запускаю автоматическую настройку HeadacheInsight...
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\bootstrap-windows-choco.ps1"
if errorlevel 1 (
    echo Автоматическая настройка завершилась с ошибкой.
    exit /b %errorlevel%
)

echo.
echo Открываю меню сборки и установки...
call "%~dp0build-headacheinsight.bat"
