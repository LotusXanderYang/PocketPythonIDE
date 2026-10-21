"""File-based command bridge for the embedded CPython runtime."""

import json
import os
import traceback


def main(command_path, result_path):
    try:
        with open(command_path, encoding="utf-8") as source:
            command = json.load(source)
        mode = command.get("mode")
        if mode == "probe":
            from runtime_probe import probe
            result = probe(command["workdir"])
        elif mode == "run":
            from ide_runner import run_code
            result = run_code(command["source"], command["filename"],
                              command["workdir"])
        else:
            raise ValueError("Unknown runtime command")
        # The two Python entry points return JSON strings. Validate before
        # passing them to Java so a malformed result cannot look successful.
        json.loads(result)
    except BaseException:
        result = json.dumps({"ok": False, "error": traceback.format_exc()},
                            ensure_ascii=False)
    temporary = result_path + ".tmp"
    with open(temporary, "w", encoding="utf-8") as output:
        output.write(result)
        output.flush()
        os.fsync(output.fileno())
    os.replace(temporary, result_path)
