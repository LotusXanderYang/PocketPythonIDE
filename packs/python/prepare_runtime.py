#!/usr/bin/env python3
"""Derive the ARM64 CPython/stdlib runtime from the pinned beta.5 archive.

No files from torch, numpy, or other third-party site-packages enter the pack.
The original archive is verified before reading and never modified.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import struct
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[2]
SOURCE_ID = b"com.example.pocketpythonide"
PACK_ID = b"com.example.pocketpythonstd"
STDLIB = "usr/lib/python3.14/"
DYNLOAD = STDLIB + "lib-dynload/"
SITE = STDLIB + "site-packages/"
SYSTEM_LIBRARIES = {
    "libc.so", "libm.so", "libdl.so", "liblog.so", "libandroid.so",
    "libz.so", "libEGL.so", "libGLESv2.so",
}


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def needed_libraries(blob: bytes) -> list[str]:
    if blob[:6] != b"\x7fELF\x02\x01":
        raise ValueError("Expected Android ARM64 ELF")
    sections_at = struct.unpack_from("<Q", blob, 40)[0]
    section_size, section_count = struct.unpack_from("<HH", blob, 58)
    if sections_at + section_size * section_count > len(blob):
        raise ValueError("ELF section table is truncated")
    sections = []
    for index in range(section_count):
        pos = sections_at + index * section_size
        sections.append((struct.unpack_from("<I", blob, pos + 4)[0],
                         struct.unpack_from("<Q", blob, pos + 24)[0],
                         struct.unpack_from("<Q", blob, pos + 32)[0],
                         struct.unpack_from("<I", blob, pos + 40)[0]))
    result = []
    for kind, offset, size, link in sections:
        if kind != 6:  # SHT_DYNAMIC
            continue
        if link >= len(sections):
            raise ValueError("Invalid ELF dynamic string table")
        _, strings_at, strings_size, _ = sections[link]
        for pos in range(offset, offset + size, 16):
            tag, value = struct.unpack_from("<QQ", blob, pos)
            if tag != 1:  # DT_NEEDED
                continue
            start = strings_at + value
            if value >= strings_size:
                raise ValueError("Invalid ELF DT_NEEDED offset")
            end = blob.find(b"\0", start, strings_at + strings_size)
            if end < 0:
                raise ValueError("Unterminated ELF DT_NEEDED")
            result.append(blob[start:end].decode("ascii"))
    return result


def select_entries(archive: zipfile.ZipFile) -> tuple[set[str], set[str]]:
    names = {item.filename for item in archive.infolist() if not item.is_dir()}
    root_libs = {Path(name).name: name for name in names
                 if name.startswith("usr/lib/") and name.count("/") == 2}
    seeds = {"usr/lib/libpython3.14.so"}
    seeds.update(name for name in names if name.startswith(DYNLOAD)
                 and name.endswith(".so"))
    selected_libs: set[str] = set()
    needed: set[str] = set()
    queue = sorted(seeds)
    while queue:
        path = queue.pop()
        if path in selected_libs:
            continue
        if path not in names:
            raise ValueError(f"Missing runtime ELF: {path}")
        selected_libs.add(path)
        for dependency in needed_libraries(archive.read(path)):
            if dependency in SYSTEM_LIBRARIES:
                continue
            target = root_libs.get(dependency)
            if target is None:
                raise ValueError(f"Unresolved Android ELF dependency: {path} -> {dependency}")
            needed.add(target)
            if target not in selected_libs:
                queue.append(target)
    selected = {name for name in names if name.startswith(STDLIB)
                and not name.startswith(SITE)}
    selected.update(selected_libs)
    selected.update(name for name in names if name.startswith("usr/etc/tls/"))
    if "usr/lib/python3.14/os.py" not in selected:
        raise ValueError("CPython standard library is incomplete")
    if any("torch" in name.lower() or "numpy" in name.lower() for name in selected):
        raise ValueError("Scientific module leaked into Python base pack")
    return selected, needed


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-zip", type=Path,
                        default=ROOT / "app/src/main/assets/runtime.zip")
    parser.add_argument("--source-manifest", type=Path,
                        default=ROOT / "app/src/main/assets/runtime.json")
    parser.add_argument("--output-dir", type=Path,
                        default=ROOT / "packs/python/src/main/assets")
    parser.add_argument("--replace", action="store_true",
                        help="Replace only this generated runtime after saving its old hash")
    args = parser.parse_args()
    if len(SOURCE_ID) != len(PACK_ID):
        raise SystemExit("Application IDs must have equal UTF-8 byte lengths")
    source = json.loads(args.source_manifest.read_text(encoding="utf-8"))
    if source["app_id"] != SOURCE_ID.decode() or source["python"] != "3.14.6":
        raise SystemExit("Unexpected pinned source runtime")
    source_hash = sha256(args.source_zip)
    if source_hash != source["asset_sha256"]:
        raise SystemExit("Source runtime SHA-256 differs from the pinned manifest")
    args.output_dir.mkdir(parents=True, exist_ok=True)
    output_zip = args.output_dir / "runtime.zip"
    output_manifest = args.output_dir / "runtime.json"
    bridge = (Path(__file__).parent / "python_pack_bridge.py").read_bytes()
    bridge_hash = hashlib.sha256(bridge).hexdigest()
    if output_zip.exists() or output_manifest.exists():
        if output_zip.is_file() and output_manifest.is_file():
            old = json.loads(output_manifest.read_text(encoding="utf-8"))
            if (old.get("source_asset_sha256") == source_hash
                    and old.get("bridge_sha256") == bridge_hash
                    and old.get("asset_sha256") == sha256(output_zip)):
                print(f"Verified existing Python runtime: {output_zip}")
                return
        if not args.replace:
            raise SystemExit("Generated runtime differs; preserve it and pass --replace")
        print(f"Replacing generated runtime; old SHA-256: {sha256(output_zip)}")

    handle, temporary_name = tempfile.mkstemp(prefix="python-runtime-", suffix=".zip",
                                              dir=args.output_dir)
    os.close(handle)
    temporary = Path(temporary_name)
    substitutions = 0
    try:
        with zipfile.ZipFile(args.source_zip) as source_zip:
            selected, native_dependencies = select_entries(source_zip)
            with zipfile.ZipFile(temporary, "w", compression=zipfile.ZIP_DEFLATED,
                                 compresslevel=9, allowZip64=True) as target_zip:
                for name in sorted(selected):
                    if SOURCE_ID.decode() in name:
                        raise ValueError(f"Package ID in ZIP path: {name}")
                    data = source_zip.read(name)
                    substitutions += data.count(SOURCE_ID)
                    data = data.replace(SOURCE_ID, PACK_ID)
                    target_zip.writestr(name, data)
                target_zip.writestr(SITE + "python_pack_bridge.py", bridge)
        if substitutions == 0:
            raise ValueError("Source runtime contains no prefix to rebase")
        with zipfile.ZipFile(temporary) as candidate:
            if candidate.testzip() is not None:
                raise ValueError("Generated Python runtime ZIP has a corrupt member")
            if any("torch" in n.lower() or "numpy" in n.lower()
                   for n in candidate.namelist()):
                raise ValueError("Scientific dependency in generated runtime")
        manifest = {
            "app_id": PACK_ID.decode(), "python": "3.14.6",
            "asset": "runtime.zip", "asset_sha256": sha256(temporary),
            "asset_bytes": temporary.stat().st_size,
            "source_asset_sha256": source_hash, "bridge_sha256": bridge_hash,
            "source_manifest": "app/src/main/assets/runtime.json",
            "native_dependency_count": len(native_dependencies),
            "included_files": len(selected) + 1,
            "excluded": ["torch", "numpy", "all third-party site-packages"],
        }
        os.replace(temporary, output_zip)
        output_manifest.write_text(json.dumps(manifest, ensure_ascii=False, indent=2)
                                   + "\n", encoding="utf-8")
        print(f"Python base runtime: {output_zip} ({manifest['asset_bytes']} bytes)")
        print(f"SHA-256: {manifest['asset_sha256']}; {manifest['included_files']} files")
    finally:
        temporary.unlink(missing_ok=True)


if __name__ == "__main__":
    main()
