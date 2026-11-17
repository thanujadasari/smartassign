# Triage recommendations (priority, severity, complexity, fraud risk)

Customers do not enter severity, complexity or fraud risk. When a supervisor opens a new claim, SmartAssign
**recommends** values with their reasons (`core/triage/TriageAdvisor.java`). The supervisor decides; if the decision
differs from the recommendation a note of at least 10 characters is required. Both the recommendation and the
decision are stored on the claim (`suggested_*`, `triage_suggestion_json`, `severity`, `complexity`,
`fraud_risk_score`, `triage_note`, `triaged_by_user_id`) and in the audit log.

Each reason is tagged **DATA** (derived from the training dataset) or **ASSUMED** (a business rule to adjust to your policy).

## Severity — DATA
Cut-points on the estimated loss, chosen to best reproduce the dataset's recorded severity:

| Estimated loss (INR) | Severity |
|---|---|
| below 115,000 | LOW |
| 115,000 – 307,999 | MEDIUM |
| 308,000 – 833,999 | HIGH |
| 834,000 and above | CRITICAL |

On the 7,000 dataset claims these thresholds match the recorded severity exactly for 59% of claims and within one
level for 98.7% (always predicting the most common level would match 39.8%). Injury and incident type did not
change severity in the data, so they are not used. This is a starting point for the supervisor, not a decision.

## Complexity — DATA + ASSUMED
Loss is only weakly related to complexity in the data (thresholds 122,000 / 834,000 match 46.8% vs a 41.6% baseline),
so two assumed rules are added:
* injury **and** a third party → at least MEDIUM;
* liability claim with injury and a loss ≥ 308,000 → HIGH.

## Fraud risk — ASSUMED red-flag checklist
In the dataset the fraud score has no relationship with any field a customer enters, so it cannot be learned from
that data. The recommendation is a transparent checklist: start at 0.10 and add

| Flag | Weight |
|---|---|
| reported more than 30 days after the incident | +0.15 |
| loss above the 95th percentile for the claim type (Auto 606k, Liability 841k, Property 1,067k, Workers' Comp 844k) | +0.15 |
| theft | +0.10 |
| customer registered 2+ other claims in the last 12 months | +0.15 |
| description shorter than 40 characters | +0.05 |

capped at 0.95. Replace these with your special-investigations unit's own rules.

## Priority — ASSUMED business rules
The dataset has no priority column, so these are business rules (`TriageAdvisor`). The suggestion is calculated when the
customer registers the claim (so the Triage Queue lists the most urgent claims first) and again when the supervisor
triages. The supervisor chooses the final priority at triage and can change it later (`PATCH /api/claims/{id}/priority`,
reason required) until the claim is resolved. Every change is in the claim timeline and the audit log.

| Priority | Suggested when any of these is true |
|---|---|
| **HIGH** | someone was injured · fire · property damage of INR 308,000 or more (major) · the customer ticked "the danger or damage is still ongoing" · any other loss of INR 834,000 or more |
| **MEDIUM** | (not HIGH and) vehicle accident/damage of INR 115,000 or more · property damage of INR 115,000 or more · other loss of INR 115,000 or more · theft · fraud red flags · liability claim with a third party (needs investigation) |
| **LOW** | none of the above - minor vehicle scratches, small property damage, minor claims |

The customer never sees the priority. Queues (triage, routing, handler's assigned claims) are ordered HIGH → MEDIUM →
LOW, then oldest first. Priority does not change eligibility or ranking.
