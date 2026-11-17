# Dataset and ML findings (Step 1–2)

All numbers below were computed from the supplied file `smartassign_p_and_c_7000.xlsx`
(reproduce with `python3 dataset/prepare_dataset.py`; facts are written to `dataset/derived/dataset_profile.json`).

## Structure
| Fact | Value |
|---|---|
| Rows × columns | 7,000 × 29 (single sheet, no missing values) |
| Row grain | **one claim paired with one adjuster** (7,000 unique `claim_id`, 150 unique `adjuster_id`) |
| Target | `assignment_success` (binary): 6,240 × 0, 760 × 1 → **10.9 % positive** (imbalanced) |
| Categorical | claim_type (4), policy_type (6), severity (4), complexity (3), claim_location (7 cities), incident_type (10), adjuster_expertise (5), adjuster_location (7) |
| Derived columns | `workload_ratio` = current / max workload (verified exactly); `expertise_match` = 1 when the adjuster's expertise covers the claim type |

## Issues found

1. **Adjuster attributes are not stable per `adjuster_id`.** The same id appears with up to 5 different
   expertise values, 7 locations, 20 experience values and 5 maximum workloads. `adjuster_id` is a label,
   not a consistent person. **Decision:** each handler record is that adjuster's *latest observed snapshot*
   (row with the highest `claim_id`), so every value is real data. Aggregating with the most frequent value was tried
   and rejected — it produced no Workers' Compensation or Multi-line handlers at all. Handler names are not in the
   dataset; deterministic placeholder names are generated and flagged `name_is_synthetic = true`.

2. **The target is dominated by location.** Same-city pairs succeed 66.0 % of the time vs 1.8 % otherwise;
   651 of the 760 successes are same-city. Within same-city pairs, expertise match raises success from 55 % to 84 %,
   and lower workload helps (74 % below half capacity vs 53 % above 80 %). A model trained on this data mostly learns
   "same city". That is not leakage (location is known before assignment), but it explains the high ROC-AUC.

3. **The target ignores hard constraints.** 431 of the 760 successes involve adjusters who would fail SmartAssign's
   mandatory rules (inactive, unavailable, at capacity or wrong expertise). An ML score can therefore be high for an
   ineligible handler — which is exactly why the backend only sends eligible handlers to the ML service and never lets a
   score override eligibility.

4. **Leakage check.** The notebook correctly excludes `claim_id`, `adjuster_id` (identifiers), `assignment_delay_hours`
   (known only after assignment) and `previous_reassignments`. `assignment_delay_hours` carries no signal anyway
   (success 13–17 % across all delay bands). No remaining feature is post-assignment information.

5. **`MULTI_P_AND_C` covers every claim type** (`expertise_match` = 1 on all 843 rows) — used as the expertise rule.

6. **Evaluation method in the notebook:** one stratified 80/20 split (`random_state=42`, test = 1,400 rows:
   1,248 negative / 152 positive), `class_weight="balanced"` (sample weights for Gradient Boosting), threshold 0.5.
   Consequences: precision is low (≈58–63 %) because balanced weighting pushes many negatives over 0.5, and the
   predicted probabilities are **not calibrated**. SmartAssign therefore uses the score only as a 0–1 *relative*
   "predicted claim fit" factor inside the explainable ranking — never as a probability shown to supervisors.
   A single split also gives no variance estimate; cross-validation is recommended before choosing a final model.

## Model artifacts
**No trained model files (`.joblib` / `.pkl`) were supplied** — only a PDF export of the training notebook
(`documentation/ml-training-notebook-export.pdf`). The reported metrics (LR, DT, RF, GB) are kept as internal
reference only and are never shown in the product. The ML service step will rebuild a reproducible training pipeline
from the notebook (same features, same preprocessing) and save the artifact plus its preprocessing pipeline together.
Linear Regression appears in the project brief but **not** in the notebook; it was not used, and a regression model is
not appropriate for the binary target.

## Feature contract used by the backend (24 features)
claim_type, policy_type, severity, complexity, estimated_loss, claim_location, claim_age_days, incident_type,
fraud_risk_score, injury_involved, third_party_involved, adjuster_expertise, adjuster_experience_years,
adjuster_current_workload, adjuster_max_workload, adjuster_availability, adjuster_location, adjuster_success_rate,
adjuster_avg_resolution_days, similar_claims_handled, adjuster_active, distance_km, workload_ratio, expertise_match.

`distance_km` at inference time comes from the city-pair median distances derived from the dataset
(`dataset/derived/city_distances.csv`).

## Step 2 result — rebuilt model (internal, synthetic data)
Full tables: `ml-service/reports/training_report.md`; machine-readable: `model-artifacts/model_metadata.json`.

**Reproduction.** Re-running the notebook's exact configuration reproduces its hold-out results for Logistic
Regression, Decision Tree and Gradient Boosting to four decimals. Random Forest differs slightly
(F1 0.7462 vs 0.7579 reported, ROC-AUC 0.9735 vs 0.9733), most likely because of the newer scikit-learn version.

**Selection.** 5-fold stratified CV on the training split, rule fixed in advance: highest mean ROC-AUC, near-ties
(≤ 0.002) broken by average precision. Gradient Boosting: CV ROC-AUC 0.9753 ± 0.0047, AP 0.8296 ± 0.0279;
Logistic Regression is within the tie margin (0.9740 ± 0.0041) but has lower AP (0.7964), so **Gradient Boosting**
is served. The difference between these two is small and within one standard deviation — either would be defensible.
The served artifact is the selected configuration refit on all 7,000 rows.

**How much is location?** On the same hold-out: a one-feature distance model reaches ROC-AUC 0.9674 (AP 0.6766) and
the plain "same city" rule 0.8980, against 0.9769 / 0.7662 for the selected model. Most of the signal is distance.
Because SmartAssign's ranking also has a separate *Location proximity* factor (15 %), location is effectively counted
twice with the default weights (proximity 15 % + predicted fit 20 %). That is a weighting decision for the project
team; the weights are configurable in Settings.

**Linear Regression** is not used: the target is binary and the notebook never trained it.

**Not claimed:** none of these numbers is evidence of real-world performance (labels are synthetic), scores are
uncalibrated relative fit values (Brier ≈ 0.05), and the dataset cannot measure ranking quality *within* one claim's
candidate list because each claim appears with only one adjuster.
