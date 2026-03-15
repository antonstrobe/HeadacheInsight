Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$RepoRoot = Split-Path -Parent $PSScriptRoot
$AndroidSdkRoot = if ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } else { Join-Path $env:LOCALAPPDATA "Android\\Sdk" }
$CmdlineToolsDir = Join-Path $AndroidSdkRoot "cmdline-tools\\latest"
$ToolsBin = Join-Path $CmdlineToolsDir "bin"
$JdkInstallCandidates = @(
    "EclipseAdoptium.Temurin.21.JDK",
    "Microsoft.OpenJDK.21"
)
$CmdlineToolsZip = Join-Path $env:TEMP "commandlinetools-win.zip"
$CmdlineToolsUrl = "https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip"
$GradleWorkRoot = $RepoRoot

function Resolve-JavaHome {
    if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME "bin\\java.exe"))) {
        return $env:JAVA_HOME
    }

    $machineJavaHome = [Environment]::GetEnvironmentVariable("JAVA_HOME", "Machine")
    if ($machineJavaHome -and (Test-Path (Join-Path $machineJavaHome "bin\\java.exe"))) {
        return $machineJavaHome
    }

    $candidates = @(
        (Get-ChildItem "C:\\Program Files\\Eclipse Adoptium" -Directory -ErrorAction SilentlyContinue | Sort-Object Name -Descending | Select-Object -First 1 -ExpandProperty FullName),
        (Get-ChildItem "C:\\Program Files\\Microsoft" -Directory -Filter "jdk-*" -ErrorAction SilentlyContinue | Sort-Object Name -Descending | Select-Object -First 1 -ExpandProperty FullName)
    ) | Where-Object { $_ }

    foreach ($candidate in $candidates) {
        if (Test-Path (Join-Path $candidate "bin\\java.exe")) {
            return $candidate
        }
    }

    throw "JAVA_HOME could not be resolved after JDK installation."
}

function Ensure-WingetPackage {
    param(
        [Parameter(Mandatory = $true)][string]$Id
    )

    $installed = winget list --id $Id --accept-source-agreements 2>$null | Select-String $Id
    if (-not $installed) {
        winget install --id $Id --silent --accept-source-agreements --accept-package-agreements
    }
}

function Ensure-OneOfWingetPackages {
    param(
        [Parameter(Mandatory = $true)][string[]]$Ids
    )

    foreach ($candidate in $Ids) {
        $search = winget search --id $candidate --accept-source-agreements 2>$null
        if ($LASTEXITCODE -eq 0 -and $search) {
            Ensure-WingetPackage -Id $candidate
            return
        }
    }

    throw "No supported JDK package was found via winget. Checked: $($Ids -join ', ')"
}

function Ensure-AndroidCmdlineTools {
    if (Test-Path $ToolsBin) {
        return
    }

    New-Item -ItemType Directory -Force -Path $AndroidSdkRoot | Out-Null
    Invoke-WebRequest -Uri $CmdlineToolsUrl -OutFile $CmdlineToolsZip
    $extractDir = Join-Path $env:TEMP "android-cmdline-tools"
    Remove-Item $extractDir -Recurse -Force -ErrorAction SilentlyContinue
    Expand-Archive -Path $CmdlineToolsZip -DestinationPath $extractDir -Force
    New-Item -ItemType Directory -Force -Path (Split-Path $CmdlineToolsDir -Parent) | Out-Null
    if (Test-Path $CmdlineToolsDir) {
        Remove-Item $CmdlineToolsDir -Recurse -Force
    }
    Move-Item -Path (Join-Path $extractDir "cmdline-tools") -Destination $CmdlineToolsDir
}

function Ensure-AsciiGradlePath {
    param(
        [Parameter(Mandatory = $true)][string]$Path
    )

    if ($Path -notmatch "[^\u0000-\u007F]") {
        return $Path
    }

    $drive = "X:"
    $existing = cmd /c subst
    if ($existing -match "^X:\\: => (.+)$") {
        $mapped = $Matches[1].Trim()
        if ($mapped -ieq $Path) {
            return "$drive\"
        }
        throw "subst drive X: is already mapped to a different path: $mapped"
    }

    cmd /c "subst $drive `"$Path`"" | Out-Null
    return "$drive\"
}

Ensure-OneOfWingetPackages -Ids $JdkInstallCandidates
$ResolvedJavaHome = Resolve-JavaHome
$env:JAVA_HOME = $ResolvedJavaHome
$env:Path = "$ResolvedJavaHome\\bin;$env:Path"
Ensure-AndroidCmdlineTools
$GradleWorkRoot = Ensure-AsciiGradlePath -Path $RepoRoot

$env:ANDROID_SDK_ROOT = $AndroidSdkRoot
$env:ANDROID_HOME = $AndroidSdkRoot
$env:Path = "$ToolsBin;$AndroidSdkRoot\\platform-tools;$env:Path"

& sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0" "ndk;27.2.12479018" "cmake;3.31.1"
"y`ny`ny`ny`ny" | & sdkmanager --licenses | Out-Null

Push-Location (Join-Path $GradleWorkRoot "android-app")
try {
    .\gradlew.bat --no-daemon :app:assembleDemoDebug
} finally {
    Pop-Location
}
