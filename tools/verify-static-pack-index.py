#!/usr/bin/env python3
"""Validate the public language-pack directory before publishing it."""
import json
from pathlib import Path
from urllib.parse import urlparse

from jsonschema import Draft202012Validator, FormatChecker

ROOT = Path(__file__).resolve().parents[1]
INDEX = ROOT / "docs/language-pack-index.json"
SCHEMA = ROOT / "docs/language-pack-api/v1/static-index.schema.json"


def main() -> None:
    document = json.loads(INDEX.read_text(encoding="utf-8"))
    schema = json.loads(SCHEMA.read_text(encoding="utf-8"))
    Draft202012Validator.check_schema(schema)
    Draft202012Validator(schema, format_checker=FormatChecker()).validate(document)
    seen = set()
    for pack in document["packs"]:
        key = (pack["packId"], pack["version"])
        if key in seen:
            raise ValueError(f"duplicate pack and version: {key}")
        seen.add(key)
        url = urlparse(pack["apkUrl"])
        if url.scheme != "https" or not url.netloc or "/releases/download/" not in url.path:
            raise ValueError(f"not a published Release APK: {pack['apkUrl']}")
        if not all(name in pack for name in
                   ("toolchainBytes", "capabilities", "compatibility", "lastTestedDevice",
                    "lastTestedDate")):
            raise ValueError(f"missing release metadata: {key}")
    print(f"public language-pack index: {len(seen)} published packs validated")


if __name__ == "__main__":
    main()
