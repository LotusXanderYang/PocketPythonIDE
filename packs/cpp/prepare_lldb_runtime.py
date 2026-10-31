"""Build the pinned LLDB DAP runtime asset for the independent C/C++ pack."""

import hashlib
import json
from pathlib import Path
import zipfile

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / ".toolchain/stage8-probe/official-prefix"
PROBE = ROOT / ".toolchain/stage8-probe"
NATIVE = ROOT / "packs/cpp/src/main/jniLibs/arm64-v8a"
OLD, NEW = b"com.termux", b"org.idecpp"
assert len(OLD) == len(NEW)


def relocated(path):
    return path.read_bytes().replace(OLD, NEW)


def main():
    lock = json.loads((ROOT / "packs/cpp/lldb-toolchain-lock.json").read_text())
    if lock["packages"]["lldb"]["version"] != "21.1.8-3":
        raise ValueError("Unexpected LLDB version")
    for source, target in (("lldb-dap", "liblldb_dap.so"),
                           ("lldb-server", "liblldb_server.so")):
        (NATIVE / target).write_bytes(relocated(SOURCE / "bin" / source))
    files = sorted(path for path in (SOURCE / "lib").rglob("*") if path.is_file())
    archive = PROBE / "debugger.zip"
    with zipfile.ZipFile(archive, "w", compression=zipfile.ZIP_DEFLATED,
                         compresslevel=6, allowZip64=True) as output:
        for path in files:
            output.writestr(path.relative_to(SOURCE).as_posix(), relocated(path))
    info = {"source": lock["repository"], "lock": "packs/cpp/lldb-toolchain-lock.json",
            "prefix_from": OLD.decode(), "prefix_to": NEW.decode(),
            "asset_sha256": hashlib.sha256(archive.read_bytes()).hexdigest(),
            "asset_bytes": archive.stat().st_size, "asset_files": len(files),
            "dap_sha256": hashlib.sha256((NATIVE / "liblldb_dap.so").read_bytes()).hexdigest(),
            "server_sha256": hashlib.sha256((NATIVE / "liblldb_server.so").read_bytes()).hexdigest()}
    (PROBE / "debugger.json").write_text(json.dumps(info, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(info, indent=2))


if __name__ == "__main__":
    main()
