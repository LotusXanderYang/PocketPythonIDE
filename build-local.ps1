param(
    [ValidateSet('ide', 'sample', 'python', 'all')]
    [string]$Product = 'ide'
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$toolchainRoot = Join-Path $projectRoot '.toolchain'
$localJdk = Join-Path $toolchainRoot 'zulu17.68.203-ca-jdk17.0.20.1-win_x64'
if (Test-Path (Join-Path $localJdk 'bin/java.exe')) { $env:JAVA_HOME = $localJdk }
if (-not $env:JAVA_HOME -or -not (Test-Path (Join-Path $env:JAVA_HOME 'bin/java.exe'))) {
    throw 'JDK 17 is required. Set JAVA_HOME or install the local toolchain.'
}
$localSdk = Join-Path $toolchainRoot 'android-sdk'
if (Test-Path (Join-Path $localSdk 'platforms/android-35/android.jar')) {
    $env:ANDROID_HOME = $localSdk
    $env:ANDROID_USER_HOME = Join-Path $toolchainRoot 'android-user'
}
if (-not $env:ANDROID_HOME) { throw 'Android SDK API 35 is required. Set ANDROID_HOME.' }

if ($Product -eq 'python' -or $Product -eq 'all') {
    $python = if ($env:PYTHON -and (Test-Path -LiteralPath $env:PYTHON)) { $env:PYTHON }
        elseif (Test-Path 'D:\Anaconda\python.exe') { 'D:\Anaconda\python.exe' }
        else { (Get-Command python -ErrorAction Stop).Source }
    if ($Product -eq 'python' -or $Product -eq 'all') {
        & $python (Join-Path $projectRoot 'packs/python/prepare_runtime.py')
        if ($LASTEXITCODE -ne 0) { throw "Python pack runtime preparation failed: $LASTEXITCODE" }
    }
}

$localGradle = Join-Path $toolchainRoot 'gradle-8.13/bin/gradle.bat'
if (Test-Path $localGradle) {
    $env:GRADLE_USER_HOME = Join-Path $toolchainRoot 'gradle-home'
    $gradle = $localGradle
    $gradleArgs = @('--offline', '--no-daemon')
} else {
    $gradle = Join-Path $projectRoot 'gradlew.bat'
    $gradleArgs = @('--no-daemon')
}
if ($Product -eq 'ide' -or $Product -eq 'all') { $gradleArgs += ':ide:assembleDebug' }
if ($Product -eq 'sample' -or $Product -eq 'all') { $gradleArgs += ':sample-pack:assembleDebug' }
if ($Product -eq 'sample' -or $Product -eq 'all') { $gradleArgs += ':pack-probe:assembleDebug' }
if ($Product -eq 'python' -or $Product -eq 'all') { $gradleArgs += ':packs:python:assembleDebug' }

Push-Location $projectRoot
try {
    & $gradle @gradleArgs
    if ($LASTEXITCODE -ne 0) { throw "Gradle failed: $LASTEXITCODE" }
} finally { Pop-Location }

$modules = if ($Product -eq 'all') { @('ide', 'sample-pack', 'pack-probe', 'packs/python') }
    elseif ($Product -eq 'sample') { @('sample-pack', 'pack-probe') } else { @($Product) }
if ($Product -eq 'python') { $modules = @('packs/python') }
foreach ($module in $modules) {
    $apkName = Split-Path -Leaf $module
    $apk = Join-Path $projectRoot "$module/build/outputs/apk/debug/$apkName-debug.apk"
    if (-not (Test-Path -LiteralPath $apk -PathType Leaf)) { throw "APK missing: $apk" }
    $item = Get-Item -LiteralPath $apk
    $hash = (Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash.ToLowerInvariant()
    Write-Output "$module APK: $($item.FullName) ($($item.Length) bytes) SHA-256 $hash"
}
