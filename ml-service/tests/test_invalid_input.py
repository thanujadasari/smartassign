import pytest
from pydantic import ValidationError

from app.config import MAX_CANDIDATES
from app.schemas import PredictRequest


def _req(f, cid="1"):
    return {"candidates": [{"candidate_id": cid, "features": f}]}


@pytest.mark.parametrize("field,value", [
    ("severity", "EXTREME"),
    ("claim_type", "MARINE"),
    ("estimated_loss", -1),
    ("fraud_risk_score", 1.5),
    ("adjuster_max_workload", 0),
    ("injury_involved", 2),
    ("claim_location", ""),
    ("adjuster_success_rate", "high"),
])
def test_invalid_values_are_rejected(features, field, value):
    with pytest.raises(ValidationError):
        PredictRequest(**_req(dict(features, **{field: value})))


def test_missing_feature_is_rejected(features):
    f = dict(features)
    del f["distance_km"]
    with pytest.raises(ValidationError):
        PredictRequest(**_req(f))


@pytest.mark.parametrize("extra", ["claim_id", "adjuster_id", "assignment_delay_hours", "assignment_success"])
def test_identifiers_and_leakage_fields_are_rejected(features, extra):
    with pytest.raises(ValidationError):
        PredictRequest(**_req(dict(features, **{extra: 1})))


def test_empty_candidate_list_is_rejected():
    with pytest.raises(ValidationError):
        PredictRequest(candidates=[])


def test_duplicate_candidate_ids_are_rejected(features):
    with pytest.raises(ValidationError, match="unique"):
        PredictRequest(candidates=[{"candidate_id": "7", "features": features},
                                   {"candidate_id": "7", "features": features}])


def test_too_many_candidates_are_rejected(features):
    with pytest.raises(ValidationError):
        PredictRequest(candidates=[{"candidate_id": str(i), "features": features} for i in range(MAX_CANDIDATES + 1)])
