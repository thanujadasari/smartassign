"""Runtime settings from environment variables."""
import os
from pathlib import Path

_DEFAULT_DIR = Path(__file__).resolve().parent.parent.parent / "model-artifacts"

MODEL_PATH = Path(os.getenv("SMARTASSIGN_MODEL_PATH", str(_DEFAULT_DIR / "suitability_model.joblib")))
METADATA_PATH = Path(os.getenv("SMARTASSIGN_MODEL_METADATA_PATH", str(_DEFAULT_DIR / "model_metadata.json")))
MAX_CANDIDATES = int(os.getenv("SMARTASSIGN_ML_MAX_CANDIDATES", "500"))
