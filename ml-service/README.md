# SmartAssign suitability service (internal)

FastAPI service that scores **eligible** claim–handler candidates. Called only by the Spring Boot backend;
the browser never reaches it. Keep it on an internal network (`/ml/model-info` exposes internal metadata).

## Endpoints
| Method | Path | Purpose |
|---|---|---|
| POST | `/ml/predict` | `{"candidates":[{"candidate_id":"42","features":{…24 features…}}]}` → `{"status":"ok","model_version":"…","predictions":[{"candidate_id":"42","suitability":0.83}],"warnings":[]}` |
| GET | `/ml/health` | `{"status":"ok" or "degraded","model_loaded":true,"model_version":"…"}` |
| GET | `/ml/model-info` | internal metadata: features, selection rule, CV + hold-out metrics, dataset hash, versions |
| GET | `/ml/docs` | OpenAPI UI |

Errors: `422 {"status":"error","detail":[…]}` for invalid input (unknown category, out-of-range value, missing
feature, **any identifier or leakage field such as `claim_id` or `assignment_delay_hours`**, duplicate ids, more than 500
candidates). `503 {"status":"unavailable"}` when no valid model is loaded. The backend treats any non-200 response as
"predicted fit unavailable" and ranks on profile factors only.

`suitability` is `predict_proba[:,1]` of a class-balanced classifier: a **relative** fit score in [0, 1], not a calibrated
probability. Unseen city names are scored (the encoder ignores them) but reported in `warnings`.

## Train (offline, reproducible)
```bash
cd ml-service
pip install -r requirements-dev.txt
python -m training.train --data ../dataset/smartassign_p_and_c_7000.xlsx --out ../model-artifacts
```
Reproduces the original notebook (same features, exclusions, split, preprocessing and hyper-parameters), adds 5-fold
CV and reference baselines, selects a model by a fixed rule, refits it on all rows and writes
`model-artifacts/suitability_model.joblib`, `model_metadata.json` and `reports/training_report.md`.

## Run and test
```bash
uvicorn app.main:app --port 8000      # from ml-service/
python -m pytest                      # model loading, preprocessing, prediction, invalid input, API
```
