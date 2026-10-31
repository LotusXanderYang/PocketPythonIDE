#!/usr/bin/env python3
"""Inventory copyright/license files inside locally pinned C/C++ .deb archives."""
import hashlib
import io
import json
from pathlib import Path
import subprocess
import tarfile
import argparse

ROOT = Path(__file__).resolve().parents[1]
LOCK = json.loads((ROOT / 'packs/cpp/lldb-toolchain-lock.json').read_text(encoding='utf-8'))['packages']
SEARCH = [ROOT / '.toolchain/stage8-probe/debs', ROOT / '.toolchain/stage5-probe/official-debs',
          ROOT / '.toolchain/runtime-debs']


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--extract', action='store_true',
                        help='copy original notice files to APK assets without overwriting')
    args = parser.parse_args()
    archives = [p for folder in SEARCH if folder.is_dir() for p in folder.glob('*.deb')]
    by_size = {}
    missing = []
    for archive in archives:
        by_size.setdefault(archive.stat().st_size, []).append(archive)
    for name, info in LOCK.items():
        direct = ROOT / '.toolchain/stage8-probe/debs' / Path(info['filename']).name
        candidates = ([direct] if direct.exists() else []) + by_size.get(info['size'], [])
        match = next((p for p in candidates if p.stat().st_size == info['size']
                      if hashlib.sha256(p.read_bytes()).hexdigest() == info['sha256']), None)
        if match is None:
            print(f'{name}: MISSING pinned archive')
            missing.append(name)
            continue
        members = subprocess.run(['tar', '-tf', str(match)], capture_output=True,
                                 text=True, check=True).stdout.splitlines()
        data = next((m for m in members if m.startswith('data.tar.')), None)
        if data is None:
            print(f'{name}: MISSING data archive')
            missing.append(name)
            continue
        payload = subprocess.run(['tar', '-xOf', str(match), data], capture_output=True, check=True).stdout
        with tarfile.open(fileobj=io.BytesIO(payload), mode='r:*') as content:
            notices = [m for m in content.getmembers() if m.isfile() and
                       (('/share/doc/' in m.name and any(x in m.name.lower() for x in
                         ('license', 'copying', 'copyright'))) or
                        m.name.lower().endswith('/license.txt'))]
            if args.extract:
                for member in notices:
                    suffix = member.name.split('/usr/', 1)[-1].replace('/', '__')
                    target = ROOT / 'packs/cpp/src/main/assets/upstream-notices' / name / suffix
                    target.parent.mkdir(parents=True, exist_ok=True)
                    original = content.extractfile(member).read()
                    if target.exists() and target.read_bytes() != original:
                        raise RuntimeError(f'notice would overwrite different content: {target}')
                    if not target.exists():
                        target.write_bytes(original)
            else:
                for member in notices:
                    suffix = member.name.split('/usr/', 1)[-1].replace('/', '__')
                    target = ROOT / 'packs/cpp/src/main/assets/upstream-notices' / name / suffix
                    if not target.is_file() or target.read_bytes() != content.extractfile(member).read():
                        missing.append(f'{name}:{suffix}')
        print(f'{name}: {len(notices)} notice files: {", ".join(m.name for m in notices[:6])}')
    if missing:
        raise SystemExit('missing or changed pinned notice assets: ' + ', '.join(missing))


if __name__ == '__main__':
    main()
