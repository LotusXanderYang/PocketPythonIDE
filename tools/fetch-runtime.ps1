# Fetch the pinned Android ARM64 runtime published with the beta release.
$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$assets = Join-Path $projectRoot 'app/src/main/assets'
$manifest = Get-Content (Join-Path $assets 'runtime.json') -Raw | ConvertFrom-Json
$output = Join-Path $assets 'runtime.zip'
$download = Join-Path $assets 'runtime.zip.download'
$url = 'https://github.com/LotusXanderYang/PocketPythonIDE/releases/download/v0.1.0-beta.1/runtime.zip'
try {
    Invoke-WebRequest -Uri $url -OutFile $download
    $actual = (Get-FileHash $download -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actual -ne $manifest.asset_sha256) {
        throw "Runtime SHA-256 mismatch: $actual"
    }
    Move-Item -LiteralPath $download -Destination $output -Force
    Write-Output "Verified runtime: $output"
} finally {
    if (Test-Path $download) { Remove-Item -LiteralPath $download }
}
