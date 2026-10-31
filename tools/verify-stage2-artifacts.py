"""Read-only checks for the three APKs and the Lab runtime asset."""

import hashlib
import json
import re
import subprocess
from pathlib import Path
import zipfile

ROOT = Path(__file__).resolve().parent.parent
BASELINE_HASH = "bb898c236cb14f0218a799d7217432c65144b2ad37b2248cc7075c77f1b63ccb"
IDS = {
    "app": "com.example.pocketpythonide",
    "ide": "com.example.pocketandroidide",
    "lab": "com.example.pocketpythonlab",
}
VERSIONS = {"app": ("9", "0.1.2-beta.5"),
            "ide": ("1", "0.2.0-alpha.1"),
            "lab": ("1", "0.2.0-alpha.1")}
APKS = {
    "app": ROOT / "app/build/outputs/apk/debug/app-debug.apk",
    "ide": ROOT / "ide/build/outputs/apk/debug/ide-debug.apk",
    "lab": ROOT / "lab/build/outputs/apk/debug/lab-debug.apk",
}


def digest(path):
    value = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            value.update(block)
    return value.hexdigest()


def main():
    aapt = ROOT / ".toolchain/android-sdk/build-tools/35.0.0/aapt.exe"
    if not aapt.is_file():
        raise SystemExit("Android SDK aapt.exe unavailable; set up the local SDK first")
    for product, path in APKS.items():
        actual = digest(path)
        if product == "app" and actual != BASELINE_HASH:
            raise SystemExit("beta.5 APK changed")
        badging = subprocess.check_output([str(aapt), "dump", "badging", str(path)],
                                          text=True, errors="replace")
        match = re.search(r"^package: name='([^']+)' versionCode='([^']+)' "
                          r"versionName='([^']+)'", badging, re.MULTILINE)
        if (not match or match.group(1) != IDS[product] or
                (match.group(2), match.group(3)) != VERSIONS[product]):
            raise SystemExit(f"Wrong APK identity: {product}")
        with zipfile.ZipFile(path) as archive:
            names = set(archive.namelist())
            if product == "ide":
                if any("runtime.zip" in name or "torch" in name
                       or name.startswith("lib/") for name in names):
                    raise SystemExit("IDE contains runtime/native components")
                dex = archive.read("classes.dex")
                if b"torch" in dex or b"numpy" in dex:
                    raise SystemExit("IDE contains scientific-runtime UI/catalog strings")
            if product == "lab":
                manifest = json.loads(archive.read("assets/runtime.json"))
                runtime = archive.read("assets/runtime.zip")
                if (manifest["app_id"] != IDS["lab"] or
                        hashlib.sha256(runtime).hexdigest() != manifest["asset_sha256"] or
                        "lib/arm64-v8a/libnativepythonbridge.so" not in names):
                    raise SystemExit("Lab runtime asset mismatch")
        print(f"{product}: {match.group(1)} {match.group(3)} "
              f"{path.stat().st_size} bytes sha256:{actual}")
    print("Static stage-2 artifact checks passed; device behavior is unverified.")


if __name__ == "__main__":
    main()
