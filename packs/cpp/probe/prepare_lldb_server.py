"""Stage the official ARM64 LLDB server for same-UID device feasibility tests."""

import hashlib
from pathlib import Path
import subprocess
import tarfile

ROOT = Path(__file__).resolve().parents[3]
DEB = ROOT / ".toolchain/stage8-probe/lldb_21.1.8-3_aarch64.deb"
EXPECTED = "705b1a59918a2aedab0177ee0c8d51a1155faea681cb8a513b0ce25e406f574b"
DEST = ROOT / "packs/cpp/src/main/jniLibs/arm64-v8a/liblldb_server.so"


def main():
    if hashlib.sha256(DEB.read_bytes()).hexdigest() != EXPECTED:
        raise ValueError("LLDB package SHA-256 mismatch")
    process = subprocess.Popen(["tar", "-xOf", str(DEB), "data.tar.xz"],
                               stdout=subprocess.PIPE)
    blob = None
    with tarfile.open(fileobj=process.stdout, mode="r|xz") as archive:
        for member in archive:
            if member.name.endswith("/bin/lldb-server") and member.isfile():
                source = archive.extractfile(member)
                if source is None:
                    raise ValueError("Missing LLDB server data")
                blob = source.read()
    process.stdout.close()
    if process.wait() != 0 or blob is None:
        raise ValueError("Cannot extract LLDB server")
    # Same-length package-id relocation used by the existing Clang runtime.
    blob = blob.replace(b"com.termux", b"org.idecpp")
    DEST.write_bytes(blob)
    print(DEST, len(blob), hashlib.sha256(blob).hexdigest())


if __name__ == "__main__":
    main()
