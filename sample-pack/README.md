# Pocket Echo sample pack

This independently installed APK demonstrates API v1 without a native interpreter. It accepts `.echo` files. `ECHO text` prints text, `WAIT ms` pauses up to 30 seconds, `REPEAT n` writes bounded output, and `ERROR` reports a 1-based source position. The sample has no network permission. Its `testCrash` and `testVersionMismatch` operations exist only for compatibility validation; they are not declared user capabilities.

Build with `.\build-local.ps1 -Product sample`, install the APK after installing the IDE, then use the IDE's left extension icon to inspect and validate it. Create `demo.echo` containing `ECHO hello` and run it. The sample package's source and bundled `LICENSE.txt` use the root MIT license. See `docs/language-pack-api/v1/README.md` for the full protocol and validation status.
