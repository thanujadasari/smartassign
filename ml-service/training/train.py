"""
SmartAssign - offline, reproducible training and evaluation of the suitability classifier.

Reproduces the original notebook EXACTLY (same features, same exclusions, same 80/20 stratified split with
random_state=42, same preprocessors and hyper-parameters, balanced class weighting), then adds what the notebook
lacked before a model is chosen:
  * 5-fold stratified cross-validation on the training split (mean +/- std),
  * threshold-free ranking metrics (ROC-AUC, average precision) and a calibration metric (Brier score),
  * simple reference baselines (distance-only, same-city rule) to show how much the data depends on location.

Selection rule (documented, not hand-picked): highest mean CV ROC-AUC; ties within 0.002 are broken by
mean CV average precision. Ranking metrics are used because the service output is used to ORDER eligible
handlers, not to make a yes/no decision.

Usage (from ml-service/):  python -m training.train --data ../dataset/smartassign_p_and_c_7000.xlsx --out ../model-artifacts
Never run at request time.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import platform
from datetime import datetime, timezone
from pathlib import Path

import joblib
import numpy as np
import pandas as pd
import sklearn
from sklearn.compose import ColumnTransformer
from sklearn.ensemble import GradientBoostingClassifier, RandomForestClassifier
from sklearn.impute import SimpleImputer
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import (accuracy_score, average_precision_score, brier_score_loss, confusion_matrix,
                             f1_score, precision_score, recall_score, roc_auc_score)
from sklearn.model_selection import StratifiedKFold, train_test_split
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import OneHotEncoder, StandardScaler
from sklearn.tree import DecisionTreeClassifier
from sklearn.utils.class_weight import compute_sample_weight

from training.features import CATEGORICAL, EXCLUDED, FEATURES, NUMERICAL, TARGET

RANDOM_STATE = 42


def load(path: Path) -> pd.DataFrame:
    df = pd.read_excel(path) if path.suffix.lower() in (".xlsx", ".xls") else pd.read_csv(path)
    missing = [c for c in FEATURES + EXCLUDED + [TARGET] if c not in df.columns]
    if missing:
        raise ValueError(f"dataset is missing columns: {missing}")
    if set(df[TARGET].unique()) - {0, 1}:
        raise ValueError("target must be binary 0/1")
    return df


def preprocessor(scale: bool) -> ColumnTransformer:
    num_steps = [("imputer", SimpleImputer(strategy="median"))]
    if scale:
        num_steps.append(("scaler", StandardScaler()))
    return ColumnTransformer(transformers=[
        ("num", Pipeline(num_steps), NUMERICAL),
        ("cat", Pipeline([("imputer", SimpleImputer(strategy="most_frequent")),
                          ("onehot", OneHotEncoder(handle_unknown="ignore", sparse_output=False))]), CATEGORICAL),
    ])


def candidates() -> dict[str, tuple[Pipeline, bool]]:
    """(pipeline, needs_sample_weight) - hyper-parameters copied from the notebook."""
    return {
        "logistic_regression": (Pipeline([("preprocessor", preprocessor(True)), ("model", LogisticRegression(
            max_iter=2000, class_weight="balanced", random_state=RANDOM_STATE))]), False),
        "decision_tree": (Pipeline([("preprocessor", preprocessor(False)), ("model", DecisionTreeClassifier(
            max_depth=6, min_samples_leaf=10, class_weight="balanced", random_state=RANDOM_STATE))]), False),
        "random_forest": (Pipeline([("preprocessor", preprocessor(False)), ("model", RandomForestClassifier(
            n_estimators=300, max_depth=10, min_samples_leaf=3, class_weight="balanced", random_state=RANDOM_STATE,
            n_jobs=-1))]), False),
        "gradient_boosting": (Pipeline([("preprocessor", preprocessor(False)), ("model", GradientBoostingClassifier(
            n_estimators=150, learning_rate=0.05, max_depth=3, random_state=RANDOM_STATE))]), True),
    }


def fit(pipe: Pipeline, needs_weight: bool, X: pd.DataFrame, y: pd.Series) -> Pipeline:
    if needs_weight:
        pipe.fit(X, y, model__sample_weight=compute_sample_weight(class_weight="balanced", y=y))
    else:
        pipe.fit(X, y)
    return pipe


def metrics(y_true, prob, threshold: float = 0.5) -> dict:
    pred = (prob >= threshold).astype(int)
    tn, fp, fn, tp = confusion_matrix(y_true, pred, labels=[0, 1]).ravel()
    return {
        "accuracy": round(float(accuracy_score(y_true, pred)), 4),
        "precision": round(float(precision_score(y_true, pred, zero_division=0)), 4),
        "recall": round(float(recall_score(y_true, pred, zero_division=0)), 4),
        "f1": round(float(f1_score(y_true, pred, zero_division=0)), 4),
        "roc_auc": round(float(roc_auc_score(y_true, prob)), 4),
        "average_precision": round(float(average_precision_score(y_true, prob)), 4),
        "brier": round(float(brier_score_loss(y_true, prob)), 4),
        "confusion_matrix": {"tn": int(tn), "fp": int(fp), "fn": int(fn), "tp": int(tp)},
    }


def cross_validate(pipe_factory, needs_weight: bool, X: pd.DataFrame, y: pd.Series, folds: int = 5) -> dict:
    skf = StratifiedKFold(n_splits=folds, shuffle=True, random_state=RANDOM_STATE)
    scores = {"roc_auc": [], "average_precision": [], "f1": [], "brier": []}
    for tr, va in skf.split(X, y):
        pipe = fit(pipe_factory(), needs_weight, X.iloc[tr], y.iloc[tr])
        prob = pipe.predict_proba(X.iloc[va])[:, 1]
        m = metrics(y.iloc[va], prob)
        for k in scores:
            scores[k].append(m[k])
    return {k: {"mean": round(float(np.mean(v)), 4), "std": round(float(np.std(v)), 4)} for k, v in scores.items()}


def baselines(X_train, y_train, X_test, y_test) -> dict:
    same_city = (X_test["claim_location"] == X_test["adjuster_location"]).astype(float).to_numpy()
    dist_only = Pipeline([("s", StandardScaler()), ("m", LogisticRegression(class_weight="balanced"))])
    dist_only.fit(X_train[["distance_km"]], y_train)
    prob = dist_only.predict_proba(X_test[["distance_km"]])[:, 1]
    return {
        "same_city_rule": {"roc_auc": round(float(roc_auc_score(y_test, same_city)), 4),
                           "average_precision": round(float(average_precision_score(y_test, same_city)), 4)},
        "distance_only_logistic": {"roc_auc": round(float(roc_auc_score(y_test, prob)), 4),
                                   "average_precision": round(float(average_precision_score(y_test, prob)), 4)},
    }


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--data", required=True, type=Path)
    ap.add_argument("--out", required=True, type=Path)
    ap.add_argument("--reports", type=Path, default=Path("reports"))
    args = ap.parse_args()

    df = load(args.data)
    data_hash = hashlib.sha256(args.data.read_bytes()).hexdigest()
    X = df[FEATURES].copy()
    y = df[TARGET].astype(int)
    X_train, X_test, y_train, y_test = train_test_split(X, y, test_size=0.20, random_state=RANDOM_STATE, stratify=y)

    results = {}
    fitted = {}
    for name, (pipe, needs_weight) in candidates().items():
        print(f"[train] {name} ...", flush=True)
        factory = lambda n=name: candidates()[n][0]
        cv = cross_validate(factory, needs_weight, X_train, y_train)
        model = fit(pipe, needs_weight, X_train, y_train)
        test = metrics(y_test, model.predict_proba(X_test)[:, 1])
        results[name] = {"cv_5fold_train": cv, "holdout_test": test}
        fitted[name] = (model, needs_weight)
        print(f"        CV ROC-AUC {cv['roc_auc']['mean']:.4f}±{cv['roc_auc']['std']:.4f}  "
              f"AP {cv['average_precision']['mean']:.4f}  | test ROC-AUC {test['roc_auc']:.4f} F1 {test['f1']:.4f}", flush=True)

    ranked = sorted(results, key=lambda n: (-results[n]["cv_5fold_train"]["roc_auc"]["mean"],
                                            -results[n]["cv_5fold_train"]["average_precision"]["mean"]))
    best = ranked[0]
    top_auc = results[best]["cv_5fold_train"]["roc_auc"]["mean"]
    near = [n for n in ranked if top_auc - results[n]["cv_5fold_train"]["roc_auc"]["mean"] <= 0.002]
    if len(near) > 1:
        best = max(near, key=lambda n: results[n]["cv_5fold_train"]["average_precision"]["mean"])
    base = baselines(X_train, y_train, X_test, y_test)

    # final artifact: refit the selected configuration on the FULL dataset for serving
    final_pipe, needs_weight = candidates()[best]
    final = fit(final_pipe, needs_weight, X, y)

    trained_at = datetime.now(timezone.utc).replace(microsecond=0)
    version = f"suitability-{best.replace('_', '-')}-{trained_at:%Y%m%d}-{data_hash[:8]}"
    categories = {c: sorted(map(str, X[c].dropna().unique())) for c in CATEGORICAL}
    ranges = {c: {"min": float(X[c].min()), "max": float(X[c].max())} for c in NUMERICAL}
    metadata = {
        "model_version": version,
        "model_family": best,
        "task": "BINARY_CLASSIFICATION",
        "target": TARGET,
        "positive_class_meaning": "assignment succeeded (synthetic label)",
        "output": "predict_proba[:,1] of a class-balanced model - a RELATIVE suitability score, not a calibrated probability",
        "features": FEATURES,
        "categorical_features": CATEGORICAL,
        "numerical_features": NUMERICAL,
        "excluded_columns": EXCLUDED,
        "training_categories": categories,
        "training_numeric_ranges": ranges,
        "selection_rule": "highest mean 5-fold CV ROC-AUC on the training split; ties within 0.002 broken by CV average precision",
        "metrics": {"selected": best, "candidates": results, "reference_baselines": base,
                    "holdout": "stratified 20% split, random_state=42 (same as the original notebook)"},
        "final_fit": "selected configuration refit on all 7,000 rows",
        "dataset_sha256": data_hash,
        "dataset_rows": int(len(df)),
        "class_balance": {str(k): int(v) for k, v in y.value_counts().sort_index().items()},
        "trained_at": trained_at.isoformat(),
        "environment": {"python": platform.python_version(), "scikit_learn": sklearn.__version__,
                        "pandas": pd.__version__, "numpy": np.__version__, "joblib": joblib.__version__},
        "synthetic_data": True,
    }

    args.out.mkdir(parents=True, exist_ok=True)
    joblib.dump(final, args.out / "suitability_model.joblib", compress=3)
    (args.out / "model_metadata.json").write_text(json.dumps(metadata, indent=2))
    args.reports.mkdir(parents=True, exist_ok=True)
    (args.reports / "training_report.json").write_text(json.dumps(metadata["metrics"], indent=2))
    write_markdown(args.reports / "training_report.md", metadata, ranked)
    print(f"[train] selected {best} -> {version}")


def write_markdown(path: Path, md: dict, ranked: list[str]) -> None:
    m = md["metrics"]
    lines = ["# Suitability model - training report (INTERNAL, synthetic data)", "",
             f"Model version: `{md['model_version']}`  ", f"Selected: **{m['selected']}** - {md['selection_rule']}  ",
             f"Dataset sha256: `{md['dataset_sha256']}` ({md['dataset_rows']} rows, class balance {md['class_balance']})", "",
             "## 5-fold cross-validation on the training split (mean ± std)", "",
             "| Model | ROC-AUC | Avg precision | F1 @0.5 | Brier |", "|---|---:|---:|---:|---:|"]
    for n in ranked:
        cv = m["candidates"][n]["cv_5fold_train"]
        lines.append(f"| {n} | {cv['roc_auc']['mean']:.4f} ± {cv['roc_auc']['std']:.4f} | "
                     f"{cv['average_precision']['mean']:.4f} ± {cv['average_precision']['std']:.4f} | "
                     f"{cv['f1']['mean']:.4f} ± {cv['f1']['std']:.4f} | {cv['brier']['mean']:.4f} |")
    lines += ["", "## Hold-out test (20 %, same split as the original notebook)", "",
              "| Model | Accuracy | Precision | Recall | F1 | ROC-AUC | Avg precision | Brier | TN/FP/FN/TP |",
              "|---|---:|---:|---:|---:|---:|---:|---:|---|"]
    for n in ranked:
        t = m["candidates"][n]["holdout_test"]
        c = t["confusion_matrix"]
        lines.append(f"| {n} | {t['accuracy']:.4f} | {t['precision']:.4f} | {t['recall']:.4f} | {t['f1']:.4f} | "
                     f"{t['roc_auc']:.4f} | {t['average_precision']:.4f} | {t['brier']:.4f} | {c['tn']}/{c['fp']}/{c['fn']}/{c['tp']} |")
    b = m["reference_baselines"]
    lines += ["", "## Reference baselines (hold-out)", "",
              f"* Same-city rule (1 if claim and adjuster city match): ROC-AUC {b['same_city_rule']['roc_auc']:.4f}, "
              f"AP {b['same_city_rule']['average_precision']:.4f}",
              f"* Distance-only logistic regression: ROC-AUC {b['distance_only_logistic']['roc_auc']:.4f}, "
              f"AP {b['distance_only_logistic']['average_precision']:.4f}", "",
              "These show how much of the predictive signal is location. The labels are synthetic; none of these "
              "numbers is evidence of real-world performance. Scores come from class-balanced models and are not "
              "calibrated probabilities (see Brier). This report is internal and is never shown in the supervisor UI."]
    path.write_text("\n".join(lines) + "\n")


if __name__ == "__main__":
    main()
