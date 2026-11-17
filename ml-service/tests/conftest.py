import sys
from pathlib import Path

import pandas as pd
import pytest

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT))

from app import config  # noqa: E402
from app.service import SuitabilityService  # noqa: E402
from training.features import FEATURES  # noqa: E402

DATASET = ROOT.parent / "dataset" / "smartassign_p_and_c_7000.csv"


@pytest.fixture(scope="session")
def service() -> SuitabilityService:
    s = SuitabilityService.from_paths(config.MODEL_PATH, config.METADATA_PATH)
    assert s.model is not None, s.load_error
    return s


@pytest.fixture(scope="session")
def dataset() -> pd.DataFrame:
    return pd.read_csv(DATASET)


@pytest.fixture()
def features() -> dict:
    """A realistic eligible candidate, in the exact shape the Spring Boot backend sends."""
    return {
        "claim_type": "AUTO", "policy_type": "AUTO", "severity": "MEDIUM", "complexity": "MEDIUM",
        "estimated_loss": 185000.0, "claim_location": "Pune", "claim_age_days": 2, "incident_type": "COLLISION",
        "fraud_risk_score": 0.12, "injury_involved": 0, "third_party_involved": 1, "adjuster_expertise": "AUTO",
        "adjuster_experience_years": 9, "adjuster_current_workload": 3, "adjuster_max_workload": 12,
        "adjuster_availability": 1, "adjuster_location": "Pune", "adjuster_success_rate": 0.78,
        "adjuster_avg_resolution_days": 8.4, "similar_claims_handled": 60, "adjuster_active": 1,
        "distance_km": 0.8, "workload_ratio": 0.25, "expertise_match": 1,
    }


def row_features(row: pd.Series) -> dict:
    return {c: (row[c].item() if hasattr(row[c], "item") else row[c]) for c in FEATURES}
