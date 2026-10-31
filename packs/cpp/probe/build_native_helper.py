"""Build the tiny ARM64 linker argv[0] wrapper on the connected test device."""

import argparse
import subprocess
from pathlib import Path


ROOT = Path(__file__).resolve().parents[3]
HELPERS = (
    (ROOT / "packs/cpp/native/lld_wrapper.c", "lld_wrapper", "liblld_wrapper.so", []),
    (ROOT / "packs/cpp/probe/src/project_runner.c", "project_runner",
     "libprojectrunner.so", ["-ldl"]),
)
OUTPUT_DIR = ROOT / "packs/cpp/src/main/jniLibs/arm64-v8a"
TERMUX = "/data/data/com.termux/files/usr"
PROBE = "/data/data/com.termux/files/home/pocketide-stage5-probe"


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--adb", required=True)
    parser.add_argument("--serial", required=True)
    args = parser.parse_args()
    base = [args.adb, "-s", args.serial]

    def run(*parts: str) -> bytes:
        result = subprocess.run(base + list(parts), capture_output=True, check=True)
        return result.stdout

    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    for source, stem, name, libraries in HELPERS:
        remote = f"/data/local/tmp/pocketide-stage5-{stem}.c"
        run("push", str(source), remote)
        run("shell", "run-as", "com.termux", "cp", remote,
            f"{PROBE}/src/{stem}.c")
        run("shell", "run-as", "com.termux", "/system/bin/env",
            f"LD_LIBRARY_PATH={PROBE}/lib:{TERMUX}/lib",
            f"PATH={TERMUX}/bin:/system/bin",
            f"TMPDIR={PROBE}/tmp",
            f"{TERMUX}/bin/clang", "-std=c11", "-O2", "-o",
            f"{PROBE}/bin/{stem}", f"{PROBE}/src/{stem}.c", *libraries)
        output = OUTPUT_DIR / name
        output.write_bytes(run("exec-out", "run-as", "com.termux", "cat",
                               f"{PROBE}/bin/{stem}"))
        print(output, output.stat().st_size)


if __name__ == "__main__":
    main()
