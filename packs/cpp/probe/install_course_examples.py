"""Add named stage-5 course examples to the debug IDE without touching other files."""

import argparse
import subprocess
from pathlib import Path


ROOT = Path(__file__).resolve().parents[3]
EXAMPLES = ROOT / "packs/cpp/examples"


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--adb", required=True)
    parser.add_argument("--serial", required=True)
    args = parser.parse_args()
    base = [args.adb, "-s", args.serial]

    def adb(*parts: str, check: bool = True) -> subprocess.CompletedProcess:
        return subprocess.run(base + list(parts), capture_output=True, check=check)

    for folder in sorted(EXAMPLES.iterdir()):
        if not folder.is_dir():
            continue
        target = f"files/scripts/{folder.name}"
        present = adb("shell", "run-as", "com.example.pocketandroidide", "test", "-e", target,
                      check=False)
        if present.returncode == 0:
            raise SystemExit(f"Refusing to overwrite existing IDE project: {target}")
        adb("shell", "run-as", "com.example.pocketandroidide", "mkdir", target)
        for file in sorted(folder.iterdir()):
            if not file.is_file():
                continue
            remote = f"/data/local/tmp/pocketide-stage5-{folder.name}-{file.name}"
            adb("push", str(file), remote)
            try:
                adb("shell", "run-as", "com.example.pocketandroidide", "cp", remote,
                    f"{target}/{file.name}")
            finally:
                adb("shell", "rm", "-f", remote)
        print("installed", target)


if __name__ == "__main__":
    main()
