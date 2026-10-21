"""Run the IDE's Python/PyTorch checks through the Android UI on an ARM64 device."""

import argparse
import json
import re
import shutil
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path


ROOT = Path(__file__).resolve().parent.parent
LOCAL_ADB = ROOT / ".toolchain/android-sdk/platform-tools/adb.exe"
ADB = str(LOCAL_ADB) if LOCAL_ADB.is_file() else shutil.which("adb") or "adb"
PACKAGE = "com.example.pocketpythonide"
UI_XML = "/sdcard/pocketpy-verify.xml"


def adb(serial, *args, timeout=30):
    result = subprocess.run(
        [str(ADB), "-s", serial, *args],
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
        timeout=timeout,
    )
    if result.returncode:
        raise RuntimeError(f"adb {' '.join(args)}: {result.stderr or result.stdout}")
    return result.stdout.strip()


def hierarchy(serial):
    last_error = None
    for attempt in range(4):
        try:
            adb(serial, "shell", "uiautomator", "dump", UI_XML, timeout=40)
            return ET.fromstring(adb(serial, "shell", "cat", UI_XML))
        except (RuntimeError, ET.ParseError) as error:
            last_error = error
            time.sleep(1 + attempt)
    raise RuntimeError(f"Cannot read Android UI hierarchy: {last_error}")


def find_node(root, attribute, value):
    for node in root.iter("node"):
        if node.get(attribute) == value:
            return node
    raise RuntimeError(f"UI control missing: {attribute}={value!r}")


def tap_node(serial, node):
    bounds = node.get("bounds", "")
    numbers = [int(n) for n in re.findall(r"\d+", bounds)]
    if len(numbers) != 4:
        raise RuntimeError(f"Invalid UI bounds: {bounds!r}")
    x = (numbers[0] + numbers[2]) // 2
    y = (numbers[1] + numbers[3]) // 2
    adb(serial, "shell", "input", "tap", str(x), str(y))


def await_output(serial, marker, timeout=90):
    deadline = time.monotonic() + timeout
    last_text = ""
    while time.monotonic() < deadline:
        root = hierarchy(serial)
        texts = [node.get("text", "") for node in root.iter("node")]
        last_text = "\n".join(texts)
        for item in texts:
            if marker in item:
                return item
        failure_markers = ("[自检失败]", "[运行失败]", "解释器错误：")
        if any(failure in item for item in texts for failure in failure_markers):
            raise RuntimeError(f"Runtime failed before {marker!r}: {last_text[-2000:]}")
        time.sleep(2)
    raise RuntimeError(f"Timed out waiting for {marker!r}; last UI: {last_text[-1000:]}")


def verify(serial, apk, expected_python, expected_torch):
    abi = adb(serial, "shell", "getprop", "ro.product.cpu.abilist")
    if "arm64-v8a" not in abi.split(","):
        raise RuntimeError(f"Physical ARM64 runtime required; device ABIs: {abi}")
    api = adb(serial, "shell", "getprop", "ro.build.version.sdk")
    model = adb(serial, "shell", "getprop", "ro.product.model")
    adb(serial, "install", "-r", str(apk), timeout=120)
    adb(serial, "shell", "am", "force-stop", PACKAGE)
    adb(serial, "shell", "am", "start", "-n", f"{PACKAGE}/.MainActivity")

    tap_node(serial, find_node(hierarchy(serial), "content-desc", "环境信息"))
    tap_node(serial, find_node(hierarchy(serial), "text", "运行自检"))
    # The first modern-runtime launch verifies and extracts its embedded ZIP.
    probe = await_output(serial, "[自检通过]",
                         timeout=300 if expected_python.startswith("3.14") else 90)
    if f"Python {expected_python}" not in probe or f"PyTorch {expected_torch}" not in probe:
        raise RuntimeError(f"Unexpected runtime versions: {probe}")
    for expected in ("矩阵乘法：[[7,10],[15,22]]", "自动求导：[[7,11],[9,13]]", "NumPy 桥接：5.0"):
        if expected not in probe:
            raise RuntimeError(f"Missing runtime result {expected!r}: {probe}")
    if expected_python.startswith("3.14") and "文件读写：通过" not in probe:
        raise RuntimeError(f"Missing Python file I/O check: {probe}")

    tap_node(serial, find_node(hierarchy(serial), "content-desc", "运行脚本"))
    script = await_output(serial, "[运行完成]")
    if f"torch: {expected_torch}" not in script or "x @ x = [[7.0, 10.0], [15.0, 22.0]]" not in script:
        raise RuntimeError(f"Unexpected editor script result: {script}")

    return {"ok": True, "serial": serial, "model": model, "api": api,
            "abi": abi, "python": expected_python, "torch": expected_torch,
            "probe": probe, "editor_script": script}


def verify_core(serial, apk, package, expected_python):
    abi = adb(serial, "shell", "getprop", "ro.product.cpu.abilist")
    if "arm64-v8a" not in abi.split(","):
        raise RuntimeError(f"Physical ARM64 runtime required; device ABIs: {abi}")
    api = adb(serial, "shell", "getprop", "ro.build.version.sdk")
    model = adb(serial, "shell", "getprop", "ro.product.model")
    adb(serial, "install", "-r", str(apk), timeout=120)
    adb(serial, "shell", "am", "force-stop", package)
    adb(serial, "shell", "am", "start", "-n", f"{package}/com.example.pocketpythonide.MainActivity")
    tap_node(serial, find_node(hierarchy(serial), "content-desc", "运行脚本"))
    script = await_output(serial, "[运行完成]")
    for expected in (f"Python {expected_python}", "SQLite ", "OpenSSL "):
        if expected not in script:
            raise RuntimeError(f"Missing {expected!r} in core runtime output: {script}")
    return {"ok": True, "serial": serial, "model": model, "api": api,
            "abi": abi, "python": expected_python, "package": package,
            "editor_script": script}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--apk", type=Path, default=ROOT / "app/build/outputs/apk/debug/app-debug.apk")
    parser.add_argument("--expect-python", required=True)
    parser.add_argument("--expect-torch")
    parser.add_argument("--core-only", action="store_true",
                        help="Verify Python, SQLite and OpenSSL in an interpreter-only APK")
    parser.add_argument("--package", default=PACKAGE)
    parser.add_argument("--report", type=Path, default=ROOT / ".toolchain/device-runtime-report.json")
    args = parser.parse_args()
    if args.core_only:
        report = verify_core(args.serial, args.apk.resolve(), args.package, args.expect_python)
    else:
        if not args.expect_torch:
            parser.error("--expect-torch is required unless --core-only is set")
        if args.package != PACKAGE:
            parser.error("--package is only supported with --core-only")
        report = verify(args.serial, args.apk.resolve(), args.expect_python, args.expect_torch)
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(report, ensure_ascii=True, indent=2))


if __name__ == "__main__":
    main()
