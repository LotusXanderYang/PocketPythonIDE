# Package the audited CPython/PyTorch runtime into the isolated ARM64 IDE APK.
$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$toolchainRoot = Join-Path $projectRoot '.toolchain'
$assetRoot = Join-Path $projectRoot 'app/src/main/assets'
$runtimeZip = Join-Path $assetRoot 'runtime.zip'
$runtimeManifest = Join-Path $assetRoot 'runtime.json'
$nativeBridge = Join-Path $projectRoot 'app/src/main/jniLibs/arm64-v8a/libnativepythonbridge.so'

foreach ($required in @($runtimeZip, $runtimeManifest, $nativeBridge)) {
    if (-not (Test-Path -LiteralPath $required -PathType Leaf)) {
        throw "Required APK input is missing: $required"
    }
}
$manifest = Get-Content -LiteralPath $runtimeManifest -Raw | ConvertFrom-Json
if ($manifest.app_id -ne 'com.example.pocketpythonide' -or
    $manifest.python -ne '3.14.6' -or $manifest.torch -ne '2.14.1') {
    throw 'Runtime manifest has unexpected application ID or version'
}
$actualHash = (Get-FileHash -LiteralPath $runtimeZip -Algorithm SHA256).Hash.ToLowerInvariant()
if ($actualHash -ne $manifest.asset_sha256) {
    throw 'runtime.zip SHA-256 differs from runtime.json'
}

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
$localGradle = Join-Path $toolchainRoot 'gradle-8.13/bin/gradle.bat'
if (Test-Path $localGradle) {
    $env:GRADLE_USER_HOME = Join-Path $toolchainRoot 'gradle-home'
    $gradle = $localGradle
    $gradleArgs = @('--offline', '--no-daemon', 'assembleDebug')
} else {
    $gradle = Join-Path $projectRoot 'gradlew.bat'
    $gradleArgs = @('--no-daemon', 'assembleDebug')
}

Push-Location $projectRoot
try {
    & $gradle @gradleArgs
    if ($LASTEXITCODE -ne 0) { throw "Gradle failed with exit code $LASTEXITCODE" }
} finally {
    Pop-Location
}
$apk = Join-Path $projectRoot 'app/build/outputs/apk/debug/app-debug.apk'
if (-not (Test-Path -LiteralPath $apk -PathType Leaf)) { throw "APK missing: $apk" }
Get-Item -LiteralPath $apk | Select-Object FullName, Length
