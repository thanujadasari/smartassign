-- =====================================================================
-- SmartAssign V2 - three portals (customer / supervisor / handler), real data only
--
-- 1. Removes every synthetic record that V1 + the old seed loaded (claims,
--    handlers, demo users, routing runs, benchmarks, audit rows). The dataset
--    is kept only where it belongs: training the suitability model (ml-service)
--    and the city distance reference table used for proximity.
-- 2. Adds customer, staff and handler-portal data to the schema.
--
-- WARNING: this migration deletes all operational rows created under V1.
-- That is intentional - under V1 every claim and handler was synthetic.
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. Purge synthetic / demo data (children first)
-- ---------------------------------------------------------------------
DELETE FROM benchmark_assignments;
DELETE FROM benchmark_results;
DELETE FROM benchmark_runs;
DELETE FROM assignments;
DELETE FROM assignment_recommendations;
DELETE FROM eligibility_results;
DELETE FROM recommendation_runs;
DELETE FROM claims;
DELETE FROM handler_skills;
DELETE FROM handlers;
DELETE FROM users;

-- audit_logs is append-only; the trigger is lifted only for this one-off purge
ALTER TABLE audit_logs DISABLE TRIGGER trg_audit_logs_no_update;
DELETE FROM audit_logs;
ALTER TABLE audit_logs ENABLE TRIGGER trg_audit_logs_no_update;

ALTER SEQUENCE claim_number_seq RESTART WITH 1;
ALTER SEQUENCE handler_code_seq RESTART WITH 1;

-- ---------------------------------------------------------------------
-- 2. Users: three roles, login by email
-- ---------------------------------------------------------------------
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_role_check;
ALTER TABLE users ADD CONSTRAINT ck_users_role CHECK (role IN ('CUSTOMER', 'SUPERVISOR', 'HANDLER'));
ALTER TABLE users DROP COLUMN demo_account;
ALTER TABLE users ALTER COLUMN email SET NOT NULL;
ALTER TABLE users ADD COLUMN phone VARCHAR(30);
CREATE UNIQUE INDEX uq_users_email_lower ON users (lower(email));

-- ---------------------------------------------------------------------
-- 3. Handlers: linked to a HANDLER user, availability reason, real statistics
-- ---------------------------------------------------------------------
ALTER TABLE handlers DROP COLUMN name_is_synthetic;
ALTER TABLE handlers ADD COLUMN user_id BIGINT REFERENCES users (id) ON DELETE RESTRICT;
CREATE UNIQUE INDEX uq_handlers_user ON handlers (user_id) WHERE user_id IS NOT NULL;
ALTER TABLE handlers ADD COLUMN phone VARCHAR(30);
ALTER TABLE handlers ADD COLUMN unavailable_reason TEXT;
ALTER TABLE handlers ADD COLUMN availability_changed_at TIMESTAMPTZ;
-- statistics are computed from claims the handler resolved in this application;
-- NULL means "no history yet" (the ranking then uses a documented neutral value)
ALTER TABLE handlers ALTER COLUMN success_rate DROP NOT NULL;
ALTER TABLE handlers ALTER COLUMN avg_resolution_days DROP NOT NULL;
ALTER TABLE handlers ADD COLUMN resolved_count INTEGER NOT NULL DEFAULT 0 CHECK (resolved_count >= 0);
ALTER TABLE handlers ADD COLUMN resolved_on_time_count INTEGER NOT NULL DEFAULT 0 CHECK (resolved_on_time_count >= 0);
ALTER TABLE handlers ADD COLUMN total_resolution_days NUMERIC(10,2) NOT NULL DEFAULT 0 CHECK (total_resolution_days >= 0);
ALTER TABLE handlers ADD CONSTRAINT ck_handlers_unavailable_reason
    CHECK (available OR (unavailable_reason IS NOT NULL AND length(trim(unavailable_reason)) >= 5));
ALTER TABLE handlers DROP CONSTRAINT IF EXISTS handlers_source_check;
ALTER TABLE handlers ADD CONSTRAINT ck_handlers_source CHECK (source IN ('STAFF_REGISTRATION', 'MANUAL_ENTRY', 'CLAIMCENTER'));
ALTER TABLE handlers ALTER COLUMN source SET DEFAULT 'STAFF_REGISTRATION';

CREATE TABLE handler_availability_log (
    id              BIGSERIAL PRIMARY KEY,
    handler_id      BIGINT       NOT NULL REFERENCES handlers (id) ON DELETE CASCADE,
    available       BOOLEAN      NOT NULL,
    reason          TEXT,
    changed_by      VARCHAR(160) NOT NULL,
    changed_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_availability_log_reason CHECK (available OR (reason IS NOT NULL AND length(trim(reason)) >= 5))
);
CREATE INDEX ix_availability_log_handler ON handler_availability_log (handler_id, changed_at DESC);

-- ---------------------------------------------------------------------
-- 4. Claims: submitted by a customer, triaged and assigned by a supervisor,
--    resolved by a handler
-- ---------------------------------------------------------------------
ALTER TABLE claims ADD COLUMN customer_id BIGINT NOT NULL REFERENCES users (id) ON DELETE RESTRICT;
ALTER TABLE claims ADD COLUMN policy_number VARCHAR(40) NOT NULL;
ALTER TABLE claims ADD COLUMN incident_date DATE NOT NULL;
ALTER TABLE claims ADD COLUMN contact_phone VARCHAR(30);

-- severity / complexity / fraud risk are set by the supervisor at triage
ALTER TABLE claims ALTER COLUMN severity DROP NOT NULL;
ALTER TABLE claims ALTER COLUMN complexity DROP NOT NULL;
ALTER TABLE claims ALTER COLUMN fraud_risk_score DROP NOT NULL;
ALTER TABLE claims ADD COLUMN suggested_severity VARCHAR(10) CHECK (suggested_severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL'));
ALTER TABLE claims ADD COLUMN suggested_complexity VARCHAR(10) CHECK (suggested_complexity IN ('LOW', 'MEDIUM', 'HIGH'));
ALTER TABLE claims ADD COLUMN suggested_fraud_risk NUMERIC(4,3) CHECK (suggested_fraud_risk BETWEEN 0 AND 1);
ALTER TABLE claims ADD COLUMN triage_suggestion_json TEXT;
ALTER TABLE claims ADD COLUMN triage_note TEXT;
ALTER TABLE claims ADD COLUMN triaged_by_user_id BIGINT REFERENCES users (id) ON DELETE RESTRICT;
ALTER TABLE claims ADD COLUMN triaged_at TIMESTAMPTZ;

ALTER TABLE claims ADD COLUMN assigned_by_user_id BIGINT REFERENCES users (id) ON DELETE RESTRICT;
ALTER TABLE claims ADD COLUMN resolved_at TIMESTAMPTZ;
ALTER TABLE claims ADD COLUMN resolution_note TEXT;
ALTER TABLE claims ADD COLUMN resolution_days NUMERIC(8,2);

ALTER TABLE claims DROP CONSTRAINT IF EXISTS claims_status_check;
ALTER TABLE claims ADD CONSTRAINT ck_claims_status
    CHECK (status IN ('SUBMITTED', 'UNASSIGNED', 'ASSIGNED', 'MANUAL_REVIEW', 'RESOLVED'));
ALTER TABLE claims ALTER COLUMN status SET DEFAULT 'SUBMITTED';
ALTER TABLE claims ADD CONSTRAINT ck_claims_triaged_before_routing
    CHECK (status = 'SUBMITTED' OR (severity IS NOT NULL AND complexity IS NOT NULL AND fraud_risk_score IS NOT NULL));
ALTER TABLE claims ADD CONSTRAINT ck_claims_resolved
    CHECK (status <> 'RESOLVED' OR (resolved_at IS NOT NULL AND assigned_handler_id IS NOT NULL
                                    AND resolution_note IS NOT NULL));
ALTER TABLE claims DROP CONSTRAINT IF EXISTS claims_source_check;
ALTER TABLE claims ADD CONSTRAINT ck_claims_source CHECK (source IN ('CUSTOMER_PORTAL', 'CLAIMCENTER'));
ALTER TABLE claims ALTER COLUMN source SET DEFAULT 'CUSTOMER_PORTAL';
CREATE INDEX ix_claims_customer ON claims (customer_id, created_at DESC);

CREATE TABLE claim_status_history (
    id              BIGSERIAL PRIMARY KEY,
    claim_id        BIGINT       NOT NULL REFERENCES claims (id) ON DELETE CASCADE,
    from_status     VARCHAR(20),
    to_status       VARCHAR(20)  NOT NULL,
    note            TEXT,
    actor_user_id   BIGINT       REFERENCES users (id) ON DELETE SET NULL,
    actor_name      VARCHAR(120) NOT NULL,
    actor_role      VARCHAR(20)  NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_status_history_claim ON claim_status_history (claim_id, created_at);

-- ---------------------------------------------------------------------
-- 5. Benchmarks now replay real claims only
-- ---------------------------------------------------------------------
ALTER TABLE benchmark_runs ALTER COLUMN synthetic_data SET DEFAULT FALSE;
ALTER TABLE benchmark_runs ALTER COLUMN claim_filter SET DEFAULT 'TRIAGED';
