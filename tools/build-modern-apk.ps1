# Compatibility entry for the large Python/PyTorch product.
# The beta.5 version of this script remains available from its Git tag.
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
& (Join-Path $root 'build-local.ps1') -Product lab
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
