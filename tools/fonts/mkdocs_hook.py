"""Generate documentation font assets from the pinned DDS package before file discovery."""
from pathlib import Path
import subprocess

def on_pre_build(config, **kwargs):
    root = Path(__file__).resolve().parents[2]
    subprocess.run(["node", str(root / "tools/fonts/prepare.mjs")], cwd=root, check=True)
