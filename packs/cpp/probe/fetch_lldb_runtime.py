"""Fetch only missing pinned Termux ARM64 debs and verify each SHA-256."""

import hashlib
import json
from pathlib import Path
from urllib.request import Request, urlopen

ROOT = Path(__file__).resolve().parents[3]
LOCK = json.loads((ROOT / "packs/cpp/lldb-toolchain-lock.json").read_text())
BASE = json.loads((ROOT / "packs/cpp/toolchain-lock.json").read_text())["packages"]
DEST = ROOT / ".toolchain/stage8-probe/debs"


def main():
    DEST.mkdir(parents=True, exist_ok=True)
    for name, info in LOCK["packages"].items():
        if name in BASE and BASE[name]["version"] == info["version"]:
            continue
        output = DEST / Path(info["filename"]).name
        if output.is_file() and hashlib.sha256(output.read_bytes()).hexdigest() == info["sha256"]:
            print("cached", name, flush=True)
            continue
        url = LOCK["repository"] + "/" + info["filename"]
        print("fetch", name, info["size"], flush=True)
        request = Request(url, headers={"User-Agent": "Mozilla/5.0"})
        with urlopen(request, timeout=30) as source, output.open("wb") as sink:
            digest = hashlib.sha256()
            size = 0
            while chunk := source.read(1024 * 1024):
                sink.write(chunk)
                digest.update(chunk)
                size += len(chunk)
        if size != info["size"] or digest.hexdigest() != info["sha256"]:
            output.unlink(missing_ok=True)
            raise ValueError("LLDB dependency mismatch: " + name)
    print("Pinned LLDB dependencies verified")


if __name__ == "__main__":
    main()
