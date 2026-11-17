# Model artifacts

No trained model files were supplied with the project (only the notebook PDF in `documentation/`), so they were
rebuilt with the reproducible pipeline in `ml-service/training/train.py`:

| File | Content |
|---|---|
| `suitability_model.joblib` | ONE scikit-learn Pipeline: the notebook's preprocessing (impute + one-hot) **and** the selected classifier, so inference always uses the training preprocessing |
| `model_metadata.json` | version, feature contract, exclusions, selection rule, CV + hold-out metrics of all four candidates, baselines, dataset sha256, library versions |

Regenerate: `cd ml-service && python -m training.train --data ../dataset/smartassign_p_and_c_7000.xlsx --out ../model-artifacts`.
The artifact must be loaded with the scikit-learn version recorded in the metadata (pinned in `ml-service/requirements.txt`).
Internal material: never shown in the supervisor interface.
