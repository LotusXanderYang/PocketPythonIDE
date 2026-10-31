"""Resolve the official ARM64 LLDB dependency closure into a reproducible lock."""

import gzip
import json
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[3]
INDEX = ROOT / ".toolchain/stage8-probe/Packages.gz"
OUTPUT = ROOT / "packs/cpp/lldb-toolchain-lock.json"


def main():
    records = {}
    for paragraph in gzip.decompress(INDEX.read_bytes()).decode().split("\n\n"):
        fields = {}
        for line in paragraph.splitlines():
            if ": " in line and not line.startswith(" "):
                key, value = line.split(": ", 1)
                fields[key] = value
        if fields.get("Package"):
            records[fields["Package"]] = fields
    selected = {}
    pending = ["lldb"]
    while pending:
        name = pending.pop(0)
        if name in selected:
            continue
        fields = records.get(name)
        if fields is None:
            raise ValueError("Unresolved LLDB dependency: " + name)
        dependencies = []
        for item in fields.get("Depends", "").split(","):
            item = re.sub(r"\s*\([^)]*\)", "", item.split("|")[0]).strip()
            if item:
                dependencies.append(item)
        selected[name] = {
            "version": fields["Version"],
            "filename": fields["Filename"],
            "size": int(fields["Size"]),
            "sha256": fields["SHA256"],
            "depends": dependencies,
        }
        pending.extend(dependencies)
    lock = {"repository": "https://packages-cf.termux.dev/apt/termux-main",
            "architecture": "aarch64", "root": "lldb",
            "packages": dict(sorted(selected.items()))}
    OUTPUT.write_text(json.dumps(lock, indent=2) + "\n", encoding="utf-8")
    print("packages", len(selected), "download_bytes",
          sum(item["size"] for item in selected.values()))
    print("names", ", ".join(sorted(selected)))


if __name__ == "__main__":
    main()
