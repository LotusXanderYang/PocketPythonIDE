"""Isolated ARM64 compiler feasibility check inside an already installed Termux app.

This is not the independent Language Pack. It only writes under a dedicated
Termux home subdirectory and /data/local/tmp; it never updates Termux packages.
"""

import argparse
import json
import subprocess
from pathlib import Path


SOURCE_DIR = Path(__file__).resolve().parent / "src"
TERMUX = "/data/data/com.termux/files/usr"
PROBE = "/data/data/com.termux/files/home/pocketide-stage5-probe"
ENV = [
    f"LD_LIBRARY_PATH={PROBE}/lib:{TERMUX}/lib",
    f"PATH={TERMUX}/bin:/system/bin",
    f"HOME={PROBE}",
    f"TMPDIR={PROBE}/tmp",
]


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--adb", type=Path, required=True)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--report", type=Path, required=True)
    args = parser.parse_args()
    base = [str(args.adb), "-s", args.serial]

    def adb(*parts: str, stdin: bytes | None = None, timeout: int = 60) -> dict:
        try:
            done = subprocess.run(base + list(parts), input=stdin, capture_output=True,
                                  timeout=timeout, check=False)
            return {"exit": done.returncode,
                    "stdout": done.stdout.decode("utf-8", "replace"),
                    "stderr": done.stderr.decode("utf-8", "replace")}
        except subprocess.TimeoutExpired as problem:
            return {"exit": None, "stdout": "", "stderr": f"host timeout: {problem}"}

    def run_as(*parts: str, stdin: bytes | None = None, timeout: int = 60) -> dict:
        return adb("shell", "run-as", "com.termux", "/system/bin/env", *ENV,
                   *parts, stdin=stdin, timeout=timeout)

    paths = [PROBE, f"{PROBE}/src", f"{PROBE}/bin", f"{PROBE}/tmp"]
    setup = adb("shell", "run-as", "com.termux", "mkdir", "-p", *paths)
    if setup["exit"] != 0:
        raise SystemExit("Cannot prepare isolated Termux probe directory: " + setup["stderr"])

    transfers = {}
    for source in sorted(SOURCE_DIR.iterdir()):
        remote = "/data/local/tmp/pocketide-stage5-" + source.name
        pushed = adb("push", str(source), remote)
        copied = adb("shell", "run-as", "com.termux", "cp", remote,
                     f"{PROBE}/src/{source.name}") if pushed["exit"] == 0 else {"exit": None}
        transfers[source.name] = {"push": pushed["exit"], "copy": copied["exit"]}
    if any(value != {"push": 0, "copy": 0} for value in transfers.values()):
        raise SystemExit("Source transfer failed: " + json.dumps(transfers))

    cases = [
        ("hello_c", "clang", ["-std=c11", "-O0", "-o", f"{PROBE}/bin/hello_c",
                                  f"{PROBE}/src/hello.c"]),
        ("multi_c", "clang", ["-std=c11", "-O0", "-I", f"{PROBE}/src",
                                  "-o", f"{PROBE}/bin/multi_c",
                                  f"{PROBE}/src/multi.c", f"{PROBE}/src/pair.c"]),
        ("hello_cpp", "clang++", ["-std=c++17", "-O0", "-o",
                                        f"{PROBE}/bin/hello_cpp", f"{PROBE}/src/hello.cpp"]),
        ("multi_cpp", "clang++", ["-std=c++17", "-O0", "-I", f"{PROBE}/src",
                                        "-o", f"{PROBE}/bin/multi_cpp",
                                        f"{PROBE}/src/multi.cpp", f"{PROBE}/src/pair.cpp"]),
        ("io", "clang", ["-std=c11", "-O0", "-o", f"{PROBE}/bin/io",
                             f"{PROBE}/src/io.c"]),
        ("broken", "clang", ["-std=c11", "-O0", "-o", f"{PROBE}/bin/broken",
                                 f"{PROBE}/src/broken.c"]),
        ("entry_so", "clang", ["-std=c11", "-O0", "-shared", "-fPIC", "-o",
                                   f"{PROBE}/bin/libentry.so", f"{PROBE}/src/entry.c"]),
        ("runner", "clang", ["-std=c11", "-O0", "-o", f"{PROBE}/bin/runner",
                                 f"{PROBE}/src/runner.c", "-ldl"]),
        ("project_runner", "clang", ["-std=c11", "-O0", "-o",
                                         f"{PROBE}/bin/project_runner",
                                         f"{PROBE}/src/project_runner.c", "-ldl"]),
        ("project_c", "clang", ["-std=c11", "-O0", "-shared", "-fPIC", "-o",
                                     f"{PROBE}/bin/libproject_c.so", f"{PROBE}/src/hello.c"]),
        ("project_c_multi", "clang", ["-std=c11", "-O0", "-shared", "-fPIC",
                                           "-I", f"{PROBE}/src", "-o",
                                           f"{PROBE}/bin/libproject_c_multi.so",
                                           f"{PROBE}/src/multi.c", f"{PROBE}/src/pair.c"]),
        ("project_cpp", "clang++", ["-std=c++17", "-O0", "-shared", "-fPIC",
                                         "-o", f"{PROBE}/bin/libproject_cpp.so",
                                         f"{PROBE}/src/hello.cpp"]),
        ("project_cpp_multi", "clang++", ["-std=c++17", "-O0", "-shared", "-fPIC",
                                               "-I", f"{PROBE}/src", "-o",
                                               f"{PROBE}/bin/libproject_cpp_multi.so",
                                               f"{PROBE}/src/multi.cpp", f"{PROBE}/src/pair.cpp"]),
    ]
    report = {"serial": args.serial, "termux_prefix": TERMUX,
              "probe_dir": PROBE, "transfers": transfers, "cases": {}}
    for name, compiler, flags in cases:
        built = run_as(f"{TERMUX}/bin/{compiler}", *flags, timeout=90)
        record = {"compile": built}
        if built["exit"] == 0 and name not in {
                "entry_so", "runner", "project_runner", "project_c", "project_c_multi",
                "project_cpp", "project_cpp_multi"}:
            record["run"] = run_as(f"{PROBE}/bin/{name}",
                                   stdin=b"stage5-input\n" if name == "io" else None)
        report["cases"][name] = record
        print(f"{name}: compile={built['exit']} run={record.get('run', {}).get('exit')}")
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n",
                           encoding="utf-8")
    print(args.report)


if __name__ == "__main__":
    main()
