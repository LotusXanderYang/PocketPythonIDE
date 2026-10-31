# Pack SDK v1

Android library source module with `ILanguagePack.aidl`, `ITaskCallback.aidl` and `PackContract.java`. Build the consuming sample with `.\build-local.ps1 -Product sample`. A third-party Gradle project can include this module or copy the AIDL and Java files under the same `org.pocketide.pack.v1` package. `python tools/package-pack-sdk.py` creates a reproducible source SDK ZIP. The protocol is documented in `docs/language-pack-api/v1/README.md`.

The prebuilt debug AAR is [`dist/pack-sdk-v1-debug.aar`](dist/pack-sdk-v1-debug.aar), SHA-256 `2cc6f6a95571846f81c3456d165ea6cb497cf9357a6c9c7bde597423abe91a52`. Recreate it from the repository root with `pwsh -File tools/package-pack-sdk-aar.ps1 -Output pack-sdk/dist/pack-sdk-v1-debug-rebuild.aar`; the script refuses to overwrite an existing archive. This workspace's offline Gradle cache lacks optional `lint-gradle:31.13.0`, so the script skips only typedef annotation extraction after confirming the SDK declares no `@IntDef` or `@StringDef`. Normal AIDL and Java compilation and AAR packaging still run. This is a debug SDK artifact, not a published Maven package.

The SDK contains only interfaces and constants. It does not grant SAF access, trust a pack, bundle a language runtime, or perform network requests. Source license: root MIT `LICENSE`.
