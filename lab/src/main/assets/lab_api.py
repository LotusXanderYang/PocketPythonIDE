"""Small, local helpers for Lab projects. No network or package installer."""

import json
import os
from pathlib import Path
import re


def _context():
    root = Path(os.environ["POCKET_LAB_PROJECT"]).resolve()
    run_id = os.environ["POCKET_LAB_RUN_ID"]
    if not re.fullmatch(r"[0-9a-f-]{36}", run_id):
        raise ValueError("Invalid Lab run ID")
    return root, run_id


def log_metric(epoch, loss):
    """Append a flushed metric and print it for the live progress panel."""
    root, run_id = _context()
    value = {"epoch": int(epoch), "loss": float(loss)}
    path = root / "metrics" / (run_id + ".jsonl")
    path.parent.mkdir(exist_ok=True)
    with path.open("a", encoding="utf-8") as output:
        output.write(json.dumps(value, ensure_ascii=False) + "\n")
        output.flush()
        os.fsync(output.fileno())
    print(f"epoch {value['epoch']}: loss={value['loss']:.6f}", flush=True)


def save_checkpoint(value, label="model"):
    """Write a unique PyTorch checkpoint through a synced temporary file."""
    import torch

    root, run_id = _context()
    if not re.fullmatch(r"[A-Za-z0-9_-]{1,32}", label):
        raise ValueError("Checkpoint label must be 1–32 ASCII letters, digits, _ or -")
    folder = root / "checkpoints"
    folder.mkdir(exist_ok=True)
    final = folder / f"{label}-{run_id}.pt"
    temporary = folder / f".{label}-{run_id}.pt.tmp"
    if final.exists() or temporary.exists():
        raise FileExistsError(f"Checkpoint already exists: {final.name}")
    try:
        with temporary.open("xb") as output:
            torch.save(value, output)
            output.flush()
            os.fsync(output.fileno())
        if final.exists():
            raise FileExistsError(f"Checkpoint already exists: {final.name}")
        os.replace(temporary, final)
        try:
            descriptor = os.open(folder, os.O_RDONLY | getattr(os, "O_DIRECTORY", 0))
            try:
                os.fsync(descriptor)
            finally:
                os.close(descriptor)
        except OSError:
            pass  # The file itself is synced; some providers reject directory fsync.
    finally:
        if temporary.exists():
            temporary.unlink()
    print(f"checkpoint: {final.relative_to(root)}", flush=True)
    return str(final)
