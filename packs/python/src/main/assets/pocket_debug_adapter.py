"""Small DAP adapter for one embedded CPython task on Android.

The DAP socket is a Binder-transferred Unix socket pair, never a network listener. Source
paths in requests and responses are workspace-relative, a documented host/pack
extension that avoids leaking the pack's private filesystem to another UID.
"""

import bdb
from collections.abc import Mapping
import contextlib
import importlib
import io
import json
import linecache
import os
from pathlib import Path
import select
import socket
import sys
import traceback


MAX_MESSAGE = 1024 * 1024
MAX_OUTPUT = 1024 * 1024


class DapConnection:
    def __init__(self, stream):
        self.stream = stream
        self.buffer = bytearray()
        self.sequence = 1

    def send(self, message):
        message["seq"] = self.sequence
        self.sequence += 1
        payload = json.dumps(message, ensure_ascii=False).encode("utf-8")
        if len(payload) > MAX_MESSAGE:
            raise ValueError("DAP response exceeds 1 MiB")
        self.stream.sendall(b"Content-Length: " + str(len(payload)).encode("ascii")
                            + b"\r\n\r\n" + payload)

    def response(self, request, body=None, success=True, message=""):
        answer = {"type": "response", "request_seq": request["seq"],
                  "command": request["command"], "success": success}
        if body is not None:
            answer["body"] = body
        if message:
            answer["message"] = message
        self.send(answer)

    def event(self, name, body=None):
        event = {"type": "event", "event": name}
        if body is not None:
            event["body"] = body
        self.send(event)

    def receive(self, timeout=None):
        self.stream.settimeout(timeout)
        while True:
            end = self.buffer.find(b"\r\n\r\n")
            if end >= 0:
                headers = self.buffer[:end].decode("ascii")
                lengths = [int(line.split(":", 1)[1].strip())
                           for line in headers.split("\r\n")
                           if line.lower().startswith("content-length:")]
                if len(lengths) != 1 or not 0 < lengths[0] <= MAX_MESSAGE:
                    raise ValueError("Invalid DAP Content-Length")
                finish = end + 4 + lengths[0]
                if len(self.buffer) >= finish:
                    raw = bytes(self.buffer[end + 4:finish])
                    del self.buffer[:finish]
                    return json.loads(raw.decode("utf-8"))
            if len(self.buffer) > MAX_MESSAGE + 4096:
                raise ValueError("DAP input exceeds 1 MiB")
            chunk = self.stream.recv(65536)
            if not chunk:
                raise EOFError("DAP peer disconnected")
            self.buffer.extend(chunk)


class DapOutput(io.TextIOBase):
    def __init__(self, connection, category):
        self.connection = connection
        self.category = category
        self.sent = 0

    def writable(self):
        return True

    def write(self, text):
        text = str(text)
        raw = text.encode("utf-8", "replace")
        left = MAX_OUTPUT - self.sent
        if left > 0:
            fragment = raw[:left].decode("utf-8", "replace")
            self.connection.event("output", {"category": self.category,
                                              "output": fragment})
            self.sent += min(left, len(raw))
        return len(text)

    def flush(self):
        return None


class Debugger(bdb.Bdb):
    def __init__(self, connection, workspace, entry):
        super().__init__()
        self.connection = connection
        self.workspace = Path(workspace).resolve(strict=True)
        self.entry = self.resolve(entry)
        self.frames = {}
        self.variables = {}
        self.next_reference = 1
        self.exception = None
        self.exception_breaks = False
        self.pause_requested = False
        self.first_line = True
        self.disconnected = False

    def set_continue(self):
        # bdb normally removes every trace hook when no breakpoints exist.
        # Keep line tracing so DAP pause/disconnect still work in that case.
        self._set_stopinfo(self.botframe, None, -1)

    def resolve(self, relative):
        if not isinstance(relative, str) or not relative or "\\" in relative:
            raise ValueError("Invalid debug source path")
        parts = relative.split("/")
        if any(part in ("", ".", "..") for part in parts):
            raise ValueError("Invalid debug source path")
        path = (self.workspace / relative).resolve(strict=True)
        path.relative_to(self.workspace)
        if not path.is_file() or path.suffix != ".py":
            raise ValueError("Debug source must be a project Python file")
        return path

    def relative(self, path):
        try:
            return str(Path(path).resolve().relative_to(self.workspace)).replace(os.sep, "/")
        except ValueError:
            return None

    def evict_workspace_modules(self):
        """Drop imports from this or earlier task mirrors in the persistent interpreter."""
        tasks_root = self.workspace.parent.parent if self.workspace.name == "workspace" else None
        for name, module in tuple(sys.modules.items()):
            filename = getattr(module, "__file__", None)
            if not isinstance(filename, str):
                continue
            try:
                path = Path(filename).resolve()
                if path.is_relative_to(self.workspace):
                    sys.modules.pop(name, None)
                elif tasks_root is not None and path.is_relative_to(tasks_root):
                    relative = path.relative_to(tasks_root)
                    if len(relative.parts) >= 3 and relative.parts[1] == "workspace":
                        sys.modules.pop(name, None)
            except (OSError, RuntimeError, ValueError):
                continue
        importlib.invalidate_caches()

    def _reference(self, value):
        reference = self.next_reference
        self.next_reference += 1
        self.variables[reference] = value
        return reference

    def _stack(self, frame):
        self.frames = {}
        self.variables = {}
        self.next_reference = 1
        stack = []
        while frame is not None and len(stack) < 40:
            relative = self.relative(frame.f_code.co_filename)
            if relative is not None:
                frame_id = self._reference(frame)
                self.frames[frame_id] = frame
                stack.append({"id": frame_id, "name": frame.f_code.co_name,
                              "source": {"name": Path(relative).name,
                                         "path": relative},
                              "line": frame.f_lineno, "column": 1})
            frame = frame.f_back
        return stack

    def stop(self, frame, reason, description=""):
        stack = self._stack(frame)
        if not stack:
            return
        body = {"reason": reason, "threadId": 1, "allThreadsStopped": True}
        if description:
            body["description"] = description
        self.connection.event("stopped", body)
        while True:
            try:
                # A person may inspect a paused frame for longer than two minutes.
                # The socket closing already tells us when the IDE goes away.
                request = self.connection.receive(None)
            except (EOFError, OSError):
                self.disconnected = True
                self.set_quit()
                raise bdb.BdbQuit()
            if request.get("type") != "request":
                continue
            command = request.get("command")
            args = request.get("arguments") or {}
            if command == "threads":
                self.connection.response(request, {"threads": [{"id": 1, "name": "Python 主线程"}]})
            elif command == "stackTrace":
                self.connection.response(request, {"stackFrames": stack,
                                                   "totalFrames": len(stack)})
            elif command == "scopes":
                selected = self.frames.get(args.get("frameId"))
                if selected is None:
                    self.connection.response(request, success=False, message="Unknown frame")
                    continue
                scopes = [{"name": "局部变量", "variablesReference":
                           self._reference(selected.f_locals), "expensive": False},
                          {"name": "全局变量", "variablesReference":
                           self._reference(selected.f_globals), "expensive": True}]
                self.connection.response(request, {"scopes": scopes})
            elif command == "variables":
                value = self.variables.get(args.get("variablesReference"))
                if value is None:
                    self.connection.response(request, {"variables": []})
                    continue
                items = value.items() if isinstance(value, Mapping) else enumerate(value)
                variables = []
                for key, item in items:
                    if len(variables) >= 100:
                        break
                    try:
                        shown = repr(item)
                    except BaseException:
                        shown = "<repr failed>"
                    variables.append({"name": str(key)[:100], "value": shown[:256],
                                      "type": type(item).__name__, "variablesReference":
                                      self._reference(item) if isinstance(item, (Mapping, list, tuple))
                                      and len(item) else 0})
                self.connection.response(request, {"variables": variables})
            elif command == "exceptionInfo":
                self.connection.response(request, self.exception or
                                         {"exceptionId": "", "breakMode": "never"})
            elif command == "pause":
                self.connection.response(request, {})
            elif command in ("continue", "next", "stepIn", "stepOut"):
                self.connection.response(request, {"allThreadsContinued": True}
                                         if command == "continue" else {})
                self.connection.event("continued", {"threadId": 1,
                                                     "allThreadsContinued": True})
                if command == "continue":
                    self.set_continue()
                elif command == "next":
                    self.set_next(frame)
                elif command == "stepIn":
                    self.set_step()
                else:
                    self.set_return(frame)
                return
            elif command in ("disconnect", "terminate"):
                self.connection.response(request, {})
                self.disconnected = True
                self.set_quit()
                raise bdb.BdbQuit()
            else:
                self.connection.response(request, success=False,
                                         message="Unsupported while stopped: " + str(command))

    def trace_dispatch(self, frame, event, arg):
        if event == "line" and self.relative(frame.f_code.co_filename) is not None:
            if self.pause_requested:
                self.pause_requested = False
                self.stop(frame, "pause")
                return self.trace_dispatch
            if not self.first_line:
                self.poll_running()
        if event == "exception" and self.exception_breaks and self.relative(
                frame.f_code.co_filename) is not None:
            kind, value, _ = arg
            self.exception = {"exceptionId": kind.__name__, "description": str(value),
                              "breakMode": "always"}
            self.stop(frame, "exception", kind.__name__ + ": " + str(value))
            return self.trace_dispatch
        return super().trace_dispatch(frame, event, arg)

    def user_line(self, frame):
        if self.relative(frame.f_code.co_filename) is None:
            self.set_continue()
            return
        if self.first_line:
            self.first_line = False
            if frame.f_lineno not in self.breaks.get(self.canonic(frame.f_code.co_filename), []):
                self.set_continue()
                return
        line_breaks = self.breaks.get(self.canonic(frame.f_code.co_filename), [])
        self.stop(frame, "breakpoint" if frame.f_lineno in line_breaks else "step")

    def poll_running(self):
        readable, _, _ = select.select([self.connection.stream], [], [], 0)
        if not readable:
            return
        request = self.connection.receive(0.01)
        if request.get("type") != "request":
            return
        command = request.get("command")
        if command == "pause":
            self.pause_requested = True
            self.connection.response(request, {})
        elif command in ("disconnect", "terminate"):
            self.connection.response(request, {})
            self.disconnected = True
            self.set_quit()
            raise bdb.BdbQuit()
        else:
            self.connection.response(request, success=False,
                                     message="Debuggee is running")

    def configure(self):
        launched = False
        while True:
            request = self.connection.receive(30)
            if request.get("type") != "request":
                continue
            command = request.get("command")
            args = request.get("arguments") or {}
            if command == "initialize":
                self.connection.response(request, {"supportsConfigurationDoneRequest": True,
                    "supportsTerminateRequest": True, "supportsExceptionInfoRequest": True,
                    "exceptionBreakpointFilters": [{"filter": "all", "label": "所有异常"}]})
                self.connection.event("initialized")
            elif command == "launch":
                if args.get("program") != str(self.entry.relative_to(self.workspace)):
                    self.connection.response(request, success=False, message="Entry mismatch")
                    continue
                launched = True
                self.connection.response(request, {})
            elif command == "setBreakpoints":
                source = args.get("source") or {}
                try:
                    path = self.resolve(source.get("path"))
                    self.clear_all_file_breaks(str(path))
                    response = []
                    for item in args.get("breakpoints", [])[:100]:
                        line = int(item["line"])
                        error = self.set_break(str(path), line)
                        response.append({"verified": error is None, "line": line,
                                         "message": error or "",
                                         "source": {"name": path.name,
                                                    "path": str(path.relative_to(self.workspace))}})
                    self.connection.response(request, {"breakpoints": response})
                except (ValueError, OSError, KeyError) as error:
                    self.connection.response(request, success=False, message=str(error))
            elif command == "setExceptionBreakpoints":
                self.exception_breaks = "all" in args.get("filters", [])
                self.connection.response(request, {"breakpoints": []})
            elif command == "configurationDone":
                if not launched:
                    self.connection.response(request, success=False, message="Launch first")
                    continue
                self.connection.response(request, {})
                return
            elif command in ("disconnect", "terminate"):
                self.connection.response(request, {})
                self.disconnected = True
                raise bdb.BdbQuit()
            else:
                self.connection.response(request, success=False,
                                         message="Unsupported setup command: " + str(command))

    def run_program(self):
        old_cwd, old_argv, old_path = os.getcwd(), sys.argv, list(sys.path)
        output = DapOutput(self.connection, "stdout")
        errors = DapOutput(self.connection, "stderr")
        exit_code = 0
        try:
            self.evict_workspace_modules()
            os.chdir(self.workspace)
            sys.argv = [str(self.entry)]
            sys.path.insert(0, str(self.workspace))
            if self.entry.parent != self.workspace:
                sys.path.insert(0, str(self.entry.parent))
            with self.entry.open(encoding="utf-8") as source:
                code = compile(source.read(), str(self.entry), "exec")
            namespace = {"__name__": "__main__", "__file__": str(self.entry),
                         "__package__": None}
            with contextlib.redirect_stdout(output), contextlib.redirect_stderr(errors):
                try:
                    self.runctx(code, namespace, namespace)
                except bdb.BdbQuit:
                    exit_code = 130
                except SystemExit as error:
                    exit_code = error.code if isinstance(error.code, int) else 1
                except BaseException:
                    exit_code = 1
                    traceback.print_exc()
        finally:
            self.evict_workspace_modules()
            linecache.clearcache()
            os.chdir(old_cwd)
            sys.argv = old_argv
            sys.path[:] = old_path
        if not self.disconnected:
            self.connection.event("exited", {"exitCode": exit_code})
            self.connection.event("terminated")
        return exit_code


def main(command_path, result_path, channel_fd, ready_path):
    answer = {"status": "error", "message": "Debug session did not start"}
    try:
        with open(command_path, encoding="utf-8") as source:
            command = json.load(source)
        workspace = Path(command["workspace"]).resolve(strict=True)
        with socket.fromfd(channel_fd, socket.AF_UNIX, socket.SOCK_STREAM) as stream:
            with open(ready_path, "x", encoding="ascii") as ready:
                ready.write("ready\n")
                ready.flush()
                os.fsync(ready.fileno())
            connection = DapConnection(stream)
            debugger = Debugger(connection, workspace, command["entry"])
            debugger.configure()
            code = debugger.run_program()
            answer = {"status": "ok" if code == 0 else "error",
                      "message": "Debug session finished", "exitCode": code}
    except BaseException as error:
        answer = {"status": "error", "message": str(error),
                  "traceback": traceback.format_exc()[-8192:]}
    finally:
        temporary = result_path + ".tmp"
        with open(temporary, "w", encoding="utf-8") as output:
            json.dump(answer, output, ensure_ascii=False)
            output.flush()
            os.fsync(output.fileno())
        os.replace(temporary, result_path)
