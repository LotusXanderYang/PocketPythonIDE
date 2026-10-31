"""Expand verified Termux deb payloads into an ignored staging prefix.

Windows tar extraction cannot reliably create Unix symlinks, so links are
materialized as copies after all packages have been expanded.
"""

import json
import os
import shutil
import subprocess
import tarfile
from pathlib import Path, PurePosixPath


ROOT = Path(__file__).resolve().parents[3]
LOCK = json.loads((ROOT / "packs/cpp/toolchain-lock.json").read_text(encoding="utf-8"))
DEBS = ROOT / ".toolchain/stage5-probe/official-debs"
PREFIX = ROOT / ".toolchain/stage5-probe/official-prefix"
ARCHIVE_PREFIX = PurePosixPath("data/data/com.termux/files/usr")


def main() -> None:
    PREFIX.mkdir(parents=True, exist_ok=True)
    links = []
    for name, info in LOCK["packages"].items():
        package = DEBS / Path(info["filename"]).name
        command = subprocess.Popen(["tar", "-xOf", str(package), "data.tar.xz"],
                                   stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        assert command.stdout is not None
        archive = tarfile.open(fileobj=command.stdout, mode="r|xz")
        files = 0
        for item in archive:
            path = PurePosixPath(item.name.removeprefix("./"))
            if path == ARCHIVE_PREFIX or ARCHIVE_PREFIX not in path.parents:
                continue
            relative = path.relative_to(ARCHIVE_PREFIX)
            if ".." in relative.parts or relative.is_absolute():
                raise ValueError(f"Unsafe archive path: {item.name}")
            output = PREFIX.joinpath(*relative.parts)
            if item.isdir():
                output.mkdir(parents=True, exist_ok=True)
            elif item.isfile():
                output.parent.mkdir(parents=True, exist_ok=True)
                source = archive.extractfile(item)
                if source is None:
                    raise ValueError(f"Unusable entry: {item.name}")
                with source, output.open("wb") as sink:
                    shutil.copyfileobj(source, sink)
                files += 1
            elif item.issym():
                links.append((output, item.linkname))
            elif item.islnk():
                links.append((output, item.linkname))
        archive.close()
        command.stdout.close()
        assert command.stderr is not None
        errors = command.stderr.read()
        command.wait()
        if command.returncode:
            raise RuntimeError(f"Cannot expand {name}: {errors.decode(errors='replace')}")
        print(name, files, "regular files", flush=True)
    pending = links
    for _ in range(5):
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
            if not target.exists() or output.exists():
                if not target.exists():
                    remaining.append((output, link))
                continue
            output.parent.mkdir(parents=True, exist_ok=True)
            if target.is_dir():
                shutil.copytree(target, output, dirs_exist_ok=True)
            else:
                shutil.copy2(target, output)
        if len(remaining) == len(pending):
            break
        pending = remaining
    print("unresolved links", len(pending), flush=True)
    for output, link in pending[:30]:
        print("unresolved", output.relative_to(PREFIX), link)
    print("prefix", PREFIX)


if __name__ == "__main__":
    main()
