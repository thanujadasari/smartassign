"""Request / response schemas. Field names match the training dataset columns exactly."""
from __future__ import annotations

from typing import Annotated, Literal

from pydantic import BaseModel, ConfigDict, Field, StringConstraints, field_validator, model_validator

from app.config import MAX_CANDIDATES

ClaimType = Literal["AUTO", "PROPERTY", "LIABILITY", "WORKERS_COMPENSATION"]
PolicyType = Literal["AUTO", "HOME", "COMMERCIAL_PROPERTY", "GENERAL_LIABILITY", "COMMERCIAL_LIABILITY",
                     "WORKERS_COMPENSATION"]
Severity = Literal["LOW", "MEDIUM", "HIGH", "CRITICAL"]
Complexity = Literal["LOW", "MEDIUM", "HIGH"]
IncidentType = Literal["ACCIDENT", "COLLISION", "DAMAGE", "FIRE", "FLOOD", "STORM", "THEFT", "LIABILITY", "INJURY",
                       "WORKPLACE_ACCIDENT"]
Expertise = Literal["AUTO", "PROPERTY", "LIABILITY", "WORKERS_COMPENSATION", "MULTI_P_AND_C"]
City = Annotated[str, StringConstraints(strip_whitespace=True, min_length=1, max_length=60)]
Flag = Literal[0, 1]


class Features(BaseModel):
    """The 24 model inputs. Identifiers and post-assignment fields are deliberately not accepted."""
    model_config = ConfigDict(extra="forbid")

    claim_type: ClaimType
    policy_type: PolicyType
    severity: Severity
    complexity: Complexity
    estimated_loss: float = Field(ge=0, le=1e12)
    claim_location: City
    claim_age_days: int = Field(ge=0, le=3650)
    incident_type: IncidentType
    fraud_risk_score: float = Field(ge=0, le=1)
    injury_involved: Flag
    third_party_involved: Flag
    adjuster_expertise: Expertise
    adjuster_experience_years: int = Field(ge=0, le=60)
    adjuster_current_workload: int = Field(ge=0, le=1000)
    adjuster_max_workload: int = Field(ge=1, le=1000)
    adjuster_availability: Flag
    adjuster_location: City
    adjuster_success_rate: float = Field(ge=0, le=1)
    adjuster_avg_resolution_days: float = Field(ge=0, le=1000)
    similar_claims_handled: int = Field(ge=0, le=100000)
    adjuster_active: Flag
    distance_km: float = Field(ge=0, le=50000)
    workload_ratio: float = Field(ge=0, le=100)
    expertise_match: Flag

    @field_validator("injury_involved", "third_party_involved", "adjuster_availability", "adjuster_active",
                     "expertise_match", mode="before")
    @classmethod
    def _bool_to_int(cls, v):
        if isinstance(v, bool):
            return int(v)
        return v


class Candidate(BaseModel):
    model_config = ConfigDict(extra="forbid")
    candidate_id: Annotated[str, StringConstraints(strip_whitespace=True, min_length=1, max_length=64)]
    features: Features


class PredictRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")
    candidates: list[Candidate] = Field(min_length=1, max_length=MAX_CANDIDATES)

    @model_validator(mode="after")
    def _unique_ids(self):
        ids = [c.candidate_id for c in self.candidates]
        if len(ids) != len(set(ids)):
            raise ValueError("candidate_id values must be unique")
        return self


class Prediction(BaseModel):
    candidate_id: str
    suitability: float = Field(ge=0, le=1)


class PredictResponse(BaseModel):
    status: Literal["ok"] = "ok"
    model_version: str
    predictions: list[Prediction]
    warnings: list[str] = []


class HealthResponse(BaseModel):
    status: Literal["ok", "degraded"]
    model_loaded: bool
    model_version: str | None = None
    detail: str | None = None


class ErrorResponse(BaseModel):
    status: Literal["error", "unavailable"]
    detail: str | list | dict
