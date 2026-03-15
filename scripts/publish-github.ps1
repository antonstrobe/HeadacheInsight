param(
    [string]$Owner = "AntonStrobe",
    [string]$RepoName = "HeadacheInsight"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$RepoRoot = Split-Path -Parent $PSScriptRoot
$GhCandidates = @(
    "gh",
    "C:\Program Files\GitHub CLI\gh.exe"
)

function Write-Section([string]$Text) {
    Write-Host ""
    Write-Host "=== $Text ===" -ForegroundColor Cyan
}

function Resolve-Gh {
    foreach ($candidate in $GhCandidates) {
        try {
            $null = & $candidate --version 2>$null
            if ($LASTEXITCODE -eq 0) {
                return $candidate
            }
        } catch {
        }
    }
    throw "GitHub CLI не найден. Установите gh или используйте bootstrap/winget."
}

function Require-GhAuth([string]$GhPath) {
    & $GhPath auth status
    if ($LASTEXITCODE -eq 0) {
        return
    }

    Write-Host "GitHub CLI установлен, но вход не выполнен." -ForegroundColor Yellow
    Write-Host "Запустите:" -ForegroundColor Yellow
    Write-Host "  gh auth login" -ForegroundColor Yellow
    throw "Нужна авторизация GitHub."
}

function Ensure-GitIdentity {
    $name = (git config --global user.name) 2>$null
    $email = (git config --global user.email) 2>$null

    if ([string]::IsNullOrWhiteSpace($name)) {
        $name = Read-Host "Введите git user.name"
        if (-not [string]::IsNullOrWhiteSpace($name)) {
            git config --global user.name $name
        }
    }
    if ([string]::IsNullOrWhiteSpace($email)) {
        $email = Read-Host "Введите git user.email"
        if (-not [string]::IsNullOrWhiteSpace($email)) {
            git config --global user.email $email
        }
    }

    $name = (git config --global user.name) 2>$null
    $email = (git config --global user.email) 2>$null
    if ([string]::IsNullOrWhiteSpace($name) -or [string]::IsNullOrWhiteSpace($email)) {
        throw "Git identity не настроен."
    }
}

function Ensure-Commit {
    $status = git status --short
    if (-not $status) {
        Write-Host "Новых изменений для коммита нет." -ForegroundColor Green
        return
    }

    $message = Read-Host "Сообщение коммита (Enter = Initial HeadacheInsight import)"
    if ([string]::IsNullOrWhiteSpace($message)) {
        $message = "Initial HeadacheInsight import"
    }

    git add .
    git commit -m $message
}

function Ensure-MainBranch {
    $branch = git branch --show-current
    if ($branch -eq "master") {
        git branch -M main
    }
}

function Ensure-Origin([string]$GhPath, [string]$OwnerName, [string]$TargetRepo, [string]$Visibility) {
    $origin = (git remote get-url origin) 2>$null
    if (-not [string]::IsNullOrWhiteSpace($origin)) {
        return $origin
    }

    $repoSlug = "$OwnerName/$TargetRepo"
    & $GhPath repo view $repoSlug --json name 1>$null 2>$null
    if ($LASTEXITCODE -ne 0) {
        Write-Section "Создание репозитория GitHub"
        & $GhPath repo create $repoSlug "--$Visibility" --confirm
        if ($LASTEXITCODE -ne 0) {
            throw "Не удалось создать репозиторий $repoSlug"
        }
    }

    $remoteUrl = "https://github.com/$repoSlug.git"
    git remote add origin $remoteUrl
    return $remoteUrl
}

function Get-ReleaseAssets {
    $assets = @()
    $candidates = @(
        (Join-Path $RepoRoot "android-app\app\build\outputs\apk\demo\debug\app-demo-debug.apk"),
        (Join-Path $RepoRoot "android-app\app\build\outputs\apk\prod\debug\app-prod-debug.apk"),
        (Join-Path $RepoRoot "android-app\app\build\outputs\apk\prod\release\app-prod-release.apk"),
        (Join-Path $RepoRoot "android-app\app\build\outputs\bundle\prodRelease\app-prod-release.aab")
    )
    foreach ($candidate in $candidates) {
        if (Test-Path $candidate) {
            $assets += $candidate
        }
    }
    return $assets
}

function Publish-ReleaseAssets([string]$GhPath, [string]$OwnerName, [string]$TargetRepo) {
    $assets = Get-ReleaseAssets
    if (-not $assets) {
        Write-Host "Готовых APK/AAB пока нет. GitHub Release пропущен." -ForegroundColor Yellow
        return
    }

    Write-Section "GitHub Release"
    Write-Host "Найдены артефакты:" -ForegroundColor Green
    foreach ($asset in $assets) {
        Write-Host " - $asset"
    }

    $tag = Read-Host "Тег релиза (Enter = v0.1.0-mvp-preview)"
    if ([string]::IsNullOrWhiteSpace($tag)) {
        $tag = "v0.1.0-mvp-preview"
    }

    $title = Read-Host "Заголовок релиза (Enter = HeadacheInsight MVP Preview)"
    if ([string]::IsNullOrWhiteSpace($title)) {
        $title = "HeadacheInsight MVP Preview"
    }

    $notes = @"
HeadacheInsight MVP preview release.

Included assets:
$(($assets | ForEach-Object { "- $([System.IO.Path]::GetFileName($_))" }) -join [Environment]::NewLine)
"@

    $repoSlug = "$OwnerName/$TargetRepo"
    & $GhPath release view $tag --repo $repoSlug 1>$null 2>$null
    if ($LASTEXITCODE -ne 0) {
        & $GhPath release create $tag @assets --repo $repoSlug --title $title --notes $notes
        if ($LASTEXITCODE -ne 0) {
            throw "Не удалось создать GitHub Release $tag"
        }
    } else {
        & $GhPath release upload $tag @assets --repo $repoSlug --clobber
        if ($LASTEXITCODE -ne 0) {
            throw "Не удалось загрузить артефакты в существующий релиз $tag"
        }
    }

    Write-Host ""
    Write-Host "Ссылки на скачивание:" -ForegroundColor Green
    foreach ($asset in $assets) {
        $fileName = [System.IO.Path]::GetFileName($asset)
        Write-Host "https://github.com/$repoSlug/releases/download/$tag/$fileName"
    }
}

Push-Location $RepoRoot
try {
    Write-Host "============================================================"
    Write-Host "HeadacheInsight - публикация в GitHub"
    Write-Host "============================================================"

    $gh = Resolve-Gh
    Require-GhAuth -GhPath $gh
    Ensure-GitIdentity
    Ensure-MainBranch

    $visibilityInput = Read-Host "Видимость репозитория: private/public (Enter = private)"
    $visibility = if ($visibilityInput -and $visibilityInput.Trim().ToLowerInvariant() -eq "public") { "public" } else { "private" }

    $remote = Ensure-Origin -GhPath $gh -OwnerName $Owner -TargetRepo $RepoName -Visibility $visibility
    Ensure-Commit

    Write-Section "Push"
    git push -u origin main
    if ($LASTEXITCODE -ne 0) {
        throw "git push завершился с ошибкой."
    }

    Publish-ReleaseAssets -GhPath $gh -OwnerName $Owner -TargetRepo $RepoName

    Write-Host ""
    Write-Host "Проект опубликован: https://github.com/$Owner/$RepoName" -ForegroundColor Green
} finally {
    Pop-Location
}
