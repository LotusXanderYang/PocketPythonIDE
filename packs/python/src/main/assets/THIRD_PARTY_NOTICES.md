# Python 基础语言包第三方组件

运行时由仓库固定的 `app/src/main/assets/runtime.zip` 派生，源 ZIP SHA-256 为
`45e7c3baa507b2c6b6d8b0338b5ef22d947b4cfed6e618234a5f57f4f527f7a4`。
脚本只保留 CPython 3.14 标准库、原生扩展及 ELF 依赖闭包；不包含 PyTorch、NumPy、pip 或第三方 site-packages。
CPython 的 PSF 许可证正文随运行时位于 `usr/lib/python3.14/LICENSE.txt`。
本 APK 的 `assets/licenses/` 保存下表所需的常见许可证文本；各组件具体声明与来源以固定的
Termux 配方和仓库根目录 `THIRD_PARTY_NOTICES.md` 为准。

| 组件 | 版本 | 许可证 |
| --- | --- | --- |
| CPython | 3.14.6 | PSF-2.0 / CPython LICENSE |
| libandroid-posix-semaphore | 0.1-4 | MIT |
| libandroid-support | 29 | Apache-2.0、MIT |
| libbz2 | 1.0.8-8 | BSD |
| libexpat | 2.9.0 | MIT |
| libffi | 3.8.0 | MIT |
| gdbm | 1.26-1 | GPL-3.0 |
| liblzma | 5.8.4 | LGPL-2.1、GPL-2.0、GPL-3.0 |
| ncurses / panel | 6.5 兼容版 | MIT |
| OpenSSL | 3.6.5 | Apache-2.0 |
| readline | 8.3.6 | GPL-3.0 |
| SQLite | 3.54.1 | Public Domain |
| zlib | 1.3.2 | Zlib |
| zstd | 1.5.7 | GPL-2.0 |

语言包自身的 Java/Python 胶水代码采用仓库根目录 MIT 许可证。派生脚本、源包哈希、
ELF 依赖闭包、最终 ZIP 哈希记录在 `packs/python/prepare_runtime.py` 与 `runtime.json`。
