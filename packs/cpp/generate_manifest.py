"""Generate the API v1 manifest from the pinned third-party package list."""

import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
LOCK = json.loads((ROOT / "packs/cpp/toolchain-lock.json").read_text(encoding="utf-8"))
LICENSES = {
    "clang": "Apache-2.0; NCSA",
    "libandroid-glob": "BSD-3-Clause",
    "libc++": "NCSA",
    "libcompiler-rt": "Apache-2.0; NCSA",
    "libffi": "MIT",
    "libiconv": "LGPL-2.1; GPL-3.0",
    "libllvm": "Apache-2.0; NCSA",
    "liblzma": "LGPL-2.1; GPL-2.0; GPL-3.0",
    "libxml2": "MIT",
    "lld": "Apache-2.0; NCSA",
    "llvm": "Apache-2.0; NCSA",
    "ncurses": "MIT",
    "ndk-sysroot": "NCSA",
    "zlib": "Zlib",
    "zstd": "GPL-2.0",
}
assert set(LICENSES) == set(LOCK["packages"])
HELLO = '#include <stdio.h>\nint main(void){puts("pack-c-ok");return 0;}\n'
manifest = {
    "schemaVersion": 1,
    "packId": "org.idecpp",
    "version": "0.1.0-stage5",
    "minHostVersion": "0.2.0-alpha.1",
    "minSdk": 26,
    "supportedAbis": ["arm64-v8a"],
    "languageIds": ["c", "cpp"],
    "extensions": [".c", ".cpp", ".cc", ".cxx"],
    "capabilities": ["initialize", "analyze", "complete", "diagnostics", "build", "run", "cancel"],
    "entryService": "org.idecpp.CppPackService",
    "warmupActivity": "org.idecpp.CppWarmupActivity",
    "source": "https://github.com/LotusXanderYang/PocketPythonIDE",
    "license": "MIT (pack); see dependencies and THIRD_PARTY_NOTICES.md",
    "dependencies": [
        {"name": name, "version": info["version"], "license": LICENSES[name]}
        for name, info in LOCK["packages"].items()
    ],
    "selfTests": {
        "initialize": {},
        "analyze": {"source": HELLO, "expectStatus": "ok", "expectOutput": "语法检查通过"},
        "complete": {"source": "pri", "expectStatus": "ok", "expectOutput": "printf"},
        "diagnostics": {"source": "#include <missing_stage5_header.h>\nint main(void){return 0;}\n",
                        "expectStatus": "error", "expectErrorCode": "HEADER_MISSING",
                        "expectLine": 1, "expectColumn": 10},
        "build": {"source": HELLO, "expectStatus": "ok", "expectOutput": "构建成功"},
        "run": {"source": HELLO, "expectStatus": "ok", "expectOutput": "pack-c-ok"},
        "cancel": {"operation": "run", "source": "int main(void){for(;;){} return 0;}\n"},
        "largeOutput": {"source": "#include <stdio.h>\nint main(void){for(int i=0;i<200000;i++)puts(\"large-output\");return 0;}\n",
                        "minBytes": 1000000},
        "versionMismatch": {},
        "crashRecovery": {},
    },
}
output = ROOT / "packs/cpp/src/main/res/raw/pack_manifest.json"
output.parent.mkdir(parents=True, exist_ok=True)
output.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(output)
