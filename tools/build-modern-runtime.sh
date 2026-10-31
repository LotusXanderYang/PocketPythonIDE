#!/usr/bin/env bash
# Run inside a Linux filesystem (for example Ubuntu on WSL2) with Docker Engine.
# This builds the complete Android/Bionic dependency chain for this app's prefix.
set -euo pipefail

expected_commit=6be420e7536d1e228f3ffee2eb74ca2736a73fc1
app_id=com.example.pocketpythonide
export TERMUX_BUILDER_IMAGE_NAME="${TERMUX_BUILDER_IMAGE_NAME:-pocketpy-termux-builder:local}"
script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
source_checkout="$script_dir/../.toolchain/termux-packages"
build_checkout="$HOME/pocketpy-build/termux-packages"

for command in git docker python3; do
    command -v "$command" >/dev/null || { echo "Missing command: $command" >&2; exit 1; }
done
if [[ ! -d "$source_checkout/.git" ]]; then
    mkdir -p "$(dirname "$source_checkout")"
    git clone --filter=blob:none https://github.com/termux/termux-packages.git "$source_checkout"
    git -C "$source_checkout" checkout --detach "$expected_commit"
fi
if ! docker info >/dev/null 2>&1; then
    if sudo docker info >/dev/null 2>&1; then
        export TERMUX_DOCKER_USE_SUDO=1
    else
        echo "Docker Engine is not running; run setup-linux-builder.sh" >&2
        exit 1
    fi
fi
[[ -d "$source_checkout/.git" ]] || { echo "Missing $source_checkout" >&2; exit 1; }
[[ "$(git -C "$source_checkout" rev-parse HEAD)" == "$expected_commit" ]] || {
    echo "Termux source commit differs from the audited checkout" >&2; exit 1;
}

if [[ ! -d "$build_checkout/.git" ]]; then
    mkdir -p "$(dirname "$build_checkout")"
    git clone --no-local "$source_checkout" "$build_checkout"
fi
[[ "$(git -c safe.directory="$build_checkout" -C "$build_checkout" rev-parse HEAD)" == "$expected_commit" ]] || {
    echo "Build checkout has an unexpected commit" >&2; exit 1;
}
resume_patch="$script_dir/../packaging/termux/python-torch-resume.patch"
if ! git -C "$build_checkout" apply --reverse --check "$resume_patch" 2>/dev/null; then
    git -C "$build_checkout" apply --check "$resume_patch"
    git -C "$build_checkout" apply "$resume_patch"
fi

properties="$build_checkout/scripts/properties.sh"
if grep -q '^TERMUX_APP__PACKAGE_NAME="com.termux"$' "$properties"; then
    sed -i "s/^TERMUX_APP__PACKAGE_NAME=\"com.termux\"$/TERMUX_APP__PACKAGE_NAME=\"$app_id\"/" "$properties"
fi
grep -q "^TERMUX_APP__PACKAGE_NAME=\"$app_id\"$" "$properties" || {
    echo "Could not set the application-specific Termux prefix" >&2; exit 1;
}
grep -q '^TERMUX_PKG_VERSION="2.14.1"$' "$build_checkout/packages/python-torch/build.sh" || {
    echo "PyTorch recipe is no longer pinned to 2.14.1" >&2; exit 1;
}
grep -q '^TERMUX_PKG_VERSION="3.14.6"$' "$build_checkout/packages/python/build.sh" || {
    echo "Python recipe is no longer pinned to 3.14.6" >&2; exit 1;
}

# The Docker image runs as uid 1001 and writes output into this bind mount.
if [[ "$(id -u)" == 0 ]]; then
    chown -R 1001:1001 "$build_checkout"
fi

cd "$build_checkout"
export TERMUX_PKG_MAKE_PROCESSES="${TERMUX_PKG_MAKE_PROCESSES:-2}"
# PyTorch's pip/wheel build starts its own Ninja invocation. Limit it too;
# otherwise MAX_JOBS defaults to multiple clang++ processes and can OOM WSL.
export MAX_JOBS="${MAX_JOBS:-$TERMUX_PKG_MAKE_PROCESSES}"
# Do not pass -i or -I: official com.termux packages use an incompatible prefix.
./scripts/run-docker.sh env MAX_JOBS="$MAX_JOBS" ./build-package.sh \
    -j"$TERMUX_PKG_MAKE_PROCESSES" -a aarch64 python-torch
python3 "$script_dir/audit_android_runtime.py" "$(find output -maxdepth 1 -name 'python-torch_2.14.1*_aarch64.deb' -print -quit)"
sha256sum output/*.deb > "$script_dir/../.toolchain/modern-runtime-sha256.txt"
