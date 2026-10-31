param([string]$Ndk = $env:ANDROID_NDK_HOME)
$ErrorActionPreference = 'Stop'
if (-not $Ndk) { throw 'Set ANDROID_NDK_HOME or pass -Ndk with Android NDK r30.' }
$toolchain = Join-Path $Ndk 'toolchains/llvm/prebuilt'
$hostFolder = Get-ChildItem -LiteralPath $toolchain -Directory |
    Where-Object Name -Match 'windows-x86_64|linux-x86_64|darwin-x86_64|darwin-arm64' |
    Select-Object -First 1
if (-not $hostFolder) { throw "Cannot find NDK host compiler in $toolchain" }
$compiler = Join-Path $hostFolder.FullName 'bin/aarch64-linux-android26-clang'
if ($IsWindows -or $env:OS -eq 'Windows_NT') { $compiler += '.cmd' }
if (-not (Test-Path -LiteralPath $compiler)) {
    $compiler = Join-Path $hostFolder.FullName 'bin/clang'
    if ($IsWindows -or $env:OS -eq 'Windows_NT') { $compiler += '.exe' }
}
if (-not (Test-Path -LiteralPath $compiler)) { throw 'NDK clang is missing.' }
$destination = Join-Path $PSScriptRoot 'src/main/jniLibs/arm64-v8a'
New-Item -ItemType Directory -Path $destination -Force | Out-Null
& $compiler --target=aarch64-linux-android26 -std=c11 -O2 -fPIE -pie `
    -o (Join-Path $destination 'liblld_wrapper.so') `
    (Join-Path $PSScriptRoot 'native/lld_wrapper.c')
if ($LASTEXITCODE -ne 0) { throw 'Building LLD wrapper failed.' }
& $compiler --target=aarch64-linux-android26 -std=c11 -O2 -fPIE -pie `
    -o (Join-Path $destination 'libprojectrunner.so') `
    (Join-Path $PSScriptRoot 'probe/src/project_runner.c') -ldl
if ($LASTEXITCODE -ne 0) { throw 'Building project runner failed.' }
Get-FileHash -Algorithm SHA256 -LiteralPath (Join-Path $destination 'liblld_wrapper.so'),
    (Join-Path $destination 'libprojectrunner.so')
