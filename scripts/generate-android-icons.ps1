param(
    [string]$SourcePng = "",
    [string]$BackgroundColor = "#10161E"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$RepoRoot = Split-Path -Parent $PSScriptRoot
$CanonicalSourcePng = Join-Path $RepoRoot "img\app-icon-master.png"
$FallbackSourcePng = Join-Path $RepoRoot "img\icon.png"
if ([string]::IsNullOrWhiteSpace($SourcePng)) {
    if (Test-Path $CanonicalSourcePng) {
        $SourcePng = $CanonicalSourcePng
    } elseif (Test-Path $FallbackSourcePng) {
        $SourcePng = $FallbackSourcePng
    } else {
        $SourcePng = $CanonicalSourcePng
    }
}

$ResRoot = Join-Path $RepoRoot "android-app\app\src\main\res"
$ExportRoot = Join-Path $RepoRoot "img\export"
$AdaptiveForegroundPath = Join-Path $ResRoot "drawable-nodpi\ic_launcher_foreground.png"
$AdaptiveXmlDir = Join-Path $ResRoot "mipmap-anydpi-v26"
$ColorsPath = Join-Path $ResRoot "values\colors.xml"
$Utf8NoBom = New-Object System.Text.UTF8Encoding($false)

function Write-Section([string]$Text) {
    Write-Host ""
    Write-Host "=== $Text ===" -ForegroundColor Cyan
}

function Ensure-Directory([string]$Path) {
    if (-not (Test-Path $Path)) {
        New-Item -ItemType Directory -Path $Path | Out-Null
    }
}

function Get-ScaledBounds([int]$SourceWidth, [int]$SourceHeight, [int]$CanvasSize, [double]$PaddingRatio = 0.10) {
    $available = [int][Math]::Round($CanvasSize * (1.0 - ($PaddingRatio * 2.0)))
    if ($available -lt 1) {
        throw "Некорректный padding ratio."
    }

    $scale = [Math]::Min($available / [double]$SourceWidth, $available / [double]$SourceHeight)
    $scaledWidth = [int][Math]::Round($SourceWidth * $scale)
    $scaledHeight = [int][Math]::Round($SourceHeight * $scale)
    $x = [int][Math]::Round(($CanvasSize - $scaledWidth) / 2.0)
    $y = [int][Math]::Round(($CanvasSize - $scaledHeight) / 2.0)

    return [pscustomobject]@{
        Width = $scaledWidth
        Height = $scaledHeight
        X = $x
        Y = $y
    }
}

function Save-PngVariant(
    [System.Drawing.Bitmap]$SourceBitmap,
    [string]$DestinationPath,
    [int]$CanvasSize,
    [double]$PaddingRatio = 0.10
) {
    $canvas = New-Object System.Drawing.Bitmap($CanvasSize, $CanvasSize, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    try {
        $graphics = [System.Drawing.Graphics]::FromImage($canvas)
        try {
            $graphics.Clear([System.Drawing.Color]::Transparent)
            $graphics.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
            $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
            $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
            $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality

            $bounds = Get-ScaledBounds -SourceWidth $SourceBitmap.Width -SourceHeight $SourceBitmap.Height -CanvasSize $CanvasSize -PaddingRatio $PaddingRatio
            $destinationRect = New-Object System.Drawing.Rectangle($bounds.X, $bounds.Y, $bounds.Width, $bounds.Height)
            $graphics.DrawImage($SourceBitmap, $destinationRect)
        } finally {
            $graphics.Dispose()
        }

        Ensure-Directory (Split-Path -Parent $DestinationPath)
        $canvas.Save($DestinationPath, [System.Drawing.Imaging.ImageFormat]::Png)
    } finally {
        $canvas.Dispose()
    }
}

function Write-TextFile([string]$Path, [string]$Content) {
    Ensure-Directory (Split-Path -Parent $Path)
    [System.IO.File]::WriteAllText($Path, $Content, $Utf8NoBom)
}

function Update-ColorResource([string]$Path, [string]$ColorHex) {
    $content = @"
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="ic_launcher_background">$ColorHex</color>
</resources>
"@
    Write-TextFile -Path $Path -Content $content
}

function Update-AdaptiveIconXml([string]$Directory) {
    Ensure-Directory $Directory
    $iconXml = @"
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
"@
    Write-TextFile -Path (Join-Path $Directory "ic_launcher.xml") -Content $iconXml
    Write-TextFile -Path (Join-Path $Directory "ic_launcher_round.xml") -Content $iconXml
}

Write-Section "Проверка исходника"
if (-not (Test-Path $SourcePng)) {
    throw "Не найден исходный PNG: $SourcePng"
}

$resolvedSourcePath = (Resolve-Path $SourcePng).Path
if ($resolvedSourcePath -ne $CanonicalSourcePng) {
    Copy-Item -Path $resolvedSourcePath -Destination $CanonicalSourcePng -Force
}

Add-Type -AssemblyName System.Drawing
$sourceImage = [System.Drawing.Image]::FromFile($SourcePng)
try {
    $sourceBitmap = New-Object System.Drawing.Bitmap($sourceImage)
    try {
        if ($sourceBitmap.Width -lt 512 -or $sourceBitmap.Height -lt 512) {
            throw "Исходник слишком маленький. Нужен минимум 512x512, рекомендуется 1024x1024."
        }

        Write-Host "Исходник: $SourcePng"
        Write-Host "Размер: $($sourceBitmap.Width)x$($sourceBitmap.Height)" -ForegroundColor Green

        Write-Section "Генерация adaptive icon foreground"
        Save-PngVariant -SourceBitmap $sourceBitmap -DestinationPath $AdaptiveForegroundPath -CanvasSize 432 -PaddingRatio 0.16
        Update-AdaptiveIconXml -Directory $AdaptiveXmlDir
        Update-ColorResource -Path $ColorsPath -ColorHex $BackgroundColor

        Write-Section "Генерация mipmap PNG"
        $densities = @(
            @{ Directory = "mipmap-mdpi"; Size = 48 },
            @{ Directory = "mipmap-hdpi"; Size = 72 },
            @{ Directory = "mipmap-xhdpi"; Size = 96 },
            @{ Directory = "mipmap-xxhdpi"; Size = 144 },
            @{ Directory = "mipmap-xxxhdpi"; Size = 192 }
        )
        foreach ($density in $densities) {
            $targetDir = Join-Path $ResRoot $density.Directory
            Save-PngVariant -SourceBitmap $sourceBitmap -DestinationPath (Join-Path $targetDir "ic_launcher.png") -CanvasSize $density.Size -PaddingRatio 0.18
            Save-PngVariant -SourceBitmap $sourceBitmap -DestinationPath (Join-Path $targetDir "ic_launcher_round.png") -CanvasSize $density.Size -PaddingRatio 0.18
        }

        Write-Section "Экспорт preview"
        Ensure-Directory $ExportRoot
        Save-PngVariant -SourceBitmap $sourceBitmap -DestinationPath (Join-Path $ExportRoot "app-icon-play-store-512.png") -CanvasSize 512 -PaddingRatio 0.10
        Save-PngVariant -SourceBitmap $sourceBitmap -DestinationPath (Join-Path $ExportRoot "app-icon-preview-256.png") -CanvasSize 256 -PaddingRatio 0.16

        Write-Host ""
        Write-Host "Иконки сгенерированы." -ForegroundColor Green
        Write-Host "Foreground: $AdaptiveForegroundPath"
        Write-Host "Preview export: $ExportRoot"
    } finally {
        $sourceBitmap.Dispose()
    }
} finally {
    $sourceImage.Dispose()
}
