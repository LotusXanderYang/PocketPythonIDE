#!/usr/bin/env bash
# Run inside the isolated Termux builder image (Android NDK r30).
set -euo pipefail

source_file="${1:?Pass native_python_bridge.c}"
output_file="${2:?Pass output .so path}"
ndk_root="${ANDROID_NDK_ROOT:-/home/builder/lib/android-ndk-r30}"
compiler="$ndk_root/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android26-clang"
readelf="$ndk_root/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-readelf"

"$compiler" -shared -fPIC -O2 -Wall -Wextra -Werror \
    -Wl,-z,max-page-size=16384 -o "$output_file" "$source_file" -ldl
"$readelf" -l "$output_file" | awk '$1 == "LOAD" {count++; if ($NF != "0x4000") bad=1} END {exit (bad || count == 0)}'
"$readelf" -d "$output_file" | grep 'Shared library: \[libdl.so\]'
