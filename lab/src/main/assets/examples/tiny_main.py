"""Deterministic, tiny CPU linear regression with an optional imported CSV."""

import csv
import json
from pathlib import Path
import time

import torch

from lab_api import log_metric, save_checkpoint

params = json.loads(Path("params.json").read_text(encoding="utf-8"))
seed = int(params.get("seed", 7))
epochs = int(params.get("epochs", 80))
learning_rate = float(params.get("learning_rate", 0.15))
delay = int(params.get("step_delay_ms", 200))
if not 1 <= epochs <= 500 or not 0 < learning_rate <= 1 or not 0 <= delay <= 1000:
    raise ValueError("参数范围：epochs 1–500、learning_rate (0,1]、step_delay_ms 0–1000")
torch.manual_seed(seed)

data_file = str(params.get("data_file", ""))
if data_file:
    data_path = (Path.cwd() / data_file).resolve(strict=True)
    data_path.relative_to(Path.cwd().resolve())
    if not str(data_path.relative_to(Path.cwd())).startswith("data/"):
        raise ValueError("data_file 必须位于项目 data/ 目录")
    with data_path.open(newline="", encoding="utf-8") as source:
        rows = list(csv.DictReader(source))
    if not 2 <= len(rows) <= 256:
        raise ValueError("CSV 需要 2–256 行，列名为 x,y")
    xs = [float(row["x"]) for row in rows]
    ys = [float(row["y"]) for row in rows]
    print("dataset:", data_file, "rows:", len(rows), flush=True)
else:
    xs = [-1.0, -0.5, 0.0, 0.5, 1.0]
    ys = [2.0 * value + 1.0 for value in xs]
    print("dataset: built-in deterministic 5 rows", flush=True)

features = torch.tensor(xs, dtype=torch.float32).reshape(-1, 1)
targets = torch.tensor(ys, dtype=torch.float32).reshape(-1, 1)
model = torch.nn.Linear(1, 1)
optimizer = torch.optim.SGD(model.parameters(), lr=learning_rate)
for epoch in range(1, epochs + 1):
    optimizer.zero_grad()
    loss = torch.nn.functional.mse_loss(model(features), targets)
    loss.backward()
    optimizer.step()
    if epoch == 1 or epoch % 10 == 0 or epoch == epochs:
        log_metric(epoch, loss.item())
    if delay:
        time.sleep(delay / 1000)

checkpoint = save_checkpoint(model.state_dict(), "tiny-linear")
loaded = torch.load(checkpoint, map_location="cpu", weights_only=True)
restored = torch.nn.Linear(1, 1)
restored.load_state_dict(loaded)
print("reloaded prediction x=0:", float(restored(torch.tensor([[0.0]])).item()), flush=True)
