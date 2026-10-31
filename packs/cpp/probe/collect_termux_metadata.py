"""Read, without modifying the device, the pinned Termux package closure."""

import argparse
import json
import re
import subprocess
from pathlib import Path


TERMUX_APT = "/data/data/com.termux/files/usr/bin/apt-cache"
PREFIX = "/data/data/com.termux/files/usr"
LIB = "/data/data/com.termux/files/home/pocketide-stage5-probe/lib"


def parse_fields(raw: str) -> dict[str, str]:
    fields: dict[str, str] = {}
    previous = None
    for line in raw.splitlines():
        if not line:
            break
        if line[0].isspace() and previous:
            fields[previous] += " " + line.strip()
        elif ":" in line:
            key, value = line.split(":", 1)
            fields[key] = value.strip()
            previous = key
    return fields


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--adb", type=Path, required=True)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    base = [str(args.adb), "-s", args.serial, "shell", "run-as", "com.termux",
            "/system/bin/env", f"LD_LIBRARY_PATH={LIB}:{PREFIX}/lib", TERMUX_APT, "show"]
    remaining = ["clang"]
    packages: dict[str, dict] = {}
    while remaining:
        name = remaining.pop(0)
        if name in packages:
            continue
        done = subprocess.run(base + [name], capture_output=True, check=False, timeout=30)
        if done.returncode:
            raise SystemExit(f"apt-cache show {name} failed: "
                             + done.stderr.decode("utf-8", "replace"))
        fields = parse_fields(done.stdout.decode("utf-8", "replace"))
        if fields.get("Package") != name or "SHA256" not in fields:
            raise SystemExit(f"No package hash for {name}: {fields}")
        dependencies = []
        for part in fields.get("Depends", "").split(","):
            match = re.match(r"\s*([A-Za-z0-9.+-]+)", part)
            if match:
                dependencies.append(match.group(1))
        packages[name] = {"version": fields["Version"],
                          "installed_kib": int(fields["Installed-Size"]),
                          "archive_bytes": int(fields["Size"]),
                          "sha256": fields["SHA256"],
                          "filename": fields["Filename"],
                          "depends": dependencies}
        remaining.extend(dep for dep in dependencies if dep not in packages)
        print(name, packages[name]["version"], packages[name]["archive_bytes"])
    output = {"root": "clang", "abi": "aarch64",
              "archive_total_bytes": sum(x["archive_bytes"] for x in packages.values()),
              "installed_total_kib": sum(x["installed_kib"] for x in packages.values()),
              "packages": dict(sorted(packages.items()))}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(output, ensure_ascii=False, indent=2) + "\n",
                           encoding="utf-8")
    print(args.output, len(packages), output["archive_total_bytes"],
          output["installed_total_kib"])


if __name__ == "__main__":
    main()
