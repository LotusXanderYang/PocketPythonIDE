#!/usr/bin/env bash
# Run in WSL after build-modern-runtime.sh has completed successfully.
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
project_dir="$(cd "$script_dir/.." && pwd -P)"
packages_dir="${POCKETPY_PACKAGES_DIR:-$project_dir/.toolchain/runtime-debs}"
wheels_dir="$project_dir/.toolchain/pure-wheels"
assets_dir="$project_dir/app/src/main/assets"
torch_package="$(find "$packages_dir" -maxdepth 1 -name 'python-torch_2.14.1*_aarch64.deb' -print -quit)"

[[ -n "$torch_package" ]] || { echo "Missing self-built PyTorch 2.14.1 package" >&2; exit 1; }
if [[ "$packages_dir" == "$project_dir/.toolchain/runtime-debs" ]]; then
    (cd "$packages_dir" && sha256sum -c ../runtime-debs.sha256)
fi
[[ -f "$project_dir/packaging/pure-wheels.sha256" ]] || {
    echo "Missing pure-wheel SHA-256 manifest" >&2; exit 1;
}
(
    cd "$wheels_dir"
    sha256sum -c ../../packaging/pure-wheels.sha256
)
python3 "$script_dir/audit_android_runtime.py" "$torch_package"
mkdir -p "$assets_dir"
python3 "$script_dir/assemble-modern-runtime.py" \
    "$packages_dir" "$assets_dir/runtime.zip" \
    --module "$script_dir/native-runtime/pocketpy_runtime_bridge.py" \
    --module "$project_dir/app/src/main/python/ide_runner.py" \
    --module "$project_dir/app/src/main/python/runtime_probe.py" \
    --wheel "$wheels_dir/filelock-4.2.4-py3-none-any.whl" \
    --wheel "$wheels_dir/typing_extensions-4.16.0-py3-none-any.whl" \
    --wheel "$wheels_dir/setuptools-84.2.0-py3-none-any.whl" \
    --wheel "$wheels_dir/sympy-1.14.0-py3-none-any.whl" \
    --wheel "$wheels_dir/networkx-3.7-py3-none-any.whl" \
    --wheel "$wheels_dir/jinja2-3.1.6-py3-none-any.whl" \
    --wheel "$wheels_dir/fsspec-2026.9.0-py3-none-any.whl" \
    --wheel "$wheels_dir/mpmath-1.3.0-py3-none-any.whl" \
    --wheel "$wheels_dir/markupsafe-3.0.4-py3-none-any.whl"
sha256sum "$assets_dir/runtime.zip"
