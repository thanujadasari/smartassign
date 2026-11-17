# API reference

Base URL `http://localhost:8080`. All `/api/**` endpoints except login need `Authorization: Bearer <token>`.
Interactive docs: `/swagger-ui.html` (OpenAPI JSON at `/v3/api-docs`). Errors share one shape:
`{timestamp, status, error, message, path, fieldErrors[{field,message}], details[]}`.
Status codes: 400 validation · 401 not logged in · 403 role · 404 · 409 state conflict · 422 business rule (e.g. ineligible handler).

Roles: **CUSTOMER** (`/api/customer/**`) · **SUPERVISOR** (everything else under `/api`) · **HANDLER** (`/api/handler-portal/**`).
Public: login, sign-up, `/api/auth/registration-options`, `/api/reference/**`.

## Auth
| Method | Path | Notes |
|---|---|---|
| POST | /api/auth/login | `{email,password}` → `{token, tokenType, expiresAt, user{id,fullName,email,phone,role,handlerId}}` |
| POST | /api/auth/logout | revokes the token |
| GET | /api/auth/me | current user |
| GET | /api/auth/registration-options | `{staffSignupEnabled}` |
| POST | /api/auth/register/customer | `{fullName,email,phone?,password(≥10)}` |
| POST | /api/auth/register/staff | `{fullName,email,phone?,password,role: SUPERVISOR|HANDLER,staffCode,handlerProfile?{primaryExpertise,experienceYears,location}}` · 403 on a wrong code or when `SMARTASSIGN_STAFF_REGISTRATION_CODE` is empty |

## Customer portal
| Method | Path | Notes |
|---|---|---|
| POST | /api/customer/claims | `{claimType,policyType,policyNumber (P + 4-12 digits, e.g. P0001; stored upper-case),incidentType,incidentDate,location,estimatedLoss,injuryInvolved,thirdPartyInvolved,ongoingRisk?,description(≥20),contactPhone?}` → status SUBMITTED, number `CLM-000001…` |
| GET | /api/customer/claims | own claims only |
| GET | /api/customer/claims/{id} | own claim + simplified timeline; 404 for anyone else's. No fraud risk / complexity |

## Supervisor: claims and triage
| Method | Path | Notes |
|---|---|---|
| PATCH | /api/claims/{id}/priority | `{priority: HIGH/MEDIUM/LOW, reason (5-500)}` - after triage, until resolved |
| GET | /api/claims | `sort=priority` orders HIGH→LOW (decided, else suggested); `priority` filter; `q` (claim, policy or customer), `status` (SUBMITTED/UNASSIGNED/ASSIGNED/MANUAL_REVIEW/RESOLVED), `claimType`, `severity`, `location`, `handlerId`, paging, sort |
| GET | /api/claims/{id} | `{claim, timeline}` - claim includes customer, triage, assigned handler and **assignedBy** |
| GET | /api/claims/{id}/activity | audit entries |
| GET | /api/claims/{id}/triage-suggestion | recommended severity / complexity / fraud risk with reasons (nothing saved) |
| POST | /api/claims/{id}/triage | `{priority,severity,complexity,fraudRiskScore,note?}`; note ≥10 chars required when it differs from the recommendation; SUBMITTED → UNASSIGNED |

## Supervisor: handlers
| Method | Path | Notes |
|---|---|---|
| GET | /api/handlers | `q`, `expertise`, `location`, `available`, `active`, `status`, paging, sort |
| GET | /api/handlers/{id} | profile, open + resolved claims (with assigning supervisor), availability history |
| PUT | /api/handlers/{id} | `{primaryExpertise, additionalSkills?, experienceYears, maxWorkload, location}` - availability is not editable here |
| PATCH | /api/handlers/{id}/status | `{active, reason?}` |

## Handler portal
| Method | Path | Notes |
|---|---|---|
| GET | /api/handler-portal/me | `{handler, resolutionTargetDays, availabilityHistory}` |
| PUT | /api/handler-portal/availability | `{available, reason}` - reason (≥5 chars) required when unavailable |
| GET | /api/handler-portal/claims?view=open|resolved | claims assigned to me, each with `assignedBy` |
| GET | /api/handler-portal/claims/{id} | `{claim, timeline}` (only my claims) |
| POST | /api/handler-portal/claims/{id}/resolve | `{note(10-2000)}` → RESOLVED, workload −1, statistics updated |

There is no accept / reject endpoint: assignments are made by supervisors.

## Routing
| Method | Path | Returns |
|---|---|---|
| POST | /api/routing/{claimId}/eligibility | `eligible[]`, `ineligible[]` (each with rule-by-rule results), `exclusionBreakdown`, `rulesApplied` |
| POST | /api/routing/{claimId}/recommendations | `runId`, `recommendations[]` (rank, handler, suitabilityScore, factors[], explanation), `scoringBasis` (PROFILE_AND_PREDICTED_FIT / PROFILE_ONLY), `scoringNote`, `weightsApplied`, `ineligible[]`, `fallback` (when no one is eligible), `validUntil` |
| GET | /api/routing/{claimId}/runs | run history |
| GET | /api/routing/runs/{runId} | stored run |

## Assignments
| Method | Path | Body |
|---|---|---|
| POST | /api/assignments | `{claimId, runId}` → PENDING decision for the run's top recommendation |
| POST | /api/assignments/{id}/approve | `{note?}` |
| POST | /api/assignments/{id}/override | `{handlerId, reason (10–1000)}` — handler must be eligible |
| POST | /api/assignments/{id}/manual-review | `{reason}` |
| GET | /api/assignments, /api/assignments/{id} | filters `claimId`, `status`, `handlerId` |

## Fairness, audit, dashboard, settings, reference
| Method | Path | Notes |
|---|---|---|
| GET | /api/fairness/summary?windowDays=30 | report, warnings, distributions |
| GET | /api/audit · /api/audit/export | filters `claimNumber, handlerCode, eventType, action, actor, from, to, q`; CSV |
| GET | /api/dashboard/summary | KPIs, charts, recent claims/actions, data-source status |
| GET/PUT | /api/settings/ranking-weights (+ /history) | `{expertise, workload, experience, successRate, similarClaims, proximity, predictedFit, fairnessThreshold, fairnessPenalty, usePredictedFit}` |
| GET / PUT | /api/settings/eligibility-rules · /{code} | `{enabled, param1?, param2?}` |
| GET | /api/reference/options | enum options + known cities for forms |
| GET | /api/integration/claimcenter/status | NOT_CONNECTED + field mapping + limitations |
| GET | /api/internal/suitability-service | Supervisor-only internal diagnostics (not used by the UI) |

## Internal contract with the ML service (backend → ml-service only)
```
POST /ml/predict  {"candidates":[{"candidate_id":"42","features":{ ...24 features... }}]}
 →  {"status":"ok","model_version":"…","predictions":[{"candidate_id":"42","suitability":0.83}]}
GET  /ml/health   →  {"status":"ok","model_loaded":true,"model_version":"…"}
GET  /ml/model-info → internal metadata
```
Any error, timeout, value outside 0–1 or missing candidate ⇒ predictions treated as unavailable (profile-only ranking).
