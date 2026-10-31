param([string]$Output = 'pack-sdk/dist/pack-sdk-v1-debug.aar')

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$sdkSource = Join-Path $projectRoot 'pack-sdk/src'
$annotation = Get-ChildItem -LiteralPath $sdkSource -Recurse -File |
    Select-String -Pattern '@IntDef|@StringDef' -List
if ($annotation) {
    throw 'SDK now declares typedef annotations; restore Gradle annotation extraction before packaging.'
}

$typedefDir = Join-Path $projectRoot 'pack-sdk/build/intermediates/annotations_typedef_file/debug/extractDebugAnnotations'
New-Item -ItemType Directory -Force -Path $typedefDir | Out-Null
New-Item -ItemType File -Force -Path (Join-Path $typedefDir 'typedefs.txt') | Out-Null

$localJdk = Join-Path $projectRoot '.toolchain/zulu17.68.203-ca-jdk17.0.20.1-win_x64'
if (Test-Path (Join-Path $localJdk 'bin/java.exe')) { $env:JAVA_HOME = $localJdk }
$localSdk = Join-Path $projectRoot '.toolchain/android-sdk'
if (Test-Path (Join-Path $localSdk 'platforms/android-35/android.jar')) {
    $env:ANDROID_HOME = $localSdk
    $env:ANDROID_USER_HOME = Join-Path $projectRoot '.toolchain/android-user'
}
$localGradle = Join-Path $projectRoot '.toolchain/gradle-8.13/bin/gradle.bat'
if (Test-Path $localGradle) {
    $env:GRADLE_USER_HOME = Join-Path $projectRoot '.toolchain/gradle-home'
    $gradle = $localGradle
    $mode = @('--offline')
} else {
    $gradle = Join-Path $projectRoot 'gradlew.bat'
    $mode = @()
}

Push-Location $projectRoot
try {
    & $gradle ':pack-sdk:bundleDebugAar' '-x' ':pack-sdk:extractDebugAnnotations' @mode '--no-daemon'
    if ($LASTEXITCODE -ne 0) { throw "Gradle failed: $LASTEXITCODE" }
    $source = Join-Path $projectRoot 'pack-sdk/build/outputs/aar/pack-sdk-debug.aar'
    $destination = Join-Path $projectRoot $Output
    if (Test-Path -LiteralPath $destination) { throw "Refusing to overwrite $destination" }
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $destination) | Out-Null
    Copy-Item -LiteralPath $source -Destination $destination
    $hash = (Get-FileHash -LiteralPath $destination -Algorithm SHA256).Hash.ToLowerInvariant()
    Write-Output "$destination SHA-256 $hash"
} finally { Pop-Location }
