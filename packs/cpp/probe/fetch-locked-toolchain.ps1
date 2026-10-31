param([string]$Lock = 'packs/cpp/toolchain-lock.json')
$ErrorActionPreference = 'Stop'
$manifest = Get-Content -LiteralPath $Lock -Raw | ConvertFrom-Json
$destination = Join-Path (Get-Location) '.toolchain/stage5-probe/official-debs'
New-Item -ItemType Directory -Path $destination -Force | Out-Null
foreach ($property in $manifest.packages.PSObject.Properties) {
    $name = $property.Name
    $info = $property.Value
    $file = Join-Path $destination ([IO.Path]::GetFileName($info.filename))
    if ((Test-Path -LiteralPath $file) -and
        (Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash.ToLowerInvariant() -eq $info.sha256) {
        Write-Output "verified cached $name"
        continue
    }
    $part = "$file.part"
    $mirrors = @($manifest.repository, 'https://packages.termux.dev/apt/termux-main')
    $downloaded = $false
    foreach ($mirror in $mirrors) {
        for ($attempt = 1; $attempt -le 3; $attempt++) {
            try {
                Invoke-WebRequest -Uri "$($mirror.TrimEnd('/'))/$($info.filename)" -OutFile $part -UseBasicParsing -TimeoutSec 120
                if ((Get-FileHash -LiteralPath $part -Algorithm SHA256).Hash.ToLowerInvariant() -ne $info.sha256) {
                    throw "SHA-256 mismatch for $name"
                }
                if ((Get-Item -LiteralPath $part).Length -ne $info.archive_bytes) {
                    throw "Size mismatch for $name"
                }
                Move-Item -LiteralPath $part -Destination $file -Force
                Write-Output "downloaded verified $name $($info.archive_bytes)"
                $downloaded = $true
                break
            } catch {
                Write-Warning "$name attempt $attempt on $mirror failed: $($_.Exception.Message)"
                Start-Sleep -Seconds ([Math]::Min(10, $attempt * 2))
            }
        }
        if ($downloaded) { break }
    }
    if (-not $downloaded) { throw "All mirrors failed for $name" }
}
