"""Repeat a DAP session over the exact two-file course example."""

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


def matching(connection, predicate):
    for _ in range(40):
        message = connection.receive(5)
        if predicate(message):
            return message
    raise AssertionError("DAP message not received")


def response(connection, sequence):
    value = matching(connection, lambda message: message.get("type") == "response"
                     and message.get("request_seq") == sequence)
    assert value.get("success"), value
    return value.get("body", {})


class RecordingConnection:
    def __init__(self, stream):
        self.delegate = adapter.DapConnection(stream)
        self.output = []

    def receive(self, timeout):
        message = self.delegate.receive(timeout)
        if message.get("event") == "output":
            self.output.append(message.get("body", {}).get("output", ""))
        return message


def run_session(workspace, mode):
    server, client = socket.socketpair()
    server.settimeout(10)
    client.settimeout(10)
    failures = []

    def execute():
        try:
            with server:
                debugger = adapter.Debugger(adapter.DapConnection(server),
                                            workspace, "debug_main.py")
                debugger.configure()
                debugger.run_program()
        except BaseException as failure:
            failures.append(failure)

    thread = threading.Thread(target=execute, daemon=True)
    thread.start()
    with client:
        dap = RecordingConnection(client)
        request(client, 1, "initialize")
        response(dap, 1)
        request(client, 2, "launch", {"program": "debug_main.py"})
        response(dap, 2)
        request(client, 3, "setBreakpoints", {"source": {"path": "debug_main.py"},
                "breakpoints": [{"line": 8}]})
        assert response(dap, 3)["breakpoints"][0]["verified"]
        request(client, 4, "setBreakpoints", {"source": {"path": "debug_helper.py"},
                "breakpoints": [{"line": 3}]})
        assert response(dap, 4)["breakpoints"][0]["verified"]
        request(client, 5, "configurationDone")
        response(dap, 5)
        first = matching(dap, lambda item: item.get("event") == "stopped")
        request(client, 6, "stackTrace", {"threadId": 1})
        frames = response(dap, 6)["stackFrames"]
        assert frames[0]["source"]["path"] == "debug_main.py", frames
        assert frames[0]["line"] == 8, frames
        if mode == "disconnect":
            request(client, 7, "disconnect", {"terminateDebuggee": True})
            response(dap, 7)
        else:
            request(client, 7, "next" if mode == "step_to_exit" else mode,
                    {"threadId": 1})
            response(dap, 7)
            second = matching(dap, lambda item: item.get("event") == "stopped")
            request(client, 8, "stackTrace", {"threadId": 1})
            frames = response(dap, 8)["stackFrames"]
            assert frames[0]["source"]["path"] == "debug_helper.py", (mode, frames, second)
            assert frames[0]["line"] == 3, (mode, frames, second)
            if mode == "step_to_exit":
                exit_event = None
                for sequence in range(9, 25):
                    request(client, sequence, "next", {"threadId": 1})
                    response(dap, sequence)
                    event = matching(dap, lambda item: item.get("event")
                                     in ("stopped", "exited"))
                    if event["event"] == "exited":
                        exit_event = event
                        break
                assert exit_event is not None, "Stepping never reached program exit"
            else:
                request(client, 9, "continue", {"threadId": 1})
                response(dap, 9)
                exit_event = matching(dap, lambda item: item.get("event") == "exited")
            assert exit_event["body"]["exitCode"] == 0, exit_event
            matching(dap, lambda item: item.get("event") == "terminated")
            assert "debug-result: 22" in "".join(dap.output), (mode, dap.output)
    thread.join(10)
    assert not thread.is_alive() and not failures, failures


def main():
    with tempfile.TemporaryDirectory(dir=ROOT / ".toolchain/checkpoints") as folder:
        workspaces = [Path(folder) / "tasks" / f"session-{index}" / "workspace"
                      for index in range(10)]
        for workspace in workspaces:
            workspace.mkdir(parents=True)
        main_source = (
            "from debug_helper import compute\n"
            "\n"
            "BASE = 10\n"
            "\n"
            "def main():\n"
            "    left = 3\n"
            "    right = 4\n"
            "    answer = compute(left, right, BASE)\n"
            "    print(f'debug-result: {answer}')\n"
            "\n"
            "main()\n")
        helper_source = (
            "def compute(a, b, offset):\n"
            "    product = a * b\n"
            "    total = product + offset\n"
            "    return total\n")
        for workspace in workspaces:
            (workspace / "debug_main.py").write_text(main_source, encoding="utf-8")
            (workspace / "debug_helper.py").write_text(helper_source, encoding="utf-8")
        for index, workspace in enumerate(workspaces):
            mode = "continue" if index % 4 == 2 else "step_to_exit"
            run_session(workspace, mode)
    print("Ten consecutive debug sessions, stepping and continuing to exit: OK")


if __name__ == "__main__":
    main()
