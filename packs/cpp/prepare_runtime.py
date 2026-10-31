"""Create the initial course-scope Android ARM64 Clang runtime asset.

Input packages are pinned and checked by probe/fetch-locked-toolchain.ps1.
The equal-length package name rewrite relocates Termux's hardcoded prefix to
this language pack's private directory. Native compiler executables live in
the APK's extracted native-library directory, not in writable app files.
"""

import hashlib
import json
import shutil
import zipfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / ".toolchain/stage5-probe/official-prefix"
DEBUG_SOURCE = ROOT / ".toolchain/stage8-probe/official-prefix"
DEST = ROOT / "packs/cpp/src/main"
OLD = b"com.termux"
NEW = b"org.idecpp"
assert len(OLD) == len(NEW)

LIBRARIES = (
    "libLLVM.so", "libclang-cpp.so", "libc++_shared.so", "libffi.so",
    "libz.so.1", "libzstd.so.1", "libxml2.so.16", "libiconv.so",
    "liblzma.so.5", "libncurses.so.6", "libncursesw.so.6", "libandroid-glob.so",
)
NATIVE_PROGRAMS = {
    "bin/clang-21": "libclang_exec.so",
    "bin/ld.lld": "liblld_exec.so",
}


def content(path: Path) -> bytes:
    return path.read_bytes().replace(OLD, NEW)


def main() -> None:
    if not (DEBUG_SOURCE / "lib/liblldb.so").is_file():
        raise FileNotFoundError("Extract the pinned LLDB dependency closure first")
    assets = DEST / "assets"
    native = DEST / "jniLibs/arm64-v8a"
    assets.mkdir(parents=True, exist_ok=True)
    native.mkdir(parents=True, exist_ok=True)
    for input_name, output_name in NATIVE_PROGRAMS.items():
        (native / output_name).write_bytes(content(SOURCE / input_name))
    for input_name, output_name in (("lldb-dap", "liblldb_dap.so"),
                                    ("lldb-server", "liblldb_server.so")):
        (native / output_name).write_bytes(content(DEBUG_SOURCE / "bin" / input_name))
    (native / "libc++_shared.so").write_bytes(content(SOURCE / "lib/libc++_shared.so"))
    selected = set()
    developer_headers = {"llvm", "llvm-c", "clang", "clang-c", "clang-tidy"}
    for path in (SOURCE / "include").rglob("*"):
        if path.is_file() and path.relative_to(SOURCE / "include").parts[0] not in developer_headers:
            selected.add(path)
    selected.update(path for path in (SOURCE / "lib/clang/21/include").rglob("*")
                    if path.is_file())
    selected.add(SOURCE / "lib/clang/21/lib/linux/libclang_rt.builtins-aarch64-android.a")
    for filename in LIBRARIES:
        selected.add(SOURCE / "lib" / filename)
    selected.update(path for path in (SOURCE / "lib").glob("crt*.o") if path.is_file())
    selected.add(SOURCE / "lib/libunwind.a")
    selected.add(SOURCE / "lib/libcompiler_rt-extras.a")
    entries = {path.relative_to(SOURCE).as_posix(): path for path in selected}
    entries.update({path.relative_to(DEBUG_SOURCE).as_posix(): path
                    for path in (DEBUG_SOURCE / "lib").rglob("*") if path.is_file()})
    output = assets / "toolchain.zip"
    with zipfile.ZipFile(output, "w", compression=zipfile.ZIP_DEFLATED,
                         compresslevel=6, allowZip64=True) as archive:
        for relative, path in sorted(entries.items()):
            archive.writestr(relative, content(path))
    manifest = {
        "source": "https://packages-cf.termux.dev/apt/termux-main",
        "lock": "packs/cpp/toolchain-lock.json",
        "prefix_from": OLD.decode(), "prefix_to": NEW.decode(),
        "asset_sha256": hashlib.sha256(output.read_bytes()).hexdigest(),
        "asset_bytes": output.stat().st_size,
        "asset_files": len(entries),
        "compiler_sha256": hashlib.sha256((native / "libclang_exec.so").read_bytes()).hexdigest(),
        "linker_sha256": hashlib.sha256((native / "liblld_exec.so").read_bytes()).hexdigest(),
        "lldb_dap_sha256": hashlib.sha256((native / "liblldb_dap.so").read_bytes()).hexdigest(),
        "lldb_server_sha256": hashlib.sha256((native / "liblldb_server.so").read_bytes()).hexdigest(),
    }
    (assets / "toolchain.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(manifest, indent=2))


if __name__ == "__main__":
    main()
