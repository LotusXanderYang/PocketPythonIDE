# Pocket C/C++ language pack: third-party notices

The language-pack Java and native wrapper source is distributed under the
repository's MIT license (`licenses/Pack-MIT.txt`). The Android ARM64 compiler,
headers and libraries are redistributed from the official Termux main APT
repository. The exact package versions, archive sizes and SHA-256 values are
recorded in `packs/cpp/toolchain-lock.json` and
`packs/cpp/lldb-toolchain-lock.json` in the source repository.

The downloaded `InRelease` signature was checked against the installed Termux
`termux-autobuilds.gpg` key; the `Packages.gz` hash matched the signed index,
and every `.deb` hash matched the index. The APK contains a selected runtime
subset rather than the complete installed packages. Package build recipes are
available at `https://github.com/termux/termux-packages/tree/master/packages/`.
Original copyright/license files found inside the pinned `.deb` archives are
included under `assets/upstream-notices/<package>/`. Some upstream archives
do not contain a per-package notice file; the generic license texts in
`assets/licenses/`, pinned package hashes and Termux recipes identify their
terms and source. The Python and LLVM archives also retain license text inside
their toolchain payload where packaged upstream.

| Package | Version | License reported by Termux |
| --- | --- | --- |
| ca-certificates | 1:2026.10.20-1 | MPL-2.0 |
| clang | 21.1.8-3 | Apache-2.0, NCSA |
| gdbm | 1.26-1 | GPL-3.0 |
| libandroid-glob | 0.6-3 | BSD 3-Clause |
| libandroid-posix-semaphore | 0.1-4 | MIT |
| libandroid-spawn | 0.3 | BSD 2-Clause |
| libandroid-support | 29-1 | Apache-2.0, MIT |
| libbz2 | 1.0.8-8 | BSD |
| libc++ | 30 | NCSA |
| libcompiler-rt | 21.1.8-3 | Apache-2.0, NCSA |
| libcrypt | 0.2-6 | BSD 2-Clause |
| libedit | 20260512-3.1-0 | BSD 3-Clause |
| libexpat | 2.9.0 | MIT |
| libffi | 3.8.0 | MIT |
| libiconv | 1.19 | LGPL-2.1, GPL-3.0 |
| libllvm | 21.1.8-3 | Apache-2.0, NCSA |
| liblzma | 5.8.4 | LGPL-2.1, GPL-2.0, GPL-3.0 |
| libsqlite | 3.54.1 | Public Domain |
| libxml2 | 2.15.4-1 | MIT |
| lld | 21.1.8-3 | Apache-2.0, NCSA |
| lldb | 21.1.8-3 | Apache-2.0, NCSA |
| llvm | 21.1.8-3 | Apache-2.0, NCSA |
| ncurses | 6.6.20260307+really6.5.20250830 | MIT |
| ncurses-ui-libs | 6.6.20260307+really6.5.20250830 | MIT |
| ndk-sysroot | 30 | NCSA |
| openssl | 1:3.6.5 | Apache-2.0 |
| python | 3.14.8 | custom (PSF and bundled notices) |
| readline | 8.3.6 | GPL-3.0 |
| zlib | 1.3.2 | Zlib |
| zstd | 1.5.7-1 | GPL-2.0 |

The `licenses/` directory carries the corresponding generic license texts,
including the LLVM exception text. Several packages have project-specific
copyright notices and additional files under their upstream source trees;
the original Termux recipe and source URL remain the authoritative reference
for each package. Exact selected binary content is reproducible with
`packs/cpp/README.md` and the pinned `.deb` archives. No toolchain download
occurs when a student runs the installed language pack.
