#!/usr/bin/env python3
"""Device gate for API v1. Installs debug APKs and operates the IDE UI; preserves app data."""
from __future__ import annotations

import argparse
import hashlib
import json
import pathlib
import re
import shutil
import subprocess
import time
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parents[1]
ADB = ROOT / ".toolchain/android-sdk/platform-tools/adb.exe"
if not ADB.is_file():
    ADB = shutil.which("adb") or "adb"
UI_XML = "/sdcard/pocket-pack-verify.xml"


def command(serial: str, *args: str, timeout: int = 45) -> str:
    result = subprocess.run([str(ADB), "-s", serial, *args], capture_output=True,
                            text=True, encoding="utf-8", errors="replace", timeout=timeout)
    if result.returncode:
        raise RuntimeError(f"adb {' '.join(args)}: {result.stderr or result.stdout}")
    return result.stdout.strip()


def hierarchy(serial: str) -> ET.Element:
    error = None
    for attempt in range(4):
        try:
            command(serial, "shell", "uiautomator", "dump", UI_XML, timeout=50)
            return ET.fromstring(command(serial, "shell", "cat", UI_XML))
        except (RuntimeError, ET.ParseError) as failure:
            error = failure
            time.sleep(1 + attempt)
    raise RuntimeError(f"Cannot inspect UI: {error}")


def texts(root: ET.Element) -> list[str]:
    return [node.get("text", "") for node in root.iter("node")]


def find(root: ET.Element, key: str, phrase: str) -> ET.Element:
    for node in root.iter("node"):
        if phrase == node.get(key, ""):
            return node
    for node in root.iter("node"):
        if phrase in node.get(key, ""):
            return node
    raise RuntimeError(f"Missing UI item {key}={phrase!r}; visible={texts(root)[-20:]}")


def tap(serial: str, node: ET.Element) -> None:
    bounds = [int(value) for value in re.findall(r"\d+", node.get("bounds", ""))]
    if len(bounds) != 4:
        raise RuntimeError(f"Bad bounds: {node.get('bounds')}")
    command(serial, "shell", "input", "tap", str((bounds[0] + bounds[2]) // 2),
            str((bounds[1] + bounds[3]) // 2))


def await_text(serial: str, phrase: str, timeout: int = 90) -> ET.Element:
    deadline = time.monotonic() + timeout
    last = []
    while time.monotonic() < deadline:
        root = hierarchy(serial)
        last = texts(root)
        for node in root.iter("node"):
            if phrase in node.get("text", ""):
                return root
        time.sleep(2)
    raise RuntimeError(f"Timed out waiting for {phrase!r}; UI={last[-20:]}")


def digest(path: pathlib.Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            h.update(block)
    return h.hexdigest()


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--operate-ui", action="store_true",
                        help="required: installs three APKs and runs UI validation")
    parser.add_argument("--report", type=pathlib.Path,
                        default=ROOT / ".toolchain/language-pack-device-report.json")
    args = parser.parse_args()
    if not args.operate_ui:
        parser.error("Pass --operate-ui after saving any open editor changes")
    serial = args.serial
    abi = command(serial, "shell", "getprop", "ro.product.cpu.abilist")
    if "arm64-v8a" not in abi.split(","):
        raise RuntimeError(f"ARM64 device required, got {abi}")
    model = command(serial, "shell", "getprop", "ro.product.model")
    api = command(serial, "shell", "getprop", "ro.build.version.sdk")
    apks = {
        "ide": ROOT / "ide/build/outputs/apk/debug/ide-debug.apk",
        "sample": ROOT / "sample-pack/build/outputs/apk/debug/sample-pack-debug.apk",
        "probe": ROOT / "pack-probe/build/outputs/apk/debug/pack-probe-debug.apk",
    }
    for path in apks.values():
        command(serial, "install", "-r", str(path), timeout=120)
    # Some Honor builds block cold starts of another package's exported service.
    # Launch the sample's own entry point first, as a user would after install.
    command(serial, "shell", "am", "start", "-S", "-n",
            "org.pocketide.sample.echo/.PackInfoActivity")
    deadline = time.monotonic() + 10
    while True:
        try:
            pid = command(serial, "shell", "pidof", "org.pocketide.sample.echo")
        except RuntimeError:
            pid = ""
        if pid:
            break
        if time.monotonic() >= deadline:
            raise RuntimeError("Sample pack process did not start")
        time.sleep(0.5)
    command(serial, "shell", "am", "force-stop", "org.pocketide.test.probe")
    command(serial, "shell", "am", "start", "-n",
            "org.pocketide.test.probe/.ProbeActivity")
    await_text(serial, "PERMISSION_DENIED_OK", 30)
    # Exercise the host's cold-start recovery instead of relying on the process
    # kept alive by the permission test.
    command(serial, "shell", "am", "force-stop", "org.pocketide.sample.echo")
    command(serial, "shell", "am", "start", "-n",
            "com.example.pocketandroidide/com.example.pocketpythonide.IdeActivity")
    tap(serial, find(hierarchy(serial), "content-desc", "语言能力"))
    tap(serial, find(hierarchy(serial), "text", "org.pocketide.sample.echo"))
    tap(serial, find(hierarchy(serial), "text", "验证并启用"))
    root = await_text(serial, "验证完成", 150)
    report_text = "\n".join(texts(root))
    for gate in ("initialize: 通过", "analyze: 通过", "complete: 通过",
                 "run: 通过", "cancel: 通过", "diagnostics: 通过",
                 "largeOutput: 通过", "crashRecovery: 通过", "versionMismatch: 通过"):
        if gate not in report_text:
            raise RuntimeError(f"Validation gate missing {gate}: {report_text[-3000:]}")
    tap(serial, find(root, "text", "启用通过的能力"))
    tap(serial, find(hierarchy(serial), "content-desc", "语言能力"))
    root = await_text(serial, "当前：org.pocketide.sample.echo", 15)
    tap(serial, find(root, "text", "当前：org.pocketide.sample.echo"))
    tap(serial, find(hierarchy(serial), "text", "禁用"))
    report = {
        "device": model, "api": api, "abi": abi,
        "apkSha256": {name: digest(path) for name, path in apks.items()},
        "permissionDenied": True, "declaredCapabilities": "passed",
        "largeOutput": "passed", "crashRecovery": "passed",
        "versionMismatch": "passed", "disabled": True,
        "uninstallRecovery": "not run; no automatic uninstall",
    }
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(report, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
