#!/usr/bin/env python3
"""Create a deterministic source SDK ZIP without Gradle's optional AAR lint dependency."""
from __future__ import annotations

import hashlib
import pathlib
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
SOURCES = [
    "pack-sdk/build.gradle",
    "pack-sdk/README.md",
    "pack-sdk/src/main/AndroidManifest.xml",
    "pack-sdk/src/main/aidl/org/pocketide/pack/v1/ILanguagePack.aidl",
    "pack-sdk/src/main/aidl/org/pocketide/pack/v1/ITaskCallback.aidl",
    "pack-sdk/src/main/java/org/pocketide/pack/v1/PackContract.java",
    "LICENSE",
]
OUTPUT = ROOT / "pack-sdk/dist/pack-sdk-v1-source.zip"


def main() -> None:
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    if OUTPUT.exists():
        raise SystemExit(f"Refusing to overwrite existing SDK archive: {OUTPUT}")
    with zipfile.ZipFile(OUTPUT, "w", compression=zipfile.ZIP_DEFLATED,
                         compresslevel=9) as archive:
        for name in SOURCES:
            info = zipfile.ZipInfo(name, (2026, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o644 << 16
            archive.writestr(info, (ROOT / name).read_bytes(), compress_type=zipfile.ZIP_DEFLATED,
                             compresslevel=9)
    digest = hashlib.sha256(OUTPUT.read_bytes()).hexdigest()
    print(f"{OUTPUT} ({OUTPUT.stat().st_size} bytes) SHA-256 {digest}")


if __name__ == "__main__":
    main()
