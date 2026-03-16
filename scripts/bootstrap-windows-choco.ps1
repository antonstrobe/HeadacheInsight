Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new()

$RepoRoot = Split-Path -Parent $PSScriptRoot
$BootstrapScript = Join-Path $PSScriptRoot "bootstrap-windows.ps1"

function Write-Step {
    param(
        [Parameter(Mandatory = $true)][string]$Message
    )

    Write-Host ""
    Write-Host $Message -ForegroundColor Cyan
}

function Ensure-Administrator {
    $currentIdentity = [Security.Principal.WindowsIdentity]::GetCurrent()
    $principal = New-Object Security.Principal.WindowsPrincipal($currentIdentity)
    if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
        throw "Скрипт должен быть запущен с правами администратора."
    }
}

function Ensure-Chocolatey {
    if (Get-Command choco -ErrorAction SilentlyContinue) {
        return
    }

    Write-Step "Chocolatey не найден. Устанавливаю Chocolatey..."
    Set-ExecutionPolicy Bypass -Scope Process -Force
    [System.Net.ServicePointManager]::SecurityProtocol = [System.Net.ServicePointManager]::SecurityProtocol -bor 3072
    Invoke-Expression ((New-Object System.Net.WebClient).DownloadString("https://community.chocolatey.org/install.ps1"))
}

function Ensure-ChocoPackage {
    param(
        [Parameter(Mandatory = $true)][string]$Name
    )

    $installed = choco list --local-only --exact $Name --limit-output 2>$null
    if ($installed -match "^$([regex]::Escape($Name))\|") {
        Write-Host "Пакет $Name уже установлен." -ForegroundColor DarkGray
        return
    }

    Write-Host "Устанавливаю пакет $Name через Chocolatey..." -ForegroundColor Yellow
    choco install $Name -y --no-progress
}

Ensure-Administrator

Write-Step "Шаг 1/4. Проверяю Chocolatey"
Ensure-Chocolatey

Write-Step "Шаг 2/4. Устанавливаю базовые инструменты"
Ensure-ChocoPackage -Name "git"
Ensure-ChocoPackage -Name "python"
Ensure-ChocoPackage -Name "temurin21jdk"

Write-Step "Шаг 3/4. Настраиваю Android toolchain и собираю первую сборку"
& $BootstrapScript

Write-Step "Шаг 4/4. Базовая настройка завершена"
Write-Host "Теперь откроется меню сборки и установки HeadacheInsight." -ForegroundColor Green
