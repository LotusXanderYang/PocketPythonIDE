"""Execute one saved Lab project in its private project directory."""

import contextlib
import gc
import json
import os
from pathlib import Path
import sys
import time
import traceback


class ProgressLog:
    LIMIT = 1024 * 1024

    def __init__(self, path):
        self.path = path
        self.written = 0
        self.truncated = False

    def write(self, value):
        text = str(value)
        raw = text.encode("utf-8", errors="replace")
        left = self.LIMIT - self.written
        if left > 0:
            with open(self.path, "ab") as log:
                log.write(raw[:left])
                log.flush()
                os.fsync(log.fileno())
            self.written += min(len(raw), left)
        if len(raw) > left and not self.truncated:
            with open(self.path, "ab") as log:
                log.write("\n[日志达到 1 MiB 上限，后续内容已省略]\n".encode())
            self.truncated = True
        return len(text)

    def flush(self):
        return None


def _project_location(error, root):
    for frame in reversed(traceback.extract_tb(error.__traceback__)):
        try:
            relative = Path(frame.filename).resolve().relative_to(root)
            return str(relative).replace(os.sep, "/"), frame.lineno
        except ValueError:
            continue
    return "", 0


def main(command_path, result_path):
    answer = {"ok": False, "error": "", "line": 0, "file": "", "checkpoints": []}
    started = time.monotonic()
    project = None
    previous_cwd = os.getcwd()
    previous_argv = sys.argv
    previous_path = list(sys.path)
    try:
        with open(command_path, encoding="utf-8") as source:
            command = json.load(source)
        project = Path(command["project"]).resolve(strict=True)
        script = (project / "main.py").resolve(strict=True)
        script.relative_to(project)
        run_id = command["runId"]
        log = (project / "logs" / (run_id + ".log")).resolve()
        log.relative_to(project)
        log.parent.mkdir(exist_ok=True)
        if log.exists():
            raise FileExistsError("This run log already exists")
        log.touch(exist_ok=False)
        os.environ["POCKET_LAB_PROJECT"] = str(project)
        os.environ["POCKET_LAB_RUN_ID"] = run_id
        os.chdir(project)
        sys.argv = [str(script)]
        sys.path.insert(0, str(project))
        progress = ProgressLog(log)
        with contextlib.redirect_stdout(progress), contextlib.redirect_stderr(progress):
            print("Lab CPU task started", flush=True)
            try:
                with script.open(encoding="utf-8") as source:
                    program = source.read()
                exec(compile(program, str(script), "exec"),
                     {"__name__": "__main__", "__file__": str(script),
                      "__package__": None})
                answer["ok"] = True
                print("Lab CPU task completed", flush=True)
            except SystemExit as error:
                answer["ok"] = error.code in (None, 0)
                if not answer["ok"]:
                    answer["error"] = f"脚本退出，状态码 {error.code}"
                    print(answer["error"], flush=True)
            except BaseException as error:
                answer["error"] = str(error)
                answer["file"], answer["line"] = _project_location(error, project)
                traceback.print_exc()
        answer["checkpoints"] = sorted(
            str(path.relative_to(project)).replace(os.sep, "/")
            for path in (project / "checkpoints").glob("*" + run_id + ".pt"))
    except BaseException as error:
        answer["error"] = str(error)
        if project is not None:
            answer["file"], answer["line"] = _project_location(error, project)
        traceback.print_exc()
    finally:
        answer["pythonMs"] = int((time.monotonic() - started) * 1000)
        os.chdir(previous_cwd)
        sys.argv = previous_argv
        sys.path[:] = previous_path
        os.environ.pop("POCKET_LAB_PROJECT", None)
        os.environ.pop("POCKET_LAB_RUN_ID", None)
        gc.collect()
        temporary = result_path + ".tmp"
        with open(temporary, "w", encoding="utf-8") as output:
            json.dump(answer, output, ensure_ascii=False)
            output.flush()
            os.fsync(output.fileno())
        os.replace(temporary, result_path)
