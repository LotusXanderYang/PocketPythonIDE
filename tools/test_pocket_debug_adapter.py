"""Exercise the real DAP framing and bdb state machine without an Android APK."""

import importlib.util
import json
from pathlib import Path
import socket
import tempfile
import threading


ROOT = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location(
    "pocket_debug_adapter", ROOT / "packs/python/pocket_debug_adapter.py")
adapter = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(adapter)


def request(stream, sequence, command, arguments=None):
    payload = json.dumps({"seq": sequence, "type": "request", "command": command,
                          "arguments": arguments or {}}).encode()
    stream.sendall(b"Content-Length: " + str(len(payload)).encode()
                   + b"\r\n\r\n" + payload)


def next_matching(connection, predicate):
    for _ in range(30):
        message = connection.receive(5)
        if predicate(message):
            return message
    raise AssertionError("DAP event/response not received")


def response(connection, sequence):
    item = next_matching(connection, lambda message: message.get("type") == "response"
                         and message.get("request_seq") == sequence)
    assert item["success"], item
    return item.get("body", {})


def main():
    with tempfile.TemporaryDirectory(dir=ROOT / ".toolchain/checkpoints") as folder:
        workspace = Path(folder)
        (workspace / "main.py").write_text(
            "from helper import twice\n"
            "value = twice(21)\n"
            "print(value)\n"
            "raise ValueError('probe-error')\n", encoding="utf-8")
        (workspace / "helper.py").write_text(
            "def twice(number):\n"
            "    answer = number * 2\n"
            "    return answer\n", encoding="utf-8")
        server, client = socket.socketpair()
        server.settimeout(10)
        client.settimeout(10)
        error = []

        def run_debuggee():
            try:
                with server:
                    debugger = adapter.Debugger(adapter.DapConnection(server),
                                                workspace, "main.py")
                    debugger.configure()
                    debugger.run_program()
            except BaseException as failure:
                error.append(failure)

        worker = threading.Thread(target=run_debuggee, daemon=True)
        worker.start()
        with client:
            dap = adapter.DapConnection(client)
            request(client, 1, "initialize")
            assert response(dap, 1)["supportsConfigurationDoneRequest"]
            request(client, 2, "launch", {"program": "main.py"})
            response(dap, 2)
            request(client, 3, "setBreakpoints", {"source": {"path": "helper.py"},
                     "breakpoints": [{"line": 2}]})
            assert response(dap, 3)["breakpoints"][0]["verified"]
            request(client, 4, "setExceptionBreakpoints", {"filters": ["all"]})
            response(dap, 4)
            request(client, 5, "configurationDone")
            response(dap, 5)
            stopped = next_matching(dap, lambda m: m.get("event") == "stopped")
            assert stopped["body"]["reason"] == "breakpoint", stopped
            # Pause is harmless when a human taps it while already stopped.
            request(client, 100, "pause", {"threadId": 1})
            response(dap, 100)
            request(client, 6, "stackTrace", {"threadId": 1})
            frames = response(dap, 6)["stackFrames"]
            assert frames[0]["source"]["path"] == "helper.py", frames
            assert any(frame["source"]["path"] == "main.py" for frame in frames)
            request(client, 7, "scopes", {"frameId": frames[0]["id"]})
            scopes = response(dap, 7)["scopes"]
            request(client, 8, "variables", {"variablesReference":
                                            scopes[0]["variablesReference"]})
            variables = response(dap, 8)["variables"]
            assert any(item["name"] == "number" and item["value"] == "21"
                       for item in variables), variables
            request(client, 9, "next", {"threadId": 1})
            response(dap, 9)
            stepped = next_matching(dap, lambda m: m.get("event") == "stopped")
            assert stepped["body"]["reason"] == "step", stepped
            request(client, 10, "continue", {"threadId": 1})
            response(dap, 10)
            caught = next_matching(dap, lambda m: m.get("event") == "stopped")
            assert caught["body"]["reason"] == "exception", caught
            request(client, 11, "stackTrace", {"threadId": 1})
            frames = response(dap, 11)["stackFrames"]
            assert frames[0]["source"]["path"] == "main.py" and frames[0]["line"] == 4
            request(client, 12, "exceptionInfo", {"threadId": 1})
            assert response(dap, 12)["exceptionId"] == "ValueError"
            request(client, 13, "continue", {"threadId": 1})
            response(dap, 13)
            exited = next_matching(dap, lambda m: m.get("event") == "exited")
            assert exited["body"]["exitCode"] == 1, exited
            next_matching(dap, lambda m: m.get("event") == "terminated")
        worker.join(10)
        assert not worker.is_alive() and not error, error
    # Pause must remain available when no breakpoint has been configured.
    with tempfile.TemporaryDirectory(dir=ROOT / ".toolchain/checkpoints") as folder:
        workspace = Path(folder)
        (workspace / "loop.py").write_text(
            "counter = 0\nwhile counter < 10000000:\n    counter += 1\n",
            encoding="utf-8")
        server, client = socket.socketpair()
        server.settimeout(10)
        client.settimeout(10)
        failures = []

        def run_loop():
            try:
                with server:
                    debugger = adapter.Debugger(adapter.DapConnection(server),
                                                workspace, "loop.py")
                    debugger.configure()
                    debugger.run_program()
            except BaseException as failed:
                failures.append(failed)

        worker = threading.Thread(target=run_loop, daemon=True)
        worker.start()
        with client:
            dap = adapter.DapConnection(client)
            request(client, 1, "initialize")
            response(dap, 1)
            request(client, 2, "launch", {"program": "loop.py"})
            response(dap, 2)
            request(client, 3, "configurationDone")
            response(dap, 3)
            request(client, 4, "pause", {"threadId": 1})
            response(dap, 4)
            paused = next_matching(dap, lambda m: m.get("event") == "stopped")
            assert paused["body"]["reason"] == "pause", paused
            request(client, 5, "disconnect", {"terminateDebuggee": True})
            response(dap, 5)
        worker.join(10)
        assert not worker.is_alive() and not failures, failures

    print("DAP breakpoint, step, variables, paths, exception, pause, exit: OK")


if __name__ == "__main__":
    main()
