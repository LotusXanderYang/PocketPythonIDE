"""Execute one editor buffer with isolated globals and captured Python output."""

import contextlib
import io
import json
import os
import sys
import traceback


class _LimitedOutput(io.TextIOBase):
    LIMIT = 100_000

    def __init__(self):
        self.parts = []
        self.length = 0
        self.truncated = False

    def writable(self):
        return True

    def write(self, value):
        value = str(value)
        remaining = self.LIMIT - self.length
        if remaining > 0:
            chunk = value[:remaining]
            self.parts.append(chunk)
            self.length += len(chunk)
        if len(value) > remaining:
            self.truncated = True
        return len(value)

    def getvalue(self):
        result = "".join(self.parts)
        if self.truncated:
            result += "\n[输出已截断]\n"
        return result


def run_code(source, filename, workdir):
    """Return a JSON result so Java does not depend on Python exception types."""
    stdout = _LimitedOutput()
    stderr = _LimitedOutput()
    previous_dir = os.getcwd()
    previous_argv = sys.argv
    previous_path = list(sys.path)
    success = True

    try:
        os.makedirs(workdir, exist_ok=True)
        os.chdir(workdir)
        sys.argv = [filename]
        if workdir not in sys.path:
            sys.path.insert(0, workdir)
        script_dir = os.path.dirname(os.path.abspath(filename))
        if script_dir != workdir and script_dir not in sys.path:
            sys.path.insert(0, script_dir)
        namespace = {"__name__": "__main__", "__file__": filename,
                     "__package__": None, "__builtins__": __builtins__}
        with contextlib.redirect_stdout(stdout), contextlib.redirect_stderr(stderr):
            try:
                exec(compile(source, filename, "exec"), namespace)
            except BaseException:
                success = False
                traceback.print_exc()
    finally:
        os.chdir(previous_dir)
        sys.argv = previous_argv
        sys.path[:] = previous_path

    return json.dumps({"ok": success, "stdout": stdout.getvalue(),
                       "stderr": stderr.getvalue()}, ensure_ascii=False)

