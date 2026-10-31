"""Inspect the pinned ARM64 Termux LLDB deb before any pack integration."""

import hashlib
import importlib.util
import json
from pathlib import Path
import subprocess
import tarfile

ROOT = Path(__file__).resolve().parents[3]
DEB = ROOT / ".toolchain/stage8-probe/lldb_21.1.8-3_aarch64.deb"
SHA256 = "705b1a59918a2aedab0177ee0c8d51a1155faea681cb8a513b0ce25e406f574b"
PARSER = ROOT / "packs/python/prepare_runtime.py"
SPEC = importlib.util.spec_from_file_location("python_runtime_prepare", PARSER)
parser = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(parser)


def main():
    if hashlib.sha256(DEB.read_bytes()).hexdigest() != SHA256:
        raise ValueError("Official LLDB package SHA-256 mismatch")
    process = subprocess.Popen(["tar", "-xOf", str(DEB), "data.tar.xz"],
                               stdout=subprocess.PIPE)
    targets = {"lldb-dap", "lldb-server", "liblldb.so"}
    result = {}
    with tarfile.open(fileobj=process.stdout, mode="r|xz") as archive:
        for member in archive:
            name = Path(member.name).name
            if name not in targets or not member.isfile():
                continue
            source = archive.extractfile(member)
            if source is None:
                raise ValueError(member.name)
            blob = source.read()
            result[name] = {"bytes": len(blob), "needed": parser.needed_libraries(blob),
                            "sha256": hashlib.sha256(blob).hexdigest()}
    process.stdout.close()
    if process.wait() != 0 or set(result) != targets:
        raise ValueError("Incomplete LLDB package")
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()
