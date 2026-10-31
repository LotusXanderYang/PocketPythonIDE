"""Offline CPU matrix and autograd check. No dataset is required."""

import json
from pathlib import Path

import numpy as np
import torch

params = json.loads(Path("params.json").read_text(encoding="utf-8"))
torch.manual_seed(int(params["seed"]))

x = torch.tensor([[1.0, 2.0], [3.0, 4.0]], requires_grad=True)
y = x @ x
print("torch:", torch.__version__, "numpy:", np.__version__, flush=True)
print("matrix:", y.detach().tolist(), flush=True)
y.sum().backward()
print("grad:", x.grad.tolist(), flush=True)
print("numpy bridge:", float(np.array([2.0, 3.0]).sum()), flush=True)
