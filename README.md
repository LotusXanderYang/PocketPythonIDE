**English** | [简体中文](README.zh-CN.md)

# Pocket Android IDE

A lightweight coding workspace for ARM64 Android tablets. The IDE handles editing, project files, and task views; separately installed language packs provide Python and C/C++ execution, builds, and debugging.

**Current IDE version: `0.2.0-alpha.2` (preview)** · Android API 26+ · ARM64

The `0.2.0-alpha.2` IDE adds visual syntax-color presets, font presets, and readable language-pack names. Python and C/C++ packs remain at `0.2.0-alpha.1` and work with this IDE.

> [Pocket PyTorch Lab is a separate project](https://github.com/LotusXanderYang/PocketPyTorchLab). It is an offline CPU training app and does not require this IDE.

## Highlights

- **Install only the languages you need.** The base IDE APK is about 268 KB and contains no Python, PyTorch, or C/C++ toolchain. Before enabling a language pack, the IDE checks its identity, signature, permissions, declared capabilities, and on-device self-tests.
- **Edit and debug in one workspace.** A file tree, tabs, search, output, problems, build logs, and task status sit alongside debugging controls. Debugging supports breakpoints, stepping, continue, pause, variables, call stacks, and source locations for errors.
- **Designed for touch and physical keyboards.** Long-press a file to delete it or tap a completion suggestion. The editor also supports indentation, paired delimiters, save shortcuts, imported fonts, and custom syntax colors.
- **Choose your own project folder.** Grant a writable folder through Android's system file picker. Runs use a project snapshot; before writing results back, the IDE checks for external edits to avoid overwriting them.
- **Open language-pack API.** Third-party developers can build standalone APKs with [Language Pack API v1](docs/language-pack-api/v1/README.md) and the [SDK](pack-sdk/README.md). The [static pack index](docs/language-pack-index.json) lists only published, verified packages.

## What this version supports

| Component | Available now | Scope |
| --- | --- | --- |
| IDE | Project editing, SAF workspaces, appearance settings, task and debug UI | Cannot execute code on its own |
| [Python pack](packs/python/README.md) | CPython 3.14.6 execution, completion, diagnostics, DAP debugging, cancellation | No PyTorch, NumPy, or general `pip install` |
| [C/C++ pack](packs/cpp/README.md) | Clang/LLD builds and execution, completion, diagnostics, LLDB debugging | Single-target course projects; multi-file projects use `.pocket-cpp.json` |
| [Echo sample pack](sample-pack/README.md) | API and permission-boundary example | For pack developers and testing |

The IDE and both published language packs passed fresh-install, writable-workspace, self-test, and multi-file C project checks on BTK-W00. Python file writeback and C/C++ debugging also have device records from MAG-AN00 and MRO-W00. Android API 26 and devices with 16 KB memory pages have not yet been tested. See each component's README and the [stage 9 acceptance record](docs/STAGE9-FINAL-ACCEPTANCE-2026-10-31.md) for the tested scope.

## Install and get started

1. Download the IDE APK from the [v0.2.0-alpha.2 release](https://github.com/LotusXanderYang/PocketPythonIDE/releases/tag/v0.2.0-alpha.2) and whichever language packs you need from [v0.2.0-alpha.1](https://github.com/LotusXanderYang/PocketPythonIDE/releases/tag/v0.2.0-alpha.1). Check the SHA-256 values on those pages before installing.
2. In the IDE, choose **File → Open Folder** (`文件 → 打开文件夹`) and grant access to a writable folder. In some HarmonyOS Android compatibility environments, native folders can be browsed but reject writes.
3. Open **Language Packs** (`语言包`), review the package name, signature, and permissions, then run **Verify and Enable** (`验证并启用`) and **Enable Passed Capabilities** (`启用通过的能力`). Open a `.py`, `.c`, or `.cpp` file to use the capabilities that passed verification.

The new IDE's package ID is `com.example.pocketandroidide`, so it can coexist with the [earlier `v0.1.2-beta.5` app](https://github.com/LotusXanderYang/PocketPythonIDE/releases/tag/v0.1.2-beta.5). Private files from that app do not migrate automatically; grant the SAF workspace again in the new IDE. An in-place update requires a matching signing key. Back up private files before uninstalling. The `v0.2.0-alpha.1` release predates the repository split and still contains a Lab APK; future Lab source and releases are maintained in the [Lab repository](https://github.com/LotusXanderYang/PocketPyTorchLab).

## Build from source

You need JDK 17, Android SDK Platform 35, and Gradle 8.13. A Gradle wrapper is included. Large language runtimes and toolchains are prepared from their pinned manifests rather than committed to Git.

```powershell
git clone https://github.com/LotusXanderYang/PocketPythonIDE.git
cd PocketPythonIDE
.\build-local.ps1 -Product ide
.\build-local.ps1 -Product python
.\build-local.ps1 -Product sample
```

See the [C/C++ pack guide](packs/cpp/README.md) for its pinned toolchain and build steps, the [third-party source guide](docs/THIRD-PARTY-SOURCE.md) for archive and license provenance, and the [Lab repository](https://github.com/LotusXanderYang/PocketPyTorchLab) for its independent build. The former combined source remains available in the `v0.2.0-alpha.1` tag and earlier commits.

Project-owned source is under the [MIT License](LICENSE). Bundled tools and language-pack dependencies retain their own licenses; the earlier combined runtime is documented in the [historical notices](THIRD_PARTY_NOTICES.md).
