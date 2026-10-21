#!/usr/bin/env python3
"""Verify an official OpenHarmony SDK archive and install only Windows HDC."""

import argparse
import hashlib
import shutil
import tarfile
import zipfile
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("archive", type=Path)
    parser.add_argument("sha256_file", type=Path)
    parser.add_argument("destination", type=Path)
    args = parser.parse_args()

    expected = args.sha256_file.read_text(encoding="ascii").split()[0].lower()
    digest = hashlib.sha256()
    with args.archive.open("rb") as source:
        for block in iter(lambda: source.read(8 * 1024 * 1024), b""):
            digest.update(block)
    actual = digest.hexdigest()
    if actual != expected:
        raise SystemExit(f"SDK SHA-256 mismatch: expected {expected}, got {actual}")
    print(f"SDK SHA-256 verified: {actual}", flush=True)

    args.destination.mkdir(parents=True, exist_ok=True)
    toolchains_zip = args.destination / "toolchains-windows.zip"
    with tarfile.open(args.archive, "r:gz") as archive:
        for member in archive:
            name = member.name.replace("\\", "/").lower()
            if member.isfile() and "/windows/" in f"/{name}" and "/toolchains-windows-" in f"/{name}" and name.endswith(".zip"):
                source = archive.extractfile(member)
                if source is None:
                    raise SystemExit("Cannot read Windows toolchains member")
                with source, toolchains_zip.open("wb") as output:
                    shutil.copyfileobj(source, output, 8 * 1024 * 1024)
                print(f"Extracted {member.name}", flush=True)
                break
        else:
            raise SystemExit("Windows toolchains ZIP not found in SDK archive")

    found = set()
    with zipfile.ZipFile(toolchains_zip) as archive:
        for member in archive.infolist():
            base = Path(member.filename.replace("\\", "/")).name.lower()
            if base not in {"hdc.exe", "libusb_shared.dll"}:
                continue
            if base in found:
                raise SystemExit(f"Duplicate {base} in Windows toolchains ZIP")
            with archive.open(member) as source, (args.destination / base).open("wb") as output:
                shutil.copyfileobj(source, output)
            found.add(base)
            print(f"Installed {base}", flush=True)
    if found != {"hdc.exe", "libusb_shared.dll"}:
        raise SystemExit(f"Missing HDC component(s): {sorted({'hdc.exe', 'libusb_shared.dll'} - found)}")


if __name__ == "__main__":
    main()
