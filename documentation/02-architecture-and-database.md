# Architecture and database (Step 3–4)

```
React UI ──REST/JSON (JWT)──► Spring Boot backend ──JDBC──► PostgreSQL (Flyway)
                                     │
                                     └──internal HTTP──► FastAPI suitability service (eligible candidates only)
```
The frontend never calls the ML service. Spring Boot is the single gateway and enforces every rule.

## Backend layout (`backend/src/main/java/com/smartassign`)
| Package | Responsibility |
|---|---|
| `core` (framework-free) | eligibility engine, scoring engine + explanations, fairness calculator, triage + priority advisor, ML feature contract |
| `auth` | users (CUSTOMER / SUPERVISOR / HANDLER), sign-up with staff code, JWT issue/validate/revoke |
| `claim`, `handler` | CRUD, search/filter/sort/paginate, close claim |
| `routing` | eligibility runs, recommendation runs, safe fallback |
| `assignment` | approve / override / manual review (transactional, live re-check) |
| `fairness`, `dashboard` | monitoring and operational summaries |
| `audit` | append-only audit log, search, CSV export |
| `settings` | ranking weights (versioned) and eligibility rules |
| `ml` | internal client for the suitability service, health, model metadata |
| `integration` | ClaimCenter boundary (not connected) and field mapping |

## Routing pipeline
1. Load claim + all handlers (snapshots). 2. Apply hard rules → rule-by-rule results for every handler.
3. Ineligible handlers stop here. 4. If none are eligible → **fallback**: claim → `MANUAL_REVIEW`,
`FALLBACK_MANUAL_REVIEW` decision row, audit record, reason built from the actual rule failures. Rules are never relaxed.
5. Eligible handlers only → suitability service (if enabled). 6. Explainable ranking with fairness control.
7. Persist run, eligibility results, ranked recommendations; audit.

### Eligibility rules (table `eligibility_rules`)
| Code | Type | Default |
|---|---|---|
| ACTIVE, AVAILABLE, EXPERTISE, CAPACITY | Core – locked, cannot be disabled (DB check + engine) | on |
| CRITICAL_SEVERITY_EXPERIENCE | **Assumed** business rule – critical claims need ≥ 5 yrs | on |
| HIGH_FRAUD_RISK_EXPERIENCE | **Assumed** business rule – fraud ≥ 0.70 needs ≥ 8 yrs | on |
An unknown rule code fails safe (handler excluded).

### Ranking (weights in `ranking_settings`, must total 100 — enforced by DB check and API)
| Factor | Formula (0–1) | Default weight |
|---|---|---|
| Expertise match | 1.0 specialist · 0.8 multi-line · 0.7 secondary skill | 10 |
| Workload fit | 1 − current/max | 20 |
| Relevant experience | min(years/20, 1) | 10 |
| Historical success rate | success rate | 15 |
| Similar claims handled | min(n/100, 1) | 10 |
| Location proximity | 1 − distance/max distance | 15 |
| Predicted claim fit | ML score (0–1, relative) | 20 |

Total = Σ value × weight (0–100). **Fairness control:** if (current+1)/max > threshold (0.85) the total is multiplied by
(1 − penalty) (0.15). **ML fallback:** the predicted-fit weight is redistributed proportionally to the other factors when
the service is off, failing, returns out-of-range values, or misses any candidate (all candidates are ranked on the same
basis). The explanation cites the three strongest actual contributions plus caveats.

### Assignment transaction
Lock assignment → lock claim → lock handler → re-run hard rules on live data → workload +1 → claim `ASSIGNED`
→ decision saved → audit record — all in one transaction. Overrides must name a handler that was eligible in the run
**and** is still eligible; a reason (≥ 10 chars) is required. Decided rows are never overwritten.

## Database (Flyway `V1__initial_schema.sql`, `V2__three_portals_real_data.sql`, `V3__priority_and_remove_benchmarking.sql`, reference seed `R__reference_city_distances.sql`)
users · claims · claim_status_history · handlers · handler_skills · handler_availability_log · city_distances ·
eligibility_rules · ranking_settings · model_metadata · recommendation_runs · eligibility_results ·
assignment_recommendations · assignments · audit_logs

V3 adds claim priority (`ongoing_risk`, `suggested_priority`, `priority_reasons_json`, `priority`, `priority_note`, `priority_set_by_user_id`, `priority_set_at`, `priority_rank` for most-urgent-first ordering) and drops the benchmarking tables.

V2 removes every synthetic/demo row created by V1 and the old seed, then adds: user roles CUSTOMER/SUPERVISOR/HANDLER and
phone; claims.customer_id, policy number, incident date, triage recommendation + decision + who/when, assigned_by_user_id,
resolution fields; handlers.user_id, unavailable_reason (required when unavailable - CHECK), real statistics
(resolved_count, resolved_on_time_count, total_resolution_days; success rate / average resolution are NULL until the
first resolution). The only seeded data is the city-distance reference table.

Key safeguards: CHECK constraints on every enum and range; a claim cannot leave SUBMITTED without severity, complexity
and fraud risk; a RESOLVED claim must have a handler, a time and a note; weights must total 100; locked rules cannot be
disabled; one active settings row; one PENDING assignment per claim; an OVERRIDDEN assignment must have a reason;
`audit_logs` is append-only (trigger); optimistic `version` columns on claims and handlers.
