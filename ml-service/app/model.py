"""Loads the trained pipeline (preprocessing + classifier in ONE artifact) and serves predictions."""
from __future__ import annotations

import json
import logging
import math
from dataclasses import dataclass, field
from pathlib import Path

import joblib
import numpy as np
import pandas as pd
import sklearn

from training.features import CATEGORICAL, FEATURES

log = logging.getLogger("smartassign.ml")


class ModelUnavailable(RuntimeError):
    """The model could not be loaded or produced invalid output."""


@dataclass
class SuitabilityModel:
    pipeline: object
    metadata: dict
    warnings: list[str] = field(default_factory=list)

    @property
    def version(self) -> str:
        return self.metadata["model_version"]

    @classmethod
    def load(cls, model_path: Path, metadata_path: Path) -> "SuitabilityModel":
        if not model_path.exists():
            raise ModelUnavailable(f"model artifact not found: {model_path.name}")
        if not metadata_path.exists():
            raise ModelUnavailable(f"model metadata not found: {metadata_path.name}")
        metadata = json.loads(metadata_path.read_text())
        if metadata.get("features") != FEATURES:
            raise ModelUnavailable("artifact feature list does not match the service contract")
        warnings = []
        trained_with = metadata.get("environment", {}).get("scikit_learn")
        if trained_with and trained_with != sklearn.__version__:
            # loading a pickle across scikit-learn versions is unsafe - refuse rather than return wrong scores
            raise ModelUnavailable(f"artifact was trained with scikit-learn {trained_with} but "
                                   f"{sklearn.__version__} is installed; retrain or pin the matching version")
        pipeline = joblib.load(model_path)
        if not hasattr(pipeline, "predict_proba"):
            raise ModelUnavailable("artifact does not support probability output")
        classes = list(getattr(pipeline, "classes_", []))
        if classes != [0, 1]:
            raise ModelUnavailable(f"unexpected classes in artifact: {classes}")
        log.info("Loaded suitability model %s", metadata["model_version"])
        return cls(pipeline=pipeline, metadata=metadata, warnings=warnings)

    def frame(self, rows: list[dict]) -> pd.DataFrame:
        """Builds the model input with the exact training column order and dtypes."""
        df = pd.DataFrame(rows, columns=FEATURES)
        for c in CATEGORICAL:
            df[c] = df[c].astype(str)
        for c in FEATURES:
            if c not in CATEGORICAL:
                df[c] = pd.to_numeric(df[c], errors="raise").astype(float)
        return df

    def unseen_categories(self, rows: list[dict]) -> list[str]:
        """Values never seen in training are ignored by the encoder; report them instead of hiding it."""
        known = self.metadata.get("training_categories", {})
        notes = set()
        for r in rows:
            for c in CATEGORICAL:
                if c in known and str(r[c]) not in known[c]:
                    notes.add(f"unseen value for {c}: {r[c]}")
        return sorted(notes)

    def predict(self, rows: list[dict]) -> list[float]:
        prob = self.pipeline.predict_proba(self.frame(rows))[:, 1]
        out = [float(p) for p in np.asarray(prob)]
        if any(not math.isfinite(p) or p < 0 or p > 1 for p in out):
            raise ModelUnavailable("model produced values outside [0, 1]")
        return out
