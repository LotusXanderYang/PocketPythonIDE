"""Small on-device smoke test for the embedded CPython and native extensions."""

import json
import os
import platform
import sys
import traceback


def probe(workdir=None):
    result = {"python": sys.version.split()[0], "machine": platform.machine()}
    try:
        import numpy as np
        import torch

        result["numpy"] = np.__version__
        result["torch"] = torch.__version__
        left = torch.tensor([[1.0, 2.0], [3.0, 4.0]], requires_grad=True)
        right = left @ left
        right.sum().backward()
        result["matrix_multiply"] = right.detach().tolist()
        result["autograd"] = left.grad.tolist()
        result["numpy_bridge"] = torch.from_numpy(np.array([2.0, 3.0])).sum().item()
        if workdir is not None:
            os.makedirs(workdir, exist_ok=True)
            path = os.path.join(workdir, ".pocketpy-probe")
            try:
                with open(path, "w", encoding="utf-8") as output:
                    output.write("Python 3.14 / PyTorch 2.14.1")
                with open(path, encoding="utf-8") as source:
                    if source.read() != "Python 3.14 / PyTorch 2.14.1":
                        raise RuntimeError("File round-trip mismatch")
                result["file_io"] = "通过"
            finally:
                if os.path.exists(path):
                    os.remove(path)
        result["ok"] = True
    except BaseException:
        result["ok"] = False
        result["error"] = traceback.format_exc()
    return json.dumps(result, ensure_ascii=False)
