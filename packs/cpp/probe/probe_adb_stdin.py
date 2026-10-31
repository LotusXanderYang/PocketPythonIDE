"""Check whether adb shell -T forwards bytes before stdin closes."""

import subprocess
import threading
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
ADB = ROOT / ".toolchain/android-sdk/platform-tools/adb.exe"

with subprocess.Popen([ADB, "-s", "AF3GUT4516002511", "shell", "-T",
                       "run-as org.idecpp /system/bin/cat"],
                      stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                      stderr=subprocess.PIPE) as process:
    reply = []
    reader = threading.Thread(target=lambda: reply.append(process.stdout.read(6)),
                              daemon=True)
    reader.start()
    process.stdin.write(b"hello\n")
    process.stdin.flush()
    reader.join(3)
    print("live_reply", reply, "waiting", reader.is_alive())
    process.stdin.close()
    process.wait(timeout=5)
    print("remaining", process.stdout.read())
