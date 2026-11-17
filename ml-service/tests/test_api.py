"""HTTP-level tests of the FastAPI app. Skipped automatically if fastapi/httpx are not installed."""
import pytest

pytest.importorskip("fastapi")
pytest.importorskip("httpx")

from fastapi.testclient import TestClient  # noqa: E402

from app import config  # noqa: E402
from app.main import app  # noqa: E402


@pytest.fixture()
def client():
    with TestClient(app) as c:
        yield c


def test_health(client):
    r = client.get("/ml/health")
    assert r.status_code == 200 and r.json()["model_loaded"] is True


def test_predict_contract(client, features):
    r = client.post("/ml/predict", json={"candidates": [{"candidate_id": "42", "features": features}]})
    assert r.status_code == 200
    body = r.json()
    assert body["status"] == "ok" and body["model_version"]
    assert body["predictions"][0]["candidate_id"] == "42"
    assert 0 <= body["predictions"][0]["suitability"] <= 1


def test_invalid_input_returns_422_error_shape(client, features):
    r = client.post("/ml/predict", json={"candidates": [{"candidate_id": "1", "features": dict(features, severity="X")}]})
    assert r.status_code == 422 and r.json()["status"] == "error"


def test_model_info(client):
    r = client.get("/ml/model-info")
    assert r.status_code == 200 and r.json()["model_version"]


def test_missing_model_returns_503(monkeypatch, tmp_path, features):
    monkeypatch.setattr(config, "MODEL_PATH", tmp_path / "missing.joblib")
    with TestClient(app) as c:
        assert c.get("/ml/health").json()["status"] == "degraded"
        r = c.post("/ml/predict", json={"candidates": [{"candidate_id": "1", "features": features}]})
        assert r.status_code == 503 and r.json()["status"] == "unavailable"
