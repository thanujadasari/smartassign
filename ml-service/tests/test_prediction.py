import numpy as np
from sklearn.metrics import roc_auc_score

from app.schemas import PredictRequest
from tests.conftest import row_features


def _req(features_list):
    return PredictRequest(candidates=[{"candidate_id": f"h{i}", "features": f} for i, f in enumerate(features_list)])


def test_scores_are_probabilities_in_range_and_ids_preserved(service, features):
    res = service.predict(_req([features, dict(features, adjuster_current_workload=11, workload_ratio=0.917)]))
    assert [p.candidate_id for p in res.predictions] == ["h0", "h1"]
    assert all(0.0 <= p.suitability <= 1.0 for p in res.predictions)
    assert res.model_version == service.model.version


def test_predictions_are_deterministic(service, features):
    a = service.predict(_req([features])).predictions[0].suitability
    b = service.predict(_req([features])).predictions[0].suitability
    assert a == b


def test_same_city_candidate_scores_higher_than_distant_one(service, features):
    local = features
    far = dict(features, adjuster_location="Delhi", distance_km=1450.0)
    res = service.predict(_req([local, far]))
    assert res.predictions[0].suitability > res.predictions[1].suitability


def test_batch_and_single_scoring_agree(service, dataset):
    rows = [row_features(r) for _, r in dataset.head(25).iterrows()]
    batch = [p.suitability for p in service.predict(_req(rows)).predictions]
    single = [service.predict(_req([r])).predictions[0].suitability for r in rows]
    assert np.allclose(batch, single)


def test_scores_rank_dataset_outcomes_sanity_check(service, dataset):
    """In-sample sanity check only (the served model is fit on all rows) - NOT a performance claim."""
    sample = dataset.sample(800, random_state=1)
    rows = [row_features(r) for _, r in sample.iterrows()]
    scores = []
    for i in range(0, len(rows), 400):
        scores += [p.suitability for p in service.predict(_req(rows[i:i + 400])).predictions]
    assert roc_auc_score(sample["assignment_success"], scores) > 0.9
