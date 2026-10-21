"""Assemble locally built Termux ARM64 packages into an auditable APK asset.

Run on Linux after build-modern-runtime.sh. The ZIP contains the complete
runtime dependency closure under the app's own /files/usr prefix. It is not
made from com.termux repository packages.
"""

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import struct
import subprocess
import tempfile
import zipfile

from audit_android_runtime import APP_ID, elf_load_alignments


PREFIX = f"data/data/{APP_ID}/files/usr"
FOREIGN_PREFIX = b"/data/data/com.termux/files/usr"


def metadata(package: Path) -> dict[str, str]:
    result = subprocess.run(["dpkg-deb", "-f", str(package)], check=True,
                            capture_output=True, text=True).stdout
    values = {}
    current = None
    for line in result.splitlines():
        if line.startswith(" ") and current:
            values[current] += " " + line.strip()
        elif ": " in line:
            current, value = line.split(": ", 1)
            values[current] = value
    return values


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        while chunk := source.read(1 << 20):
            digest.update(chunk)
    return digest.hexdigest()


def dependency_names(value: str, available: set[str]) -> list[str]:
    result = []
    for alternatives in value.split(","):
        candidates = [re.match(r"\s*([a-z0-9+.-]+)", item).group(1)
                      for item in alternatives.split("|")
                      if re.match(r"\s*([a-z0-9+.-]+)", item)]
        if candidates:
            match = next((item for item in candidates if item in available), None)
            if match is None:
                raise ValueError(f"Missing runtime dependency: {alternatives.strip()}")
            result.append(match)
    return result


def dependency_closure(packages: dict[str, tuple[Path, dict[str, str]]]) -> list[str]:
    selected = set()
    pending = ["python-torch"]
    while pending:
        name = pending.pop()
        if name in selected:
            continue
        if name not in packages:
            raise ValueError(f"Missing package: {name}")
        selected.add(name)
        pending.extend(dependency_names(packages[name][1].get("Depends", ""),
                                        set(packages)))
    return sorted(selected)


def symlink_target(path: Path, stage: Path) -> Path:
    link = os.readlink(path)
    if os.path.isabs(link):
        if not link.startswith("/" + PREFIX + "/"):
            raise ValueError(f"Symlink outside app runtime: {path} -> {link}")
        target = stage / link.lstrip("/")
    else:
        target = path.parent / link
    try:
        target = target.resolve(strict=True)
    except FileNotFoundError as exc:
        raise ValueError(f"Broken runtime symlink: {path} -> {link}") from exc
    if not target.is_relative_to(stage.resolve()):
        raise ValueError(f"Unsupported symlink: {path} -> {link}")
    return target


def runtime_files(root: Path, stage: Path) -> list[tuple[Path, Path]]:
    """Return ZIP paths and real files, expanding safe directory symlinks."""
    entries = []
    resolved_root = root.resolve()

    def visit(directory: Path, relative: Path, ancestors: frozenset[Path]) -> None:
        for child in sorted(directory.iterdir()):
            destination = relative / child.name
            # Documentation is not needed by the embedded interpreter and
            # some Termux packages link it to licenses outside this closure.
            if destination.parts[:2] == ("share", "doc"):
                continue
            if child.is_symlink():
                target = symlink_target(child, stage)
                if target.is_dir():
                    if not target.is_relative_to(resolved_root) or target in ancestors:
                        raise ValueError(f"Unsafe directory symlink: {child}")
                    visit(target, destination, ancestors | {target})
                elif target.is_file():
                    entries.append((destination, target))
                else:
                    raise ValueError(f"Unsupported symlink target: {child}")
            elif child.is_dir():
                target = child.resolve()
                visit(child, destination, ancestors | {target})
            elif child.is_file():
                entries.append((destination, child))

    visit(root, Path(), frozenset({resolved_root}))
    paths = [relative.as_posix() for relative, _ in entries]
    if len(paths) != len(set(paths)):
        raise ValueError("Duplicate runtime paths after symlink expansion")
    return entries


def assemble(packages_dir: Path, output: Path, modules: list[Path],
             wheels: list[Path]) -> dict:
    packages = {}
    for path in packages_dir.glob("*.deb"):
        info = metadata(path)
        name = info.get("Package")
        if not name or name in packages:
            raise ValueError(f"Duplicate or unnamed package: {path}")
        packages[name] = (path, info)
    selected = dependency_closure(packages)
    if packages["python-torch"][1].get("Version", "").split("-")[0] != "2.14.1":
        raise ValueError("Expected PyTorch 2.14.1")
    if not packages["python"][1].get("Version", "").startswith("3.14.6"):
        raise ValueError("Expected CPython 3.14.6")
    package_manifest = []
    with tempfile.TemporaryDirectory(prefix="pocketpy-runtime-") as temporary:
        stage = Path(temporary)
        for name in selected:
            path, info = packages[name]
            if info.get("Architecture") not in ("aarch64", "all"):
                raise ValueError(f"Unexpected architecture: {name}")
            subprocess.run(["dpkg-deb", "-x", str(path), str(stage)], check=True)
            package_manifest.append({"name": name, "version": info.get("Version"),
                                     "sha256": sha256(path)})
        root = stage / PREFIX
        if not root.is_dir():
            raise ValueError(f"Missing app-specific prefix {root}")
        entries = runtime_files(root, stage)
        if not entries:
            raise ValueError("Empty runtime")
        elf_count = 0
        relocatable_elf_count = 0
        for relative, real in entries:
            data = real.read_bytes()
            if FOREIGN_PREFIX in data:
                raise ValueError(f"Foreign app prefix in {relative}")
            if data.startswith(b"\x7fELF"):
                # CPython ships python.o for building extensions. ET_REL files
                # have no PT_LOAD headers and are never loaded by Android.
                if (len(data) >= 20 and data[4:6] == b"\x02\x01" and
                        struct.unpack_from("<HH", data, 16) == (1, 183)):
                    relocatable_elf_count += 1
                    continue
                elf_count += 1
                try:
                    alignments = elf_load_alignments(data)
                except ValueError as exc:
                    raise ValueError(f"Invalid runtime ELF {relative}: {exc}") from exc
                if not alignments or min(alignments) < 0x4000:
                    raise ValueError(f"ELF lacks 16 KB alignment: {relative}")
        output.parent.mkdir(parents=True, exist_ok=True)
        temporary_zip = output.with_suffix(output.suffix + ".assembling")
        module_names = [module.name for module in modules]
        if len(module_names) != len(set(module_names)) or any(
                module.suffix != ".py" or not module.is_file() for module in modules):
            raise ValueError("Bridge modules must be distinct Python source files")
        existing = {relative.as_posix() for relative, _ in entries}
        if any(f"lib/python3.14/site-packages/{name}" in existing for name in module_names):
            raise ValueError("Bridge module would overwrite a package file")
        wheel_manifest = []
        wheel_members = {}
        excluded_wheel_files = []
        for wheel in wheels:
            if not wheel.name.endswith("-none-any.whl"):
                raise ValueError(f"Wheel is not platform independent: {wheel}")
            with zipfile.ZipFile(wheel) as source:
                if source.testzip() is not None:
                    raise ValueError(f"Corrupt wheel: {wheel}")
                wheel_info = [name for name in source.namelist()
                              if re.fullmatch(r"[^/]+\.dist-info/WHEEL", name)]
                if len(wheel_info) != 1 or b"Root-Is-Purelib: true" not in source.read(wheel_info[0]):
                    raise ValueError(f"Wheel is not pure Python: {wheel}")
                for entry in source.infolist():
                    name = entry.filename
                    if entry.is_dir():
                        continue
                    parts = Path(name).parts
                    # SymPy's console manual is installed outside purelib by
                    # pip; it is not needed for imports in the embedded app.
                    if re.fullmatch(r"sympy-[^/]+\.data/data/share/man/man1/isympy\.1", name):
                        excluded_wheel_files.append(f"{wheel.name}:{name}")
                        continue
                    if (name.startswith("/") or ".." in parts or
                            any(part.endswith(".data") for part in parts) or
                            name.endswith((".so", ".dll", ".dylib", ".pyd"))):
                        raise ValueError(f"Unsupported wheel file: {wheel}: {name}")
                    relative = "lib/python3.14/site-packages/" + name
                    if relative in existing or relative in wheel_members:
                        raise ValueError(f"Duplicate runtime file: {relative}")
                    wheel_members[relative] = (wheel, name)
            wheel_manifest.append({"name": wheel.name, "sha256": sha256(wheel)})
        if any(f"lib/python3.14/site-packages/{name}" in wheel_members
               for name in module_names):
            raise ValueError("Bridge module would overwrite a wheel file")
        with zipfile.ZipFile(temporary_zip, "w", compression=zipfile.ZIP_DEFLATED,
                             compresslevel=6, allowZip64=True) as archive:
            for relative, real in entries:
                archive.write(real, "usr/" + relative.as_posix())
            for module in modules:
                archive.write(module,
                              "usr/lib/python3.14/site-packages/" + module.name)
            for wheel in wheels:
                with zipfile.ZipFile(wheel) as source:
                    for member in sorted(name for name in source.namelist()
                                         if not name.endswith("/")):
                        if f"{wheel.name}:{member}" in excluded_wheel_files:
                            continue
                        archive.writestr(
                            "usr/lib/python3.14/site-packages/" + member,
                            source.read(member), compress_type=zipfile.ZIP_DEFLATED)
        with zipfile.ZipFile(temporary_zip) as archive:
            if archive.testzip() is not None:
                raise ValueError("Corrupt runtime ZIP")
        temporary_zip.replace(output)
        manifest = {"app_id": APP_ID, "python": "3.14.6", "torch": "2.14.1",
                    "packages": package_manifest, "files": len(entries),
                    "elf_files": elf_count,
                    "relocatable_elf_files": relocatable_elf_count,
                    "excluded_paths": ["usr/share/doc"],
                    "excluded_wheel_files": excluded_wheel_files,
                    "asset": output.name,
                    "asset_sha256": sha256(output), "asset_bytes": output.stat().st_size,
                    "modules": [{"name": module.name, "sha256": sha256(module)}
                                for module in modules], "wheels": wheel_manifest}
        output.with_suffix(".json").write_text(
            json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        return manifest


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("packages", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--module", type=Path, action="append", default=[])
    parser.add_argument("--wheel", type=Path, action="append", default=[])
    args = parser.parse_args()
    print(json.dumps(assemble(args.packages, args.output, args.module, args.wheel),
                     ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
