"""Single source of truth for the model's input contract (shared by training and the service)."""

TARGET = "assignment_success"

# Excluded exactly as in the original training notebook:
#   identifiers (claim_id, adjuster_id) and post-assignment / leakage-risk fields.
EXCLUDED = ["claim_id", "adjuster_id", "previous_reassignments", "assignment_delay_hours"]

CATEGORICAL = [
    "claim_type", "policy_type", "severity", "complexity", "claim_location", "incident_type",
    "adjuster_expertise", "adjuster_location",
]

NUMERICAL = [
    "estimated_loss", "claim_age_days", "fraud_risk_score", "injury_involved", "third_party_involved",
    "adjuster_experience_years", "adjuster_current_workload", "adjuster_max_workload", "adjuster_availability",
    "adjuster_success_rate", "adjuster_avg_resolution_days", "similar_claims_handled", "adjuster_active",
    "distance_km", "workload_ratio", "expertise_match",
]

# Column order of the original dataset (minus exclusions and target). The backend sends exactly these 24 names.
FEATURES = [
    "claim_type", "policy_type", "severity", "complexity", "estimated_loss", "claim_location", "claim_age_days",
    "incident_type", "fraud_risk_score", "injury_involved", "third_party_involved", "adjuster_expertise",
    "adjuster_experience_years", "adjuster_current_workload", "adjuster_max_workload", "adjuster_availability",
    "adjuster_location", "adjuster_success_rate", "adjuster_avg_resolution_days", "similar_claims_handled",
    "adjuster_active", "distance_km", "workload_ratio", "expertise_match",
]

assert sorted(FEATURES) == sorted(CATEGORICAL + NUMERICAL)
