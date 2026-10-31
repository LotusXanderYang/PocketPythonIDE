"""Host-side behavioral smoke test; Android native and SAF paths need device tests."""

import importlib.util
from pathlib import Path
import tempfile


ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location(
    "python_pack_bridge", ROOT / "packs/python/python_pack_bridge.py")
bridge = importlib.util.module_from_spec(spec)
spec.loader.exec_module(bridge)


with tempfile.TemporaryDirectory(dir=ROOT / ".toolchain") as directory:
    project = Path(directory)
    (project / "helper.py").write_text("def answer():\n    return 42\n", encoding="utf-8")
    (project / "main.py").write_text(
        "from helper import answer\n"
        "from pathlib import Path\n"
        "import sys\n"
        "print(input() + ':' + str(answer()) + ':' + sys.argv[1])\n"
        "Path('output.txt').write_text('saved', encoding='utf-8')\n",
        encoding="utf-8",
    )
    (project / ".pocket-python.json").write_text(
        '{"args":["course"],"stdin":"Ada\\n"}', encoding="utf-8")
    command = {"workspace": str(project), "entry": "main.py"}
    result = bridge.run(command)
    assert result["status"] == "ok", result
    assert result["stdout"] == "Ada:42:course\n", result
    assert (project / "output.txt").read_text(encoding="utf-8") == "saved"

    (project / "main.py").write_text("raise ValueError('probe')\n", encoding="utf-8")
    result = bridge.run(command)
    assert result["status"] == "error" and result["line"] == 1, result
    assert "ValueError: probe" in result["stderr"], result

    (project / "main.py").write_text("def broken(:\n", encoding="utf-8")
    result = bridge.check(command)
    assert result["status"] == "error" and result["line"] == 1, result

    (project / "main.py").write_text("pri", encoding="utf-8")
    result = bridge.complete({**command, "cursor": 3})
    assert "print" in result["stdout"].splitlines(), result

    (project / "main.py").write_text("import sys\nsys.exit(0)\n", encoding="utf-8")
    assert bridge.run(command)["status"] == "ok"
    (project / "main.py").write_text("import sys\nsys.exit(7)\n", encoding="utf-8")
    result = bridge.run(command)
    assert result["status"] == "error" and result["exitCode"] == 7, result

print("Python pack bridge host smoke: PASS")
