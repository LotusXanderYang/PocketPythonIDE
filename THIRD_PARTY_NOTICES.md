# Third-party components

PocketPythonIDE's own source is MIT licensed. The embedded runtime and wheels are independent upstream works. Their licenses are **not** replaced by the project's MIT license. Versions, package SHA-256 values, and the exact runtime composition are recorded in [`runtime.json`](app/src/main/assets/runtime.json).

The ARM64 packages were built from [Termux packages at `6be420e`](https://github.com/termux/termux-packages/commit/6be420e7536d1e228f3ffee2eb74ca2736a73fc1) with the app-specific installation prefix. License identifiers below are those recorded by the pinned Termux package recipes; consult each linked recipe and upstream source for complete terms and notices.

| Component | Version | License identifier in recipe |
| --- | --- | --- |
| [CPython](https://github.com/termux/termux-packages/tree/6be420e7536d1e228f3ffee2eb74ca2736a73fc1/packages/python) | 3.14.6 | PSF-2.0 / custom |
| [PyTorch](https://github.com/termux/termux-packages/tree/6be420e7536d1e228f3ffee2eb74ca2736a73fc1/packages/python-torch) | 2.14.1 | BSD-3-Clause |
| [NumPy](https://github.com/termux/termux-packages/tree/6be420e7536d1e228f3ffee2eb74ca2736a73fc1/packages/python-numpy) | 2.5.4 | BSD-3-Clause |
| [pip](https://github.com/pypa/pip) | 26.2.1 | MIT |
| abseil-cpp | 20260526.0 | Apache-2.0 |
| ca-certificates | 2026.09.25 | MPL-2.0 |
| gdbm | 1.26 | GPL-3.0 |
| libandroid-execinfo | 0.1 | BSD-2-Clause |
| libandroid-posix-semaphore | 0.1 | MIT |
| libandroid-support | 29 | Apache-2.0, MIT |
| libbz2 | 1.0.8 | BSD |
| libc++ | 30 | NCSA |
| libcrypt | 0.2 | BSD-2-Clause |
| libexpat | 2.9.0 | MIT |
| libffi | 3.8.0 | MIT |
| liblzma | 5.8.4 | LGPL-2.1, GPL-2.0, GPL-3.0 |
| libopenblas | 0.3.34 | BSD-3-Clause |
| libprotobuf | 36.2 | BSD-3-Clause, MIT |
| libsqlite | 3.54.1 | Public Domain |
| ncurses, ncurses-ui-libs | 6.5 compatible | MIT |
| OpenSSL | 3.6.5 | Apache-2.0 |
| readline | 8.3.6 | GPL-3.0 |
| zlib | 1.3.2 | Zlib |
| zstd | 1.5.7 | GPL-2.0 (recipe identifier; inspect upstream's available terms) |

The nine packaged pure Python wheels are `filelock` (MIT), `fsspec` (BSD-3-Clause), `Jinja2` (BSD-3-Clause), `MarkupSafe` (BSD-3-Clause), `mpmath` (BSD), `NetworkX` (BSD-3-Clause), `setuptools` (MIT), `SymPy` (BSD), and `typing_extensions` (PSF-2.0). Their exact versions and SHA-256 values appear in [`runtime.json`](app/src/main/assets/runtime.json) and [`packaging/pure-wheels.sha256`](packaging/pure-wheels.sha256). Their wheel metadata and bundled license files are retained inside `runtime.zip` where provided by upstream.

The native runtime may include additional vendored components under PyTorch, NumPy, Python, pip, and OpenSSL. Review their bundled `*.dist-info/licenses/` and source distributions before repackaging or redistributing a modified binary. The Termux source pin, package recipes, source URLs and build patches are provided so that the corresponding runtime can be rebuilt.
