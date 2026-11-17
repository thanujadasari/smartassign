# Suitability model - training report (INTERNAL, synthetic data)

Model version: `suitability-gradient-boosting-20261104-469bc489`  
Selected: **gradient_boosting** - highest mean 5-fold CV ROC-AUC on the training split; ties within 0.002 broken by CV average precision  
Dataset sha256: `469bc4892455d5cd76941e04472d4d6b839f2fa70adbb3cd6b254a249c705737` (7000 rows, class balance {'0': 6240, '1': 760})

## 5-fold cross-validation on the training split (mean ± std)

| Model | ROC-AUC | Avg precision | F1 @0.5 | Brier |
|---|---:|---:|---:|---:|
| gradient_boosting | 0.9753 ± 0.0047 | 0.8296 ± 0.0279 | 0.7491 ± 0.0162 | 0.0531 |
| logistic_regression | 0.9740 ± 0.0041 | 0.7964 ± 0.0425 | 0.7260 ± 0.0051 | 0.0601 |
| random_forest | 0.9711 ± 0.0039 | 0.7869 ± 0.0266 | 0.7534 ± 0.0125 | 0.0574 |
| decision_tree | 0.9525 ± 0.0080 | 0.7505 ± 0.0342 | 0.7294 ± 0.0083 | 0.0617 |

## Hold-out test (20 %, same split as the original notebook)

| Model | Accuracy | Precision | Recall | F1 | ROC-AUC | Avg precision | Brier | TN/FP/FN/TP |
|---|---:|---:|---:|---:|---:|---:|---:|---|
| gradient_boosting | 0.9293 | 0.6100 | 0.9671 | 0.7481 | 0.9769 | 0.7662 | 0.0566 | 1154/94/5/147 |
| logistic_regression | 0.9200 | 0.5763 | 0.9934 | 0.7295 | 0.9749 | 0.7478 | 0.0619 | 1137/111/1/151 |
| random_forest | 0.9286 | 0.6074 | 0.9671 | 0.7462 | 0.9735 | 0.7175 | 0.0584 | 1153/95/5/147 |
| decision_tree | 0.9207 | 0.5858 | 0.9211 | 0.7161 | 0.9637 | 0.7204 | 0.0637 | 1149/99/12/140 |

## Reference baselines (hold-out)

* Same-city rule (1 if claim and adjuster city match): ROC-AUC 0.8980, AP 0.5607
* Distance-only logistic regression: ROC-AUC 0.9674, AP 0.6766

These show how much of the predictive signal is location. The labels are synthetic; none of these numbers is evidence of real-world performance. Scores come from class-balanced models and are not calibrated probabilities (see Brier). This report is internal and is never shown in the supervisor UI.
