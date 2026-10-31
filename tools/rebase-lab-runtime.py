"""Repackage the pinned ARM64 runtime for the equally sized Lab application ID.

This is a binary prefix rewrite, not a substitute for Android device validation.
The source archive and manifest are read-only; output is written under lab/ only.
"""

import argparse
import hashlib
import json
import os
from pathlib import Path
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parent.parent
SOURCE_ID = b"com.example.pocketpythonide"
LAB_ID = b"com.example.pocketpythonlab"


def sha256(path):
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-zip", type=Path,
                        default=ROOT / "app/src/main/assets/runtime.zip")
    parser.add_argument("--source-manifest", type=Path,
                        default=ROOT / "app/src/main/assets/runtime.json")
    parser.add_argument("--output-dir", type=Path,
                        default=ROOT / "lab/src/main/assets")
    args = parser.parse_args()
    if len(SOURCE_ID) != len(LAB_ID):
        raise SystemExit("Application IDs must have equal UTF-8 byte lengths")
    source = json.loads(args.source_manifest.read_text(encoding="utf-8"))
    if source["app_id"] != SOURCE_ID.decode():
        raise SystemExit("Unexpected source application ID")
    source_hash = sha256(args.source_zip)
    if source_hash != source["asset_sha256"]:
        raise SystemExit("Source runtime hash differs from pinned manifest")

    args.output_dir.mkdir(parents=True, exist_ok=True)
    output_zip = args.output_dir / "runtime.zip"
    output_manifest = args.output_dir / "runtime.json"
    if output_zip.is_file() and output_manifest.is_file():
        current = json.loads(output_manifest.read_text(encoding="utf-8"))
        if (current.get("source_asset_sha256") == source_hash
                and current.get("app_id") == LAB_ID.decode()
                and current.get("asset_sha256") == sha256(output_zip)):
            print(f"Verified existing Lab runtime: {output_zip}")
            return

    handle, temp_name = tempfile.mkstemp(prefix="runtime-", suffix=".zip",
                                          dir=args.output_dir)
    os.close(handle)
    temporary = Path(temp_name)
    changed_files = 0
    substitutions = 0
    try:
        with zipfile.ZipFile(args.source_zip) as source_zip, zipfile.ZipFile(
                temporary, "w", allowZip64=True) as target_zip:
            for entry in source_zip.infolist():
                if SOURCE_ID.decode() in entry.filename:
                    raise ValueError(f"Unexpected package name in ZIP path: {entry.filename}")
                data = source_zip.read(entry)
                count = data.count(SOURCE_ID)
                if count:
                    data = data.replace(SOURCE_ID, LAB_ID)
                    changed_files += 1
                    substitutions += count
                target_zip.writestr(entry, data)
        if not substitutions:
            raise ValueError("No application prefix found in source runtime")
        manifest = dict(source)
        manifest["app_id"] = LAB_ID.decode()
        manifest["source_asset_sha256"] = source_hash
        manifest["asset_sha256"] = sha256(temporary)
        manifest["asset_bytes"] = temporary.stat().st_size
        manifest["prefix_rebase"] = {
            "from": SOURCE_ID.decode(), "to": LAB_ID.decode(),
            "files": changed_files, "substitutions": substitutions,
            "method": "equal-length byte replacement in ZIP member contents",
        }
        os.replace(temporary, output_zip)
        output_manifest.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n",
                                   encoding="utf-8")
        print(f"Lab runtime: {output_zip} ({manifest['asset_bytes']} bytes)")
        print(f"SHA-256: {manifest['asset_sha256']}")
        print(f"Rebased {substitutions} occurrences in {changed_files} files")
    finally:
        if temporary.exists():
            temporary.unlink()


if __name__ == "__main__":
    main()
