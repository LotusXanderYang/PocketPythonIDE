"""Audit a Termux .deb before considering it for an embedded Android runtime.

This deliberately fails closed: a package built for another Android app's
private directory must never be silently repackaged into this APK.
"""

import argparse
import hashlib
import io
import json
import re
import struct
import tarfile
from pathlib import Path


APP_ID = "com.example.pocketpythonide"
REQUIRED_PYTHON = "3.14"
REQUIRED_TORCH = "2.14.1"


def elf_load_alignments(blob):
    """Return the PT_LOAD alignments for a little-endian ELF64 ARM64 file."""
    if blob[:4] != b"\x7fELF":
        return None
    if len(blob) < 64 or blob[4:6] != b"\x02\x01":
        raise ValueError("Expected little-endian ELF64")
    machine = struct.unpack_from("<H", blob, 18)[0]
    if machine != 183:
        raise ValueError(f"Expected AArch64 ELF, found e_machine={machine}")
    ph_offset = struct.unpack_from("<Q", blob, 32)[0]
    ph_size, ph_count = struct.unpack_from("<HH", blob, 54)
    if ph_size < 56 or ph_offset + ph_size * ph_count > len(blob):
        raise ValueError("Invalid ELF program headers")
    return [struct.unpack_from("<Q", blob, ph_offset + index * ph_size + 48)[0]
            for index in range(ph_count)
            if struct.unpack_from("<I", blob, ph_offset + index * ph_size)[0] == 1]


def ar_members(path):
    with path.open("rb") as archive:
        if archive.read(8) != b"!<arch>\n":
            raise ValueError("Not a Debian ar archive")
        while header := archive.read(60):
            if len(header) != 60 or header[58:] != b"`\n":
                raise ValueError("Corrupt ar header")
            name = header[:16].decode("ascii").strip().rstrip("/")
            size = int(header[48:58])
            data = archive.read(size)
            if len(data) != size:
                raise ValueError("Truncated ar member")
            if size & 1:
                archive.read(1)
            yield name, data


def inspect_package(path):
    control = None
    files = []
    foreign_prefix_hits = []
    elf_files = 0
    misaligned_elf = []
    expected_prefix = f"/data/data/{APP_ID}/files/usr".encode()
    foreign_prefix = b"/data/data/com.termux/files/usr"
    for name, blob in ar_members(path):
        if name.startswith("control.tar"):
            with tarfile.open(fileobj=io.BytesIO(blob)) as archive:
                member = next((m for m in archive if m.name.lstrip("./") == "control"), None)
                if member is None:
                    raise ValueError("Missing Debian control file")
                control = archive.extractfile(member).read().decode("utf-8")
        elif name.startswith("data.tar"):
            with tarfile.open(fileobj=io.BytesIO(blob)) as archive:
                for member in archive:
                    if not (member.isfile() or member.issym() or member.islnk()):
                        continue
                    raw = (archive.extractfile(member).read() if member.isfile()
                           else member.linkname.encode("utf-8"))
                    files.append(member.name)
                    hits = raw.count(foreign_prefix)
                    if hits:
                        foreign_prefix_hits.append({"path": member.name, "hits": hits})
                    if member.isfile() and ".so" in member.name and raw[:4] == b"\x7fELF":
                        elf_files += 1
                        alignments = elf_load_alignments(raw)
                        if not alignments or min(alignments) < 0x4000:
                            misaligned_elf.append({"path": member.name,
                                                   "load_alignments": alignments})
    if control is None or not files:
        raise ValueError("Missing control or data archive")

    fields = dict(re.findall(r"^([\w-]+): ([^\n]+)", control, re.MULTILINE))
    wrong_paths = [p for p in files if not p.startswith("./" + expected_prefix.decode().lstrip("/"))]
    python_extension = [p for p in files if re.search(r"\.cpython-\d+-.*\.so$", p)]
    checks = {
        "package": fields.get("Package") == "python-torch",
        "torch_version": fields.get("Version", "").split("-")[0] == REQUIRED_TORCH,
        "aarch64": fields.get("Architecture") == "aarch64",
        "app_prefix": not wrong_paths,
        "no_foreign_embedded_prefix": not foreign_prefix_hits,
        "python_abi": any("cpython-314-" in p for p in python_extension),
        "elf_16kb_page_align": elf_files > 0 and not misaligned_elf,
    }
    return {
        "archive": str(path),
        "sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
        "package": fields.get("Package"),
        "version": fields.get("Version"),
        "architecture": fields.get("Architecture"),
        "depends": fields.get("Depends", ""),
        "required_app_id": APP_ID,
        "required_python": REQUIRED_PYTHON,
        "file_count": len(files),
        "python_extensions": python_extension,
        "wrong_prefix_paths": wrong_paths[:8],
        "foreign_prefix_hits": foreign_prefix_hits[:12],
        "elf_files": elf_files,
        "misaligned_elf": misaligned_elf[:12],
        "checks": checks,
        "ready_for_packaging": all(checks.values()),
    }


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("package", type=Path)
    args = parser.parse_args()
    report = inspect_package(args.package)
    print(json.dumps(report, ensure_ascii=False, indent=2))
    raise SystemExit(0 if report["ready_for_packaging"] else 1)
