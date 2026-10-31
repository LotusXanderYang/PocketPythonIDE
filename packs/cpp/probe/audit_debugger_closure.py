"""Check the packaged LLDB ELF dependency closure before another device install."""

import importlib.util
from pathlib import Path
import zipfile

ROOT = Path(__file__).resolve().parents[3]
SPEC = importlib.util.spec_from_file_location(
    "python_runtime_prepare", ROOT / "packs/python/prepare_runtime.py")
parser = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(parser)
ARCHIVE = ROOT / "packs/cpp/src/main/assets/toolchain.zip"
NATIVE = ROOT / "packs/cpp/src/main/jniLibs/arm64-v8a"
SYSTEM = {"libc.so", "libm.so", "libdl.so", "liblog.so", "libandroid.so",
          "libEGL.so", "libGLESv2.so", "libOpenSLES.so", "libz.so"}


def main():
    with zipfile.ZipFile(ARCHIVE) as archive:
        available = {Path(name).name for name in archive.namelist()
                     if name.startswith("lib/")}
        available.update(path.name for path in NATIVE.glob("*.so"))
        roots = [NATIVE / "liblldb_dap.so", NATIVE / "liblldb_server.so"]
        roots += [name for name in archive.namelist()
                  if name.startswith("lib/") and name.endswith(".so")]
        missing = {}
        for root in roots:
            blob = root.read_bytes() if isinstance(root, Path) else archive.read(root)
            if not blob.startswith(b"\x7fELF"):
                continue
            for needed in parser.needed_libraries(blob):
                if needed not in available and needed not in SYSTEM:
                    missing.setdefault(needed, []).append(str(root))
        for name, users in sorted(missing.items()):
            print("MISSING", name, "needed by", users[:3])
        if missing:
            raise SystemExit(1)
        print("LLDB packaged ELF dependency closure: OK")


if __name__ == "__main__":
    main()
