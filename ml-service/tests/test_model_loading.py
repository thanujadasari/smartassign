import json

import pytest
import sklearn

from app import config
from app.model import ModelUnavailable, SuitabilityModel
from app.service import SuitabilityService
from training.features import FEATURES


def test_artifact_loads_with_matching_metadata(service):
    md = service.model.metadata
    assert md["features"] == FEATURES
    assert md["task"] == "BINARY_CLASSIFICATION"
    assert md["target"] == "assignment_success"
    assert md["environment"]["scikit_learn"] == sklearn.__version__
    assert service.health().status == "ok"
    assert service.health().model_version == md["model_version"]


def test_identifier_and_leakage_columns_are_not_model_inputs(service):
    for col in ["claim_id", "adjuster_id", "assignment_delay_hours", "previous_reassignments", "assignment_success"]:
        assert col not in service.model.metadata["features"]


def test_missing_artifact_gives_degraded_health(tmp_path):
    s = SuitabilityService.from_paths(tmp_path / "nope.joblib", config.METADATA_PATH)
    h = s.health()
    assert h.status == "degraded" and h.model_loaded is False and "not found" in h.detail


def test_feature_contract_mismatch_is_refused(tmp_path):
    md = json.loads(config.METADATA_PATH.read_text())
    md["features"] = md["features"][:-1]
    bad = tmp_path / "meta.json"
    bad.write_text(json.dumps(md))
    with pytest.raises(ModelUnavailable, match="feature list"):
        SuitabilityModel.load(config.MODEL_PATH, bad)


def test_scikit_learn_version_mismatch_is_refused(tmp_path):
    md = json.loads(config.METADATA_PATH.read_text())
    md["environment"]["scikit_learn"] = "0.0.1"
    bad = tmp_path / "meta.json"
    bad.write_text(json.dumps(md))
    with pytest.raises(ModelUnavailable, match="scikit-learn"):
        SuitabilityModel.load(config.MODEL_PATH, bad)


def test_model_info_is_internal_metadata(service):
    info = service.model_info()
    assert info["model_version"] and info["features"] == FEATURES
    assert "selected" in info["metrics"]
