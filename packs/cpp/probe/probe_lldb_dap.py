"""Exercise the packaged LLDB DAP over adb shell stdio without IDE wiring."""

import json
from pathlib import Path
import subprocess
import threading

ROOT = Path(__file__).resolve().parents[3]
ADB = ROOT / ".toolchain/android-sdk/platform-tools/adb.exe"
SERIAL = "AF3GUT4516002511"
PACKAGE = "org.idecpp"


def frame(message):
    payload = json.dumps(message).encode()
    return b"Content-Length: " + str(len(payload)).encode() + b"\r\n\r\n" + payload


def main():
    path = subprocess.check_output([ADB, "-s", SERIAL, "shell", "pm", "path", PACKAGE],
                                   text=True, timeout=10).strip()
    if not path.startswith("package:/data/app/") or not path.endswith("/base.apk"):
        raise ValueError("Unexpected package path: " + path)
    native = path.removeprefix("package:").removesuffix("/base.apk") + "/lib/arm64"
    prefix = "/data/user/0/org.idecpp/files/usr"
    command = (f"LD_LIBRARY_PATH={prefix}/lib:{native} PYTHONHOME={prefix} "
               f"exec {native}/liblldb_dap.so")
    shell_command = f"run-as {PACKAGE} /system/bin/sh -c '{command}'"
    process = subprocess.Popen([ADB, "-s", SERIAL, "shell", "-T", shell_command],
                               stdin=subprocess.PIPE,
                               stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    replies = []
    errors = []

    def read_reply():
        try:
            while True:
                header = bytearray()
                while not header.endswith(b"\r\n\r\n"):
                    value = process.stdout.read(1)
                    if not value:
                        raise EOFError("DAP stdout closed")
                    header.extend(value)
                    if len(header) > 4096:
                        raise ValueError("DAP header too long")
                length = int(header.decode().split("Content-Length: ", 1)[1]
                             .split("\r\n", 1)[0])
                payload = process.stdout.read(length)
                if len(payload) != length:
                    raise EOFError("DAP payload truncated")
                message = json.loads(payload)
                replies.append(message)
                if message.get("command") == "initialize" and message.get("type") == "response":
                    return
        except Exception as failure:
            errors.append(failure)

    reader = threading.Thread(target=read_reply, daemon=True)
    reader.start()
    process.stdin.write(frame({"seq": 1, "type": "request", "command": "initialize",
                               "arguments": {"clientID": "pocketide-probe", "adapterID": "lldb"}}))
    process.stdin.flush()
    reader.join(30)
    if reader.is_alive() or errors or not replies:
        process.kill()
        process.wait(timeout=5)
        raise SystemExit(f"LLDB DAP initialize missing: {replies}, {errors}; "
                         f"stderr={process.stderr.read(2000)!r}")
    process.stdin.write(frame({"seq": 2, "type": "request", "command": "disconnect",
                               "arguments": {}}))
    process.stdin.flush()
    process.stdin.close()
    process.wait(timeout=10)
    print("exit", process.returncode)
    print("replies", json.dumps(replies, ensure_ascii=False)[:3000])
    print("stderr", process.stderr.read(1000).decode(errors="replace"))


if __name__ == "__main__":
    main()
