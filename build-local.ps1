param(
    [string]$Task = 'assembleDebug'
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
if ($Task -eq 'assembleDebug') {
    & (Join-Path $projectRoot 'tools/build-modern-apk.ps1')
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
} else {
    Push-Location $projectRoot
    try {
        & (Join-Path $projectRoot 'gradlew.bat') --no-daemon $Task
        if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    } finally {
        Pop-Location
    }
}
