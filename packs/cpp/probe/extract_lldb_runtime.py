"""Extract only missing pinned LLDB dependencies to an ignored staging prefix."""

import json
from pathlib import Path, PurePosixPath
import shutil
import subprocess
import tarfile

ROOT = Path(__file__).resolve().parents[3]
LOCK = json.loads((ROOT / "packs/cpp/lldb-toolchain-lock.json").read_text())["packages"]
BASE = json.loads((ROOT / "packs/cpp/toolchain-lock.json").read_text())["packages"]
DEBS = ROOT / ".toolchain/stage8-probe/debs"
PREFIX = ROOT / ".toolchain/stage8-probe/official-prefix"
ARCHIVE_PREFIX = PurePosixPath("data/data/com.termux/files/usr")


def main():
    PREFIX.mkdir(parents=True, exist_ok=True)
    links = []
    for name, info in LOCK.items():
        if name in BASE and BASE[name]["version"] == info["version"]:
            continue
        package = DEBS / Path(info["filename"]).name
        process = subprocess.Popen(["tar", "-xOf", str(package), "data.tar.xz"],
                                   stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        count = 0
        with tarfile.open(fileobj=process.stdout, mode="r|xz") as archive:
            for item in archive:
                path = PurePosixPath(item.name.removeprefix("./"))
                if path == ARCHIVE_PREFIX or ARCHIVE_PREFIX not in path.parents:
                    continue
                relative = path.relative_to(ARCHIVE_PREFIX)
                if ".." in relative.parts or relative.is_absolute():
                    raise ValueError("Unsafe package entry: " + item.name)
                output = PREFIX.joinpath(*relative.parts)
                if item.isdir():
                    output.mkdir(parents=True, exist_ok=True)
                elif item.isfile():
                    output.parent.mkdir(parents=True, exist_ok=True)
                    source = archive.extractfile(item)
                    if source is None:
                        raise ValueError("Unreadable package entry: " + item.name)
                    with source, output.open("wb") as sink:
                        shutil.copyfileobj(source, sink)
                    count += 1
                elif item.issym() or item.islnk():
                    links.append((output, item.linkname))
        process.stdout.close()
        error = process.stderr.read()
        if process.wait() != 0:
            raise RuntimeError(error.decode(errors="replace"))
        print(name, count, flush=True)
    pending = links
    for _ in range(6):
        remaining = []
        for output, link in pending:
            if link.startswith("/data/data/com.termux/files/usr/"):
                relative = PurePosixPath(link).relative_to("/data/data/com.termux/files/usr")
                target = PREFIX.joinpath(*relative.parts)
            elif link.startswith("/"):
                remaining.append((output, link))
                continue
            else:
                target = (output.parent / link).resolve()
            if not target.is_file():
                remaining.append((output, link))
                continue
            output.parent.mkdir(parents=True, exist_ok=True)
            if not output.exists():
                shutil.copy2(target, output)
        if len(remaining) == len(pending):
            break
        pending = remaining
    print("unresolved_links", len(pending))
    for output, target in pending[:10]:
        print("unresolved", output.relative_to(PREFIX), target)


if __name__ == "__main__":
    main()
