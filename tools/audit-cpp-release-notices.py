#!/usr/bin/env python3
"""Fail release preflight when locked LLDB packages lack notice rows."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LOCK = ROOT / "packs/cpp/lldb-toolchain-lock.json"
NOTICES = ROOT / "packs/cpp/src/main/assets/THIRD_PARTY_NOTICES.md"


def main() -> int:
    packages = json.loads(LOCK.read_text(encoding="utf-8"))["packages"]
    rows = {}
    for line in NOTICES.read_text(encoding="utf-8").splitlines():
        if not line.startswith("| "):
            continue
        columns = [part.strip() for part in line.strip("|").split("|")]
        if len(columns) == 3 and columns[0] not in ("Package", "---"):
            rows[columns[0]] = (columns[1], columns[2])
    missing = sorted(packages.keys() - rows.keys())
    empty = sorted(name for name in packages.keys() & rows.keys() if not rows[name][1])
    mismatched = sorted(name for name in packages.keys() & rows.keys()
                        if packages[name]["version"] != rows[name][0])
    if missing or empty or mismatched:
        print(f"C/C++ release notices incomplete: {len(missing)} missing, "
              f"{len(empty)} empty, {len(mismatched)} version mismatches")
        for name in missing:
            print(f"  missing: {name}")
        for name in empty:
            print(f"  empty license: {name}")
        for name in mismatched:
            print(f"  version mismatch: {name}: lock={packages[name]['version']}, "
                  f"notice={rows[name][0]}")
        return 1
    print(f"C/C++ notice rows present for {len(packages)} locked packages")
    print("Row presence does not replace review of upstream copyright and redistribution terms")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
