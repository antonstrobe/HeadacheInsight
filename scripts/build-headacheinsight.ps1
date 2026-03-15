Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$RepoRoot = Split-Path -Parent $PSScriptRoot
$DefaultJavaHome = "C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot"
$DefaultSdkRoot = if ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } else { Join-Path $env:LOCALAPPDATA "Android\Sdk" }

function Write-Section([string]$Text) {
    Write-Host ""
    Write-Host "=== $Text ===" -ForegroundColor Cyan
}

function Resolve-JavaHome {
    if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME "bin\java.exe"))) {
        return $env:JAVA_HOME
    }
    if (Test-Path (Join-Path $DefaultJavaHome "bin\java.exe")) {
        return $DefaultJavaHome
    }
    $manual = Read-Host "Укажите путь к JDK 21 (Enter = отмена)"
    if ([string]::IsNullOrWhiteSpace($manual)) {
        throw "JDK не указан."
    }
    if (-not (Test-Path (Join-Path $manual "bin\java.exe"))) {
        throw "По этому пути не найден java.exe: $manual"
    }
    return $manual
}

function Resolve-AndroidSdkRoot {
    if ($env:ANDROID_SDK_ROOT -and (Test-Path (Join-Path $env:ANDROID_SDK_ROOT "platform-tools\adb.exe"))) {
        return $env:ANDROID_SDK_ROOT
    }
    if (Test-Path (Join-Path $DefaultSdkRoot "platform-tools\adb.exe")) {
        return $DefaultSdkRoot
    }
    $manual = Read-Host "Укажите путь к Android SDK (Enter = отмена)"
    if ([string]::IsNullOrWhiteSpace($manual)) {
        throw "Android SDK не указан."
    }
    if (-not (Test-Path (Join-Path $manual "platform-tools\adb.exe"))) {
        throw "По этому пути не найден adb.exe: $manual"
    }
    return $manual
}

function Ensure-AsciiGradleRoot([string]$Path) {
    if ($Path -notmatch "[^\u0000-\u007F]") {
        return $Path
    }

    $drives = @("X:", "Y:", "Z:", "W:", "V:")
    $existing = cmd /c subst
    foreach ($drive in $drives) {
        $mapping = $existing | Select-String -SimpleMatch ("$drive\: =>")
        if (-not $mapping) {
            cmd /c "subst $drive `"$Path`"" | Out-Null
            return $drive
        }
        if ($mapping.ToString().Contains($Path)) {
            return $drive
        }
    }

    throw "Не удалось выделить ASCII drive alias для Gradle."
}

function Prepare-Environment {
    $script:JavaHome = Resolve-JavaHome
    $script:AndroidSdkRoot = Resolve-AndroidSdkRoot
    $script:AdbPath = Join-Path $script:AndroidSdkRoot "platform-tools\adb.exe"
    $script:AsciiRoot = Ensure-AsciiGradleRoot $RepoRoot
    $script:AndroidAppDir = Join-Path $script:AsciiRoot "android-app"

    if (-not (Test-Path (Join-Path $script:AndroidAppDir "gradlew.bat"))) {
        throw "Не найден gradlew.bat по пути $script:AndroidAppDir"
    }

    $env:JAVA_HOME = $script:JavaHome
    $env:ANDROID_SDK_ROOT = $script:AndroidSdkRoot
    $env:ANDROID_HOME = $script:AndroidSdkRoot
    $env:Path = "$script:JavaHome\bin;$script:AndroidSdkRoot\platform-tools;$env:Path"
}

function Show-Environment {
    Write-Section "Конфигурация"
    Write-Host "JDK: $script:JavaHome"
    Write-Host "Android SDK: $script:AndroidSdkRoot"
    Write-Host "ADB: $script:AdbPath"
    Write-Host "Gradle path: $script:AndroidAppDir"
}

function Get-DeviceState {
    & $script:AdbPath start-server | Out-Null
    $lines = & $script:AdbPath devices -l
    $devices = @()
    foreach ($line in $lines | Select-Object -Skip 1) {
        if ([string]::IsNullOrWhiteSpace($line)) { continue }
        $parts = $line -split "\s+"
        if ($parts.Length -ge 2) {
            $devices += [pscustomobject]@{
                Id = $parts[0]
                State = $parts[1]
                Raw = $line
            }
        }
    }
    return $devices
}

function Show-PhoneStatus {
    Write-Section "ADB и телефон"
    $devices = Get-DeviceState
    & $script:AdbPath devices -l
    $ready = $devices | Where-Object State -eq "device"
    $unauthorized = $devices | Where-Object State -eq "unauthorized"

    if ($ready) {
        Write-Host "Телефон подключён и готов: $($ready[0].Id)" -ForegroundColor Green
        return $true
    }
    if ($unauthorized) {
        Write-Host "ADB не авторизован: $($unauthorized[0].Id)" -ForegroundColor Yellow
        Write-Host "Разблокируйте экран и подтвердите запрос 'Разрешить отладку по USB'."
        return $false
    }

    Write-Host "Телефон не найден. Проверьте кабель и USB-отладку." -ForegroundColor Yellow
    return $false
}

function Invoke-Gradle([string[]]$Tasks, [string[]]$ExtraArgs = @()) {
    Push-Location $script:AndroidAppDir
    try {
        $arguments = @("--no-daemon") + $Tasks + $ExtraArgs
        & ".\gradlew.bat" @arguments
        if ($LASTEXITCODE -ne 0) {
            throw "Gradle завершился с кодом $LASTEXITCODE"
        }
    } finally {
        Pop-Location
    }
}

function Install-Apk([string]$ApkPath) {
    if (-not (Test-Path $ApkPath)) {
        throw "APK не найден: $ApkPath"
    }
    Write-Section "Установка APK"
    & $script:AdbPath install -r $ApkPath
    if ($LASTEXITCODE -ne 0) {
        throw "ADB install завершился с кодом $LASTEXITCODE"
    }
    Write-Host "APK установлен: $ApkPath" -ForegroundColor Green
}

function Build-Debug([string]$Variant) {
    if (-not (Show-PhoneStatus)) {
        return
    }

    switch ($Variant) {
        "demo" {
            Invoke-Gradle -Tasks @(":app:assembleDemoDebug")
            Install-Apk -ApkPath (Join-Path $script:AndroidAppDir "app\build\outputs\apk\demo\debug\app-demo-debug.apk")
        }
        "prod" {
            Invoke-Gradle -Tasks @(":app:assembleProdDebug")
            Install-Apk -ApkPath (Join-Path $script:AndroidAppDir "app\build\outputs\apk\prod\debug\app-prod-debug.apk")
        }
        default {
            throw "Неизвестный debug variant: $Variant"
        }
    }
}

function Build-Release {
    Write-Section "Production release"
    Write-Host "Можно собрать release без подписи."
    Write-Host "Если укажете keystore, скрипт попробует собрать подписанный APK."

    $keystorePath = Read-Host "Путь к keystore (Enter = пропустить подпись)"
    $extraArgs = @()

    if (-not [string]::IsNullOrWhiteSpace($keystorePath)) {
        if (-not (Test-Path $keystorePath)) {
            throw "Файл keystore не найден: $keystorePath"
        }
        $alias = Read-Host "Alias ключа"
        $storePassword = Read-Host "Пароль хранилища"
        $keyPassword = Read-Host "Пароль ключа (Enter = использовать пароль хранилища)"
        if ([string]::IsNullOrWhiteSpace($keyPassword)) {
            $keyPassword = $storePassword
        }
        if ([string]::IsNullOrWhiteSpace($alias) -or [string]::IsNullOrWhiteSpace($storePassword)) {
            Write-Host "Не все поля подписи заполнены. Сборка продолжится без подписи." -ForegroundColor Yellow
        } else {
            $extraArgs = @(
                "-Pandroid.injected.signing.store.file=$keystorePath",
                "-Pandroid.injected.signing.store.password=$storePassword",
                "-Pandroid.injected.signing.key.alias=$alias",
                "-Pandroid.injected.signing.key.password=$keyPassword"
            )
        }
    }

    Invoke-Gradle -Tasks @(":app:assembleProdRelease", ":app:bundleProdRelease") -ExtraArgs $extraArgs

    $signedApk = Join-Path $script:AndroidAppDir "app\build\outputs\apk\prod\release\app-prod-release.apk"
    $unsignedApk = Join-Path $script:AndroidAppDir "app\build\outputs\apk\prod\release\app-prod-release-unsigned.apk"
    $bundle = Join-Path $script:AndroidAppDir "app\build\outputs\bundle\prodRelease\app-prod-release.aab"

    Write-Host ""
    if (Test-Path $signedApk) { Write-Host "Подписанный APK: $signedApk" -ForegroundColor Green }
    if (Test-Path $unsignedApk) { Write-Host "Неподписанный APK: $unsignedApk" -ForegroundColor Yellow }
    if (Test-Path $bundle) { Write-Host "AAB: $bundle" -ForegroundColor Green }
}

function Run-Bootstrap {
    Write-Section "Bootstrap"
    & powershell -ExecutionPolicy Bypass -File (Join-Path $RepoRoot "scripts\bootstrap-windows.ps1")
}

function Run-IconGeneration {
    Write-Section "Генерация иконок"
    & powershell -ExecutionPolicy Bypass -File (Join-Path $RepoRoot "scripts\generate-android-icons.ps1")
}

function Run-GitHubPublish {
    Write-Section "Публикация в GitHub"
    & powershell -ExecutionPolicy Bypass -File (Join-Path $RepoRoot "scripts\publish-github.ps1")
}

function Show-Menu {
    Write-Host ""
    Write-Host "1. Проверить телефон и USB-отладку"
    Write-Host "2. Собрать demoDebug и установить на телефон"
    Write-Host "3. Собрать prodDebug и установить на телефон"
    Write-Host "4. Собрать production release APK и AAB"
    Write-Host "5. Запустить bootstrap toolchain"
    Write-Host "6. Сгенерировать Android-иконки из img\\app-icon-master.png"
    Write-Host "7. Отправить проект в GitHub (AntonStrobe/HeadacheInsight)"
    Write-Host "Q. Выход"
}

Write-Host "============================================================"
Write-Host "HeadacheInsight - сборка, деплой и проверка телефона"
Write-Host "============================================================"

Prepare-Environment
Show-Environment

while ($true) {
    Show-Menu
    $rawChoice = Read-Host "Выберите действие"
    if ($null -eq $rawChoice) {
        break
    }
    $choice = $rawChoice.Trim().ToUpperInvariant()
    if ([string]::IsNullOrWhiteSpace($choice)) {
        continue
    }
    switch ($choice) {
        "1" { [void](Show-PhoneStatus) }
        "2" { Build-Debug -Variant "demo" }
        "3" { Build-Debug -Variant "prod" }
        "4" { Build-Release }
        "5" { Run-Bootstrap }
        "6" { Run-IconGeneration }
        "7" { Run-GitHubPublish }
        "Q" { break }
        default { Write-Host "Неизвестная команда. Повторите ввод." -ForegroundColor Yellow }
    }
}

Write-Host ""
Write-Host "Выход."


