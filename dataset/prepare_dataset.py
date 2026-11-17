"""
SmartAssign - dataset preparation (reproducible, offline).

Reads the supplied synthetic P&C dataset (7,000 claim-adjuster pairings, 29 columns)
and produces:

  dataset/smartassign_p_and_c_7000.csv          - lossless CSV copy of the workbook
  dataset/derived/handlers.csv                  - one profile per adjuster_id (150)
  dataset/derived/city_distances.csv            - median distance per city pair
  dataset/derived/dataset_profile.json          - schema / distribution facts
  backend/src/main/resources/db/seed/R__reference_city_distances.sql
                                                - idempotent Flyway seed: city distances ONLY

WHAT the dataset is used for
----------------------------
The dataset trains the suitability model (ml-service) and provides the city-to-city
distance reference table used for proximity. NO claim, handler or user from the
dataset is loaded into the application database: every claim in the app is
registered by a customer and every handler signs up through the staff portal.
handlers.csv below is an analysis/training artefact only.

WHY handler profiles are derived
--------------------------------
In the supplied file the adjuster attributes are NOT stable per adjuster_id: the same
id appears with up to 5 expertise values, 7 locations and 20 experience values.
adjuster_id is therefore a label, not a consistent person. To obtain one handler
record per id we take that adjuster's LATEST OBSERVED SNAPSHOT: the row with the
highest claim_id. Every handler value is therefore a real value from the file.

(Aggregating with the most-frequent value was tried first and rejected: it collapsed
the population to AUTO/PROPERTY/LIABILITY only, all available and active, which would
leave Workers' Compensation claims with no eligible handler at all.)

Handler display names are NOT in the dataset. Deterministic placeholder names are
generated and flagged as synthetic (name_is_synthetic = true).

Usage:  python3 dataset/prepare_dataset.py   (from the project root)
"""
from __future__ import annotations

import json
from pathlib import Path

import pandas as pd

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "dataset" / "smartassign_p_and_c_7000.xlsx"
OUT_CSV = ROOT / "dataset" / "smartassign_p_and_c_7000.csv"
DERIVED = ROOT / "dataset" / "derived"
SEED_SQL = ROOT / "backend" / "src" / "main" / "resources" / "db" / "seed" / "R__reference_city_distances.sql"

FIRST = ["Aarav", "Priya", "Rohan", "Ananya", "Vikram", "Meera", "Arjun", "Kavya", "Siddharth", "Ishita",
         "Rahul", "Neha", "Karan", "Pooja", "Aditya", "Sneha", "Nikhil", "Divya", "Manish", "Riya",
         "Varun", "Shreya", "Amit", "Tanvi", "Harsh", "Anjali", "Gaurav", "Nisha", "Rajat", "Swati"]
LAST = ["Sharma", "Iyer", "Patel", "Reddy", "Mehta", "Nair", "Gupta", "Rao", "Das", "Kulkarni",
        "Joshi", "Menon", "Singh", "Banerjee", "Pillai"]

CLAIM_TYPES = ["AUTO", "PROPERTY", "LIABILITY", "WORKERS_COMPENSATION"]


def sql_str(v) -> str:
    return "'" + str(v).replace("'", "''") + "'"


def main() -> None:
    df = pd.read_excel(SRC)
    assert df.shape == (7000, 29), f"unexpected shape {df.shape}"
    assert df["claim_id"].is_unique, "claim_id must be unique"
    df.to_csv(OUT_CSV, index=False)
    DERIVED.mkdir(parents=True, exist_ok=True)

    # ---------- handler profiles ----------
    rows = []
    for i, (hid, g) in enumerate(sorted(df.groupby("adjuster_id"), key=lambda t: t[0])):
        r = g.sort_values("claim_id").iloc[-1]          # latest observed snapshot
        rows.append({
            "handler_code": hid,
            "full_name": f"{FIRST[i % len(FIRST)]} {LAST[(i * 7) % len(LAST)]}",
            "name_is_synthetic": True,
            "primary_expertise": r.adjuster_expertise,
            "experience_years": int(r.adjuster_experience_years),
            "current_workload": int(r.adjuster_current_workload),
            "max_workload": int(r.adjuster_max_workload),
            "available": bool(r.adjuster_availability),
            "active": bool(r.adjuster_active),
            "location": r.adjuster_location,
            "success_rate": round(float(r.adjuster_success_rate), 3),
            "avg_resolution_days": round(float(r.adjuster_avg_resolution_days), 1),
            "similar_claims_handled": int(r.similar_claims_handled),
            "snapshot_claim_id": r.claim_id,
            "source_rows": len(g),
        })
    handlers = pd.DataFrame(rows)
    handlers.to_csv(DERIVED / "handlers.csv", index=False)

    # ---------- city distances (median per unordered pair) ----------
    d = df[["claim_location", "adjuster_location", "distance_km"]].copy()
    d["a"] = d[["claim_location", "adjuster_location"]].min(axis=1)
    d["b"] = d[["claim_location", "adjuster_location"]].max(axis=1)
    pairs = d.groupby(["a", "b"])["distance_km"].median().round(1).reset_index()
    sym = pd.concat([
        pairs.rename(columns={"a": "from_city", "b": "to_city"}),
        pairs[pairs.a != pairs.b].rename(columns={"a": "to_city", "b": "from_city"}),
    ])[["from_city", "to_city", "distance_km"]].sort_values(["from_city", "to_city"])
    sym.to_csv(DERIVED / "city_distances.csv", index=False)

    # ---------- profile facts ----------
    same_city = df["claim_location"] == df["adjuster_location"]
    eligible_like = ((df.expertise_match == 1) & (df.adjuster_active == 1) & (df.adjuster_availability == 1)
                     & (df.adjuster_current_workload < df.adjuster_max_workload))
    per_adj_variation = {c: int(df.groupby("adjuster_id")[c].nunique().max()) for c in [
        "adjuster_expertise", "adjuster_location", "adjuster_experience_years", "adjuster_max_workload"]}
    profile = {
        "rows": int(len(df)), "columns": int(df.shape[1]),
        "unique_claims": int(df.claim_id.nunique()), "unique_adjusters": int(df.adjuster_id.nunique()),
        "missing_values": int(df.isna().sum().sum()),
        "target": "assignment_success",
        "target_distribution": {str(k): int(v) for k, v in df.assignment_success.value_counts().items()},
        "success_rate_same_city": round(float(df[same_city].assignment_success.mean()), 4),
        "success_rate_different_city": round(float(df[~same_city].assignment_success.mean()), 4),
        "successes_same_city": int(df[same_city].assignment_success.sum()),
        "successes_failing_hard_constraints": int(df[~eligible_like].assignment_success.sum()),
        "max_distinct_values_per_adjuster_id": per_adj_variation,
        "multi_p_and_c_expertise_match_rate": float(df[df.adjuster_expertise == "MULTI_P_AND_C"].expertise_match.mean()),
        "dtypes": {c: str(t) for c, t in df.dtypes.items()},
    }
    (DERIVED / "dataset_profile.json").write_text(json.dumps(profile, indent=2))

    # ---------- Flyway seed (reference data only, idempotent) ----------
    SEED_SQL.parent.mkdir(parents=True, exist_ok=True)
    out = ["-- GENERATED by dataset/prepare_dataset.py - do not edit by hand.",
           "-- Reference data derived from the dataset: median distance per city pair. Idempotent.", ""]
    out.append("INSERT INTO city_distances (from_city, to_city, distance_km) VALUES")
    out.append(",\n".join(f"({sql_str(r.from_city)},{sql_str(r.to_city)},{r.distance_km})" for r in sym.itertuples()))
    out.append("ON CONFLICT DO NOTHING;\n")

    SEED_SQL.write_text("\n".join(out))

    print(json.dumps({k: profile[k] for k in ["rows", "unique_claims", "unique_adjusters", "target_distribution",
                                              "success_rate_same_city", "successes_failing_hard_constraints"]}, indent=2))
    print("handlers by expertise:", handlers.primary_expertise.value_counts().to_dict())
    print("available:", handlers.available.sum(), "active:", handlers.active.sum(),
          "at capacity:", int((handlers.current_workload >= handlers.max_workload).sum()))
    print("wrote", SEED_SQL.relative_to(ROOT), f"({SEED_SQL.stat().st_size // 1024} KB)")


if __name__ == "__main__":
    main()
