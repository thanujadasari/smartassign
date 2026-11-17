import random

from app.schemas import Features, PredictRequest


def _req(features_list):
    return PredictRequest(candidates=[{"candidate_id": str(i), "features": f} for i, f in enumerate(features_list)])


def test_same_preprocessing_as_training_is_inside_the_artifact(service):
    steps = dict(service.model.pipeline.named_steps)
    assert "preprocessor" in steps and "model" in steps


def test_key_order_does_not_change_the_score(service, features):
    shuffled_keys = list(features)
    random.Random(7).shuffle(shuffled_keys)
    shuffled = {k: features[k] for k in shuffled_keys}
    a = service.predict(_req([features])).predictions[0].suitability
    b = service.predict(_req([shuffled])).predictions[0].suitability
    assert a == b


def test_booleans_are_accepted_as_flags(features):
    f = dict(features, injury_involved=True, adjuster_active=False)
    parsed = Features(**f)
    assert parsed.injury_involved == 1 and parsed.adjuster_active == 0


def test_unseen_city_is_scored_but_reported(service, features):
    res = service.predict(_req([dict(features, claim_location="Jaipur", adjuster_location="Jaipur")]))
    assert 0.0 <= res.predictions[0].suitability <= 1.0
    assert any("Jaipur" in w for w in res.warnings)


def test_known_values_produce_no_warnings(service, features):
    assert service.predict(_req([features])).warnings == []
