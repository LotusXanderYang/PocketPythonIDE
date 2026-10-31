"""Pin the ARM64 Clang package closure from a downloaded Termux index.

The Packages.gz hash is checked against InRelease. Signature verification is
separate; this script does not claim that HTTPS alone authenticates the index.
"""

import argparse
import gzip
import hashlib
import json
import re
from pathlib import Path


def fields(paragraph: str) -> dict[str, str]:
    result: dict[str, str] = {}
    last = None
    for line in paragraph.splitlines():
        if line.startswith(" ") and last:
            result[last] += " " + line.strip()
        elif ":" in line:
            key, value = line.split(":", 1)
            result[key] = value.strip()
            last = key
    return result


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--release", type=Path, required=True)
    parser.add_argument("--packages", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    release = args.release.read_text(encoding="utf-8")
    packed = args.packages.read_bytes()
    digest = hashlib.sha256(packed).hexdigest()
    expected_line = next((line for line in release.splitlines()
                          if line.strip().endswith("main/binary-aarch64/Packages.gz")
                          and len(line.split()[0]) == 64), None)
    if expected_line is None or expected_line.split()[0] != digest:
        raise SystemExit("Packages.gz SHA-256 differs from InRelease")
    source = gzip.decompress(packed).decode("utf-8")
    all_packages = {}
    for paragraph in source.split("\n\n"):
        item = fields(paragraph)
        if item.get("Architecture") in {"aarch64", "all"} and "SHA256" in item:
            all_packages[item["Package"]] = item

    remaining = ["clang"]
    closure = {}
    while remaining:
        name = remaining.pop(0)
        if name in closure:
            continue
        item = all_packages.get(name)
        if item is None:
            raise SystemExit("Dependency absent from official index: " + name)
        dependencies = []
        for part in item.get("Depends", "").split(","):
            match = re.match(r"\s*([A-Za-z0-9.+-]+)", part)
            if match:
                dependencies.append(match.group(1))
        closure[name] = {"version": item["Version"],
                         "archive_bytes": int(item["Size"]),
                         "installed_kib": int(item["Installed-Size"]),
                         "sha256": item["SHA256"],
                         "filename": item["Filename"],
                         "depends": dependencies}
        remaining.extend(dep for dep in dependencies if dep not in closure)

    result = {"repository": "https://packages-cf.termux.dev/apt/termux-main",
              "architecture": "aarch64", "root": "clang",
              "packages_gz_sha256": digest,
              "archive_total_bytes": sum(x["archive_bytes"] for x in closure.values()),
              "installed_total_kib": sum(x["installed_kib"] for x in closure.values()),
              "packages": dict(sorted(closure.items()))}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n",
                           encoding="utf-8")
    for name, item in result["packages"].items():
        print(name, item["version"], item["archive_bytes"])
    print(args.output, len(closure), result["archive_total_bytes"],
          result["installed_total_kib"])


if __name__ == "__main__":
    main()
