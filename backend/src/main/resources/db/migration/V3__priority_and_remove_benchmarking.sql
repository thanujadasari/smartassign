-- =====================================================================
-- SmartAssign V3
--  1. Claim priority (HIGH / MEDIUM / LOW): suggested by rules when the customer
--     registers the claim, decided by the supervisor at triage, changeable later.
--  2. Strategy Benchmarking removed from the application: its tables are dropped.
-- =====================================================================

-- ---------- 1. priority
ALTER TABLE claims ADD COLUMN ongoing_risk BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE claims ADD COLUMN suggested_priority VARCHAR(10) CHECK (suggested_priority IN ('HIGH', 'MEDIUM', 'LOW'));
ALTER TABLE claims ADD COLUMN priority_reasons_json TEXT;
ALTER TABLE claims ADD COLUMN priority VARCHAR(10) CHECK (priority IN ('HIGH', 'MEDIUM', 'LOW'));
ALTER TABLE claims ADD COLUMN priority_note TEXT;
ALTER TABLE claims ADD COLUMN priority_set_by_user_id BIGINT REFERENCES users (id) ON DELETE RESTRICT;
ALTER TABLE claims ADD COLUMN priority_set_at TIMESTAMPTZ;
-- 1 = HIGH, 2 = MEDIUM, 3 = LOW: the decided priority, or the suggestion until the supervisor decides.
-- Lets every queue sort most-urgent first.
ALTER TABLE claims ADD COLUMN priority_rank SMALLINT NOT NULL DEFAULT 2 CHECK (priority_rank BETWEEN 1 AND 3);

-- Claims triaged before V3 have no priority yet. They get one derived from the severity the supervisor
-- already chose (CRITICAL/HIGH -> HIGH, MEDIUM -> MEDIUM, LOW -> LOW), recorded as such.
UPDATE claims
SET priority = CASE severity WHEN 'CRITICAL' THEN 'HIGH' WHEN 'HIGH' THEN 'HIGH' WHEN 'MEDIUM' THEN 'MEDIUM' ELSE 'LOW' END,
    priority_note = 'Set from the triaged severity when priorities were introduced (migration V3)',
    priority_set_at = now()
WHERE status <> 'SUBMITTED' AND priority IS NULL;
UPDATE claims SET priority_rank = CASE priority WHEN 'HIGH' THEN 1 WHEN 'MEDIUM' THEN 2 WHEN 'LOW' THEN 3 ELSE 2 END;

ALTER TABLE claims ADD CONSTRAINT ck_claims_priority_after_triage CHECK (status = 'SUBMITTED' OR priority IS NOT NULL);
CREATE INDEX ix_claims_priority_queue ON claims (status, priority_rank, created_at);

-- ---------- 2. remove benchmarking
DROP TABLE IF EXISTS benchmark_assignments;
DROP TABLE IF EXISTS benchmark_results;
DROP TABLE IF EXISTS benchmark_runs;
