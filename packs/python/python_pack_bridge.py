"""Commands executed by the embedded CPython interpreter in the pack process."""

import ast
import builtins
import contextlib
import io
import json
import keyword
import os
import re
import sys
import traceback


class LimitedOutput(io.TextIOBase):
    LIMIT = 1_700_000

    def __init__(self):
        self.parts = []
        self.length = 0
        self.truncated = False

    def writable(self):
        return True

    def write(self, value):
        value = str(value)
        left = self.LIMIT - self.length
        if left > 0:
            fragment = value[:left]
            self.parts.append(fragment)
            self.length += len(fragment)
        if len(value) > left:
            self.truncated = True
        return len(value)

    def getvalue(self):
        value = "".join(self.parts)
        return value + ("\n[输出已截断]\n" if self.truncated else "")


def result(status="ok", code="", message="", stdout="", stderr="",
           exit_code=0, line=0, column=0):
    return {"status": status, "errorCode": code, "message": message,
            "stdout": stdout, "stderr": stderr, "exitCode": exit_code,
            "line": line, "column": column}


def location(error, workspace):
    if isinstance(error, SyntaxError):
        return error.lineno or 0, error.offset or 0
    frames = traceback.extract_tb(error.__traceback__)
    for frame in reversed(frames):
        if os.path.realpath(frame.filename).startswith(workspace + os.sep):
            return frame.lineno, 1
    return 0, 0


def configuration(workspace, entry):
    for path in (os.path.join(os.path.dirname(entry), ".pocket-python.json"),
                 os.path.join(workspace, ".pocket-python.json")):
        if os.path.isfile(path):
            with open(path, encoding="utf-8") as source:
                value = json.load(source)
            args = value.get("args", [])
            stdin = value.get("stdin", "")
            if not isinstance(args, list) or len(args) > 16 or any(
                    not isinstance(arg, str) or len(arg) > 1024 for arg in args):
                raise ValueError("Invalid .pocket-python.json args")
            if not isinstance(stdin, str) or len(stdin.encode("utf-8")) > 65536:
                raise ValueError("Invalid .pocket-python.json stdin")
            return args, stdin
    return [], ""


def run(command):
    workspace = os.path.realpath(command["workspace"])
    entry = os.path.realpath(os.path.join(workspace, command["entry"]))
    if not entry.startswith(workspace + os.sep) or not entry.endswith(".py"):
        return result("error", "INVALID_ENTRY", "入口文件不在项目内")
    with open(entry, encoding="utf-8") as source:
        code = source.read()
    args, input_text = configuration(workspace, entry)
    stdout, stderr = LimitedOutput(), LimitedOutput()
    previous_cwd, previous_argv = os.getcwd(), sys.argv
    previous_path, previous_stdin = list(sys.path), sys.stdin
    previous_modules = set(sys.modules)
    try:
        os.chdir(workspace)
        sys.argv = [entry] + args
        sys.stdin = io.StringIO(input_text)
        sys.path.insert(0, workspace)
        if os.path.dirname(entry) != workspace:
            sys.path.insert(0, os.path.dirname(entry))
        namespace = {"__name__": "__main__", "__file__": entry,
                     "__package__": None, "__builtins__": builtins}
        with contextlib.redirect_stdout(stdout), contextlib.redirect_stderr(stderr):
            try:
                exec(compile(code, entry, "exec"), namespace)
                return result(message="运行完成", stdout=stdout.getvalue(),
                              stderr=stderr.getvalue())
            except SystemExit as error:
                status = error.code if isinstance(error.code, int) else (1 if error.code else 0)
                if status == 0:
                    return result(message="运行完成", stdout=stdout.getvalue(),
                                  stderr=stderr.getvalue())
                if not isinstance(error.code, int):
                    print(error.code, file=sys.stderr)
                return result("error", "PYTHON_EXIT", "脚本退出，状态码 " + str(status),
                              stdout.getvalue(), stderr.getvalue(), status)
            except BaseException as error:
                line, column = location(error, workspace)
                traceback.print_exc()
                return result("error", "PYTHON_EXCEPTION", str(error),
                              stdout.getvalue(), stderr.getvalue(), 1, line, column)
    finally:
        os.chdir(previous_cwd)
        sys.argv, sys.path[:], sys.stdin = previous_argv, previous_path, previous_stdin
        for name in set(sys.modules) - previous_modules:
            module = sys.modules.get(name)
            path = getattr(module, "__file__", None)
            if isinstance(path, str) and os.path.realpath(path).startswith(workspace + os.sep):
                sys.modules.pop(name, None)


def check(command):
    workspace = os.path.realpath(command["workspace"])
    entry = os.path.realpath(os.path.join(workspace, command["entry"]))
    if not entry.startswith(workspace + os.sep):
        return result("error", "INVALID_ENTRY", "入口文件不在项目内")
    try:
        with open(entry, encoding="utf-8") as source:
            ast.parse(source.read(), filename=entry)
        return result(message="语法检查通过", stdout="语法检查通过\n")
    except SyntaxError as error:
        return result("error", "SYNTAX_ERROR", error.msg,
                      stderr=f"{command['entry']}:{error.lineno}:{error.offset}: {error.msg}\n",
                      exit_code=1, line=error.lineno or 0, column=error.offset or 0)


def complete(command):
    workspace = os.path.realpath(command["workspace"])
    entry = os.path.realpath(os.path.join(workspace, command["entry"]))
    if not entry.startswith(workspace + os.sep):
        return result("error", "INVALID_ENTRY", "入口文件不在项目内")
    with open(entry, encoding="utf-8") as source:
        code = source.read()
    cursor = min(int(command.get("cursor", len(code))), len(code))
    prefix = re.search(r"[A-Za-z_][A-Za-z_0-9]*$", code[:cursor])
    if prefix is None:
        return result(stdout="")
    needle = prefix.group()
    candidates = set(keyword.kwlist) | set(dir(builtins)) | set(sys.stdlib_module_names)
    candidates.update(re.findall(r"\b(?:def|class)\s+([A-Za-z_]\w*)", code))
    candidates.update(re.findall(r"\b([A-Za-z_]\w*)\s*=", code))
    candidates.update(re.findall(r"\bimport\s+([A-Za-z_]\w*)", code))
    scanned = 0
    for folder, dirs, files in os.walk(workspace):
        dirs[:] = [name for name in dirs if name not in
                   (".git", ".venv", "__pycache__", "build")]
        for name in files:
            if not name.endswith(".py"):
                continue
            candidates.add(name[:-3])
            path = os.path.join(folder, name)
            if path == entry or os.path.getsize(path) > 1024 * 1024:
                continue
            with open(path, encoding="utf-8", errors="replace") as other:
                text = other.read()
            candidates.update(re.findall(r"\b(?:def|class)\s+([A-Za-z_]\w*)", text))
            scanned += 1
            if scanned >= 100:
                break
        if scanned >= 100:
            break
    matches = sorted((item for item in candidates if item.startswith(needle)
                      and item != needle), key=lambda item: (item != "print", item))[:24]
    return result(stdout="\n".join(matches) + ("\n" if matches else ""))


def main(command_path, result_path):
    try:
        with open(command_path, encoding="utf-8") as source:
            command = json.load(source)
        operation = command["operation"]
        if operation == "initialize":
            import ssl
            import sqlite3
            answer = result(message="CPython ready", stdout="Python "
                            + sys.version.split()[0] + " · ssl " + ssl.OPENSSL_VERSION
                            + " · sqlite " + sqlite3.sqlite_version + "\n")
        elif operation == "run":
            answer = run(command)
        elif operation in ("analyze", "diagnostics"):
            answer = check(command)
        elif operation == "complete":
            answer = complete(command)
        else:
            answer = result("unsupported", "UNSUPPORTED", operation, exit_code=-1)
    except BaseException as error:
        answer = result("error", "PACK_ERROR", str(error),
                        stderr=traceback.format_exc(), exit_code=1)
    temporary = result_path + ".tmp"
    with open(temporary, "w", encoding="utf-8") as output:
        json.dump(answer, output, ensure_ascii=False)
        output.flush()
        os.fsync(output.fileno())
    os.replace(temporary, result_path)
