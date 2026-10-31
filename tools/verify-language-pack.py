#!/usr/bin/env python3
"""Offline API v1 schema/APK checks; live Binder checks run in the IDE validator."""
from __future__ import annotations

import argparse
import json
import pathlib
import subprocess
import sys
import zipfile

from jsonschema import Draft202012Validator

ROOT = pathlib.Path(__file__).resolve().parents[1]
SPEC = ROOT / "docs" / "language-pack-api" / "v1"


def load(path: pathlib.Path) -> object:
    return json.loads(path.read_text(encoding="utf-8"))


def cross_check(pack: dict) -> None:
    capabilities = set(pack["capabilities"])
    assert {"initialize", "cancel"} <= capabilities, "initialize and cancel are mandatory"
    fixtures = pack["selfTests"]
    assert capabilities <= fixtures.keys(), "every declared capability needs a fixture"
    if "run" in capabilities:
        assert "largeOutput" in fixtures, "run requires a large output fixture"
    assert pack["entryService"].startswith(pack["packId"] + ".")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--apk", type=pathlib.Path,
                        default=ROOT / "sample-pack/build/outputs/apk/debug/sample-pack-debug.apk")
    args = parser.parse_args()
    for kind, schema_file in (("pack", "pack-manifest.schema.json"),
                              ("index", "static-index.schema.json")):
        schema = load(SPEC / schema_file)
        Draft202012Validator.check_schema(schema)
        validator = Draft202012Validator(schema)
        valid = load(SPEC / "examples" / f"valid-{kind}.json")
        invalid = load(SPEC / "examples" / f"invalid-{kind}.json")
        validator.validate(valid)
        assert list(validator.iter_errors(invalid)), f"invalid {kind} example accepted"
        if kind == "pack":
            cross_check(valid)
        print(f"{kind} schema: valid/invalid examples passed")
    asset = load(ROOT / "sample-pack/src/main/res/raw/pack_manifest.json")
    assert asset == load(SPEC / "examples/valid-pack.json"), "sample asset differs from example"
    Draft202012Validator(load(SPEC / "pack-manifest.schema.json")).validate(asset)
    cross_check(asset)
    if not args.apk.is_file():
        raise FileNotFoundError(args.apk)
    with zipfile.ZipFile(args.apk) as apk:
        names = apk.namelist()
        assert "res/raw/pack_manifest.json" in names, "APK lacks raw manifest"
        assert "assets/LICENSE.txt" in names, "APK lacks license"
        assert not any(name.endswith(".so") for name in names), "sample unexpectedly bundles native code"
    aapt = ROOT / ".toolchain/android-sdk/build-tools/35.0.0/aapt.exe"
    if aapt.is_file():
        manifest = subprocess.check_output([str(aapt), "dump", "xmltree", str(args.apk),
                                            "AndroidManifest.xml"], text=True,
                                           encoding="utf-8", errors="replace")
        for marker in ("org.pocketide.sample.echo.EchoPackService",
                       "org.pocketide.pack.v1.LANGUAGE_PACK",
                       "org.pocketide.pack.v1.MANIFEST", "android:exported"):
            assert marker in manifest, f"APK manifest lacks {marker}"
    sdk = ROOT / "pack-sdk/dist/pack-sdk-v1-source.zip"
    assert sdk.is_file(), "SDK source ZIP missing"
    with zipfile.ZipFile(sdk) as archive:
        for path in ("pack-sdk/src/main/aidl/org/pocketide/pack/v1/ILanguagePack.aidl",
                     "pack-sdk/src/main/aidl/org/pocketide/pack/v1/ITaskCallback.aidl",
                     "pack-sdk/src/main/java/org/pocketide/pack/v1/PackContract.java"):
            assert archive.read(path) == (ROOT / path).read_bytes(), f"SDK archive stale: {path}"
    print(f"sample APK: {args.apk.stat().st_size} bytes; resource and no-native checks passed")
    print(f"SDK source ZIP: {sdk.stat().st_size} bytes; source parity passed")
    print("Device Binder, cancellation, permission, crash and uninstall behavior: NOT TESTED here")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (AssertionError, FileNotFoundError) as exc:
        print(f"FAIL: {exc}", file=sys.stderr)
        sys.exit(1)
