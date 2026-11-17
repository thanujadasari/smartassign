"""Framework-independent request handling, shared by the FastAPI app and the tests."""
from __future__ import annotations

from app.model import ModelUnavailable, SuitabilityModel
from app.schemas import HealthResponse, PredictRequest, PredictResponse, Prediction


class SuitabilityService:

    def __init__(self, model: SuitabilityModel | None, load_error: str | None = None):
        self.model = model
        self.load_error = load_error

    def health(self) -> HealthResponse:
        if self.model is None:
            return HealthResponse(status="degraded", model_loaded=False, detail=self.load_error)
        return HealthResponse(status="ok", model_loaded=True, model_version=self.model.version)

    def predict(self, req: PredictRequest) -> PredictResponse:
        if self.model is None:
            raise ModelUnavailable(self.load_error or "model not loaded")
        rows = [c.features.model_dump() for c in req.candidates]
        scores = self.model.predict(rows)
        return PredictResponse(
            model_version=self.model.version,
            predictions=[Prediction(candidate_id=c.candidate_id, suitability=round(s, 6))
                         for c, s in zip(req.candidates, scores)],
            warnings=self.model.unseen_categories(rows),
        )

    def model_info(self) -> dict:
        if self.model is None:
            raise ModelUnavailable(self.load_error or "model not loaded")
        md = self.model.metadata
        keys = ["model_version", "model_family", "task", "target", "output", "features", "excluded_columns",
                "selection_rule", "metrics", "trained_at", "dataset_sha256", "dataset_rows", "class_balance",
                "environment", "synthetic_data"]
        return {k: md.get(k) for k in keys}

    @classmethod
    def from_paths(cls, model_path, metadata_path) -> "SuitabilityService":
        try:
            return cls(SuitabilityModel.load(model_path, metadata_path))
        except (ModelUnavailable, OSError, ValueError) as e:
            return cls(None, str(e))
