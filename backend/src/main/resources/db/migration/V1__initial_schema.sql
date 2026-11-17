-- =====================================================================
-- SmartAssign - initial schema
-- Conventions: bigserial PKs, created_at/updated_at timestamps (UTC),
-- enums stored as VARCHAR with CHECK constraints, JSON payloads stored as TEXT.
-- =====================================================================

-- ---------------------------------------------------------------------
-- Sequences for human-readable identifiers of records created in the app
-- (seeded dataset uses C00001..C07000 and A001..A150)
-- ---------------------------------------------------------------------
CREATE SEQUENCE claim_number_seq START WITH 7001;
CREATE SEQUENCE handler_code_seq START WITH 151;

-- ---------------------------------------------------------------------
-- Users / authentication
-- ---------------------------------------------------------------------
CREATE TABLE users (
    id              BIGSERIAL PRIMARY KEY,
    username        VARCHAR(60)  NOT NULL,
    password_hash   VARCHAR(100) NOT NULL,
    full_name       VARCHAR(120) NOT NULL,
    email           VARCHAR(160),
    role            VARCHAR(20)  NOT NULL CHECK (role IN ('ADMIN', 'SUPERVISOR', 'ANALYST')),
    enabled         BOOLEAN      NOT NULL DEFAULT TRUE,
    demo_account    BOOLEAN      NOT NULL DEFAULT FALSE,
    last_login_at   TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_users_username UNIQUE (username),
    CONSTRAINT uq_users_email UNIQUE (email)
);

-- ---------------------------------------------------------------------
-- Reference data: distances between the cities used by the dataset
-- ---------------------------------------------------------------------
CREATE TABLE city_distances (
    from_city    VARCHAR(60)   NOT NULL,
    to_city      VARCHAR(60)   NOT NULL,
    distance_km  NUMERIC(7,1)  NOT NULL CHECK (distance_km >= 0),
    PRIMARY KEY (from_city, to_city)
);

-- ---------------------------------------------------------------------
-- Handlers (claim adjusters)
-- ---------------------------------------------------------------------
CREATE TABLE handlers (
    id                      BIGSERIAL PRIMARY KEY,
    handler_code            VARCHAR(20)  NOT NULL,
    full_name               VARCHAR(120) NOT NULL,
    name_is_synthetic       BOOLEAN      NOT NULL DEFAULT FALSE,
    email                   VARCHAR(160),
    primary_expertise       VARCHAR(30)  NOT NULL CHECK (primary_expertise IN
                                ('AUTO', 'PROPERTY', 'LIABILITY', 'WORKERS_COMPENSATION', 'MULTI_P_AND_C')),
    experience_years        INTEGER      NOT NULL CHECK (experience_years BETWEEN 0 AND 60),
    current_workload        INTEGER      NOT NULL DEFAULT 0 CHECK (current_workload >= 0),
    max_workload            INTEGER      NOT NULL CHECK (max_workload BETWEEN 1 AND 200),
    available               BOOLEAN      NOT NULL DEFAULT TRUE,
    active                  BOOLEAN      NOT NULL DEFAULT TRUE,
    location                VARCHAR(60)  NOT NULL,
    success_rate            NUMERIC(4,3) NOT NULL CHECK (success_rate BETWEEN 0 AND 1),
    avg_resolution_days     NUMERIC(5,1) NOT NULL CHECK (avg_resolution_days >= 0),
    similar_claims_handled  INTEGER      NOT NULL DEFAULT 0 CHECK (similar_claims_handled >= 0),
    source                  VARCHAR(30)  NOT NULL DEFAULT 'MANUAL_ENTRY'
                                CHECK (source IN ('SYNTHETIC_DATASET', 'MANUAL_ENTRY', 'CLAIMCENTER')),
    version                 BIGINT       NOT NULL DEFAULT 0,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_handlers_code UNIQUE (handler_code)
);
-- NOTE: current_workload may exceed max_workload (e.g. capacity reduced by an admin);
-- this is surfaced by Fairness Monitoring and blocked by the CAPACITY eligibility rule.
CREATE INDEX ix_handlers_expertise ON handlers (primary_expertise);
CREATE INDEX ix_handlers_location ON handlers (location);
CREATE INDEX ix_handlers_active_available ON handlers (active, available);

CREATE TABLE handler_skills (
    handler_id  BIGINT      NOT NULL REFERENCES handlers (id) ON DELETE CASCADE,
    skill_code  VARCHAR(30) NOT NULL CHECK (skill_code IN ('AUTO', 'PROPERTY', 'LIABILITY', 'WORKERS_COMPENSATION')),
    PRIMARY KEY (handler_id, skill_code)
);
CREATE INDEX ix_handler_skills_skill ON handler_skills (skill_code);

-- ---------------------------------------------------------------------
-- Claims
-- ---------------------------------------------------------------------
CREATE TABLE claims (
    id                      BIGSERIAL PRIMARY KEY,
    claim_number            VARCHAR(30)   NOT NULL,
    claim_type              VARCHAR(30)   NOT NULL CHECK (claim_type IN ('AUTO', 'PROPERTY', 'LIABILITY', 'WORKERS_COMPENSATION')),
    policy_type             VARCHAR(30)   NOT NULL CHECK (policy_type IN ('AUTO', 'HOME', 'COMMERCIAL_PROPERTY',
                                'GENERAL_LIABILITY', 'COMMERCIAL_LIABILITY', 'WORKERS_COMPENSATION')),
    severity                VARCHAR(10)   NOT NULL CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    complexity              VARCHAR(10)   NOT NULL CHECK (complexity IN ('LOW', 'MEDIUM', 'HIGH')),
    estimated_loss          NUMERIC(14,2) NOT NULL CHECK (estimated_loss >= 0),
    claim_location          VARCHAR(60)   NOT NULL,
    claim_age_days          INTEGER       NOT NULL DEFAULT 0 CHECK (claim_age_days >= 0),
    incident_type           VARCHAR(30)   NOT NULL CHECK (incident_type IN ('ACCIDENT', 'COLLISION', 'DAMAGE', 'FIRE',
                                'FLOOD', 'STORM', 'THEFT', 'LIABILITY', 'INJURY', 'WORKPLACE_ACCIDENT')),
    fraud_risk_score        NUMERIC(4,3)  NOT NULL CHECK (fraud_risk_score BETWEEN 0 AND 1),
    injury_involved         BOOLEAN       NOT NULL DEFAULT FALSE,
    third_party_involved    BOOLEAN       NOT NULL DEFAULT FALSE,
    previous_reassignments  INTEGER       NOT NULL DEFAULT 0 CHECK (previous_reassignments >= 0),
    description             TEXT,
    status                  VARCHAR(20)   NOT NULL DEFAULT 'UNASSIGNED'
                                CHECK (status IN ('UNASSIGNED', 'ASSIGNED', 'MANUAL_REVIEW', 'CLOSED')),
    assigned_handler_id     BIGINT        REFERENCES handlers (id) ON DELETE RESTRICT,
    assigned_at             TIMESTAMPTZ,
    source                  VARCHAR(30)   NOT NULL DEFAULT 'MANUAL_ENTRY'
                                CHECK (source IN ('SYNTHETIC_DATASET', 'MANUAL_ENTRY', 'CLAIMCENTER')),
    external_reference      VARCHAR(80),
    version                 BIGINT        NOT NULL DEFAULT 0,
    created_at              TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_claims_number UNIQUE (claim_number),
    CONSTRAINT ck_claims_assigned_has_handler CHECK (status <> 'ASSIGNED' OR assigned_handler_id IS NOT NULL)
);
CREATE INDEX ix_claims_status ON claims (status);
CREATE INDEX ix_claims_type ON claims (claim_type);
CREATE INDEX ix_claims_severity ON claims (severity);
CREATE INDEX ix_claims_assigned_handler ON claims (assigned_handler_id);
CREATE INDEX ix_claims_created_at ON claims (created_at DESC);

-- ---------------------------------------------------------------------
-- Configuration: eligibility rules and ranking weights
-- ---------------------------------------------------------------------
CREATE TABLE eligibility_rules (
    code         VARCHAR(40)  PRIMARY KEY,
    name         VARCHAR(120) NOT NULL,
    description  TEXT         NOT NULL,
    category     VARCHAR(20)  NOT NULL CHECK (category IN ('CORE', 'BUSINESS')),
    enabled      BOOLEAN      NOT NULL DEFAULT TRUE,
    locked       BOOLEAN      NOT NULL DEFAULT FALSE,   -- core rules cannot be disabled
    param_1      NUMERIC(10,3),
    param_2      NUMERIC(10,3),
    sort_order   INTEGER      NOT NULL DEFAULT 0,
    updated_by   VARCHAR(60),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_locked_rules_enabled CHECK (NOT locked OR enabled)
);

INSERT INTO eligibility_rules (code, name, description, category, enabled, locked, param_1, param_2, sort_order) VALUES
('ACTIVE',    'Handler is active',          'Only active handlers can receive new claims.', 'CORE', TRUE, TRUE, NULL, NULL, 10),
('AVAILABLE', 'Handler is available',       'Handlers marked unavailable (leave, training, etc.) are excluded.', 'CORE', TRUE, TRUE, NULL, NULL, 20),
('EXPERTISE', 'Required expertise',         'Handler must hold the skill for the claim line of business. Multi-line (P&C) handlers hold every skill.', 'CORE', TRUE, TRUE, NULL, NULL, 30),
('CAPACITY',  'Workload capacity',          'Handler must have at least one open slot (current workload below maximum workload).', 'CORE', TRUE, TRUE, NULL, NULL, 40),
('CRITICAL_SEVERITY_EXPERIENCE', 'Critical claims need senior handlers',
    'ASSUMED BUSINESS RESTRICTION - claims of CRITICAL severity require at least param_1 years of experience. Adjust to your organisation''s policy.',
    'BUSINESS', TRUE, FALSE, 5, NULL, 50),
('HIGH_FRAUD_RISK_EXPERIENCE', 'High fraud-risk claims need experienced handlers',
    'ASSUMED BUSINESS RESTRICTION - claims with fraud risk score >= param_1 require at least param_2 years of experience. Adjust to your organisation''s policy.',
    'BUSINESS', TRUE, FALSE, 0.700, 8, 60);

CREATE TABLE ranking_settings (
    id                   BIGSERIAL PRIMARY KEY,
    w_expertise          NUMERIC(5,2) NOT NULL CHECK (w_expertise >= 0),
    w_workload           NUMERIC(5,2) NOT NULL CHECK (w_workload >= 0),
    w_experience         NUMERIC(5,2) NOT NULL CHECK (w_experience >= 0),
    w_success_rate       NUMERIC(5,2) NOT NULL CHECK (w_success_rate >= 0),
    w_similar_claims     NUMERIC(5,2) NOT NULL CHECK (w_similar_claims >= 0),
    w_proximity          NUMERIC(5,2) NOT NULL CHECK (w_proximity >= 0),
    w_ml_suitability     NUMERIC(5,2) NOT NULL CHECK (w_ml_suitability >= 0),
    fairness_threshold   NUMERIC(4,3) NOT NULL DEFAULT 0.850 CHECK (fairness_threshold BETWEEN 0.1 AND 1),
    fairness_penalty     NUMERIC(4,3) NOT NULL DEFAULT 0.150 CHECK (fairness_penalty BETWEEN 0 AND 1),
    use_ml               BOOLEAN      NOT NULL DEFAULT TRUE,
    active               BOOLEAN      NOT NULL DEFAULT FALSE,
    created_by           VARCHAR(60),
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_weights_total_100 CHECK (
        abs((w_expertise + w_workload + w_experience + w_success_rate + w_similar_claims + w_proximity + w_ml_suitability) - 100) < 0.01)
);
-- exactly one active settings row; history is retained (rows are never updated in place except the active flag)
CREATE UNIQUE INDEX uq_ranking_settings_active ON ranking_settings (active) WHERE active;

INSERT INTO ranking_settings (w_expertise, w_workload, w_experience, w_success_rate, w_similar_claims, w_proximity,
                              w_ml_suitability, fairness_threshold, fairness_penalty, use_ml, active, created_by)
VALUES (10, 20, 10, 15, 10, 15, 20, 0.850, 0.150, TRUE, TRUE, 'system');

-- ---------------------------------------------------------------------
-- Internal model metadata (never exposed to the supervisor UI)
-- ---------------------------------------------------------------------
CREATE TABLE model_metadata (
    id              BIGSERIAL PRIMARY KEY,
    model_version   VARCHAR(80)  NOT NULL,
    model_family    VARCHAR(80),
    task            VARCHAR(40)  NOT NULL DEFAULT 'BINARY_CLASSIFICATION',
    target          VARCHAR(80),
    feature_list    TEXT,
    metrics_json    TEXT,
    trained_at      TIMESTAMPTZ,
    first_seen_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_seen_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_model_metadata_version UNIQUE (model_version)
);

-- ---------------------------------------------------------------------
-- Routing: recommendation runs, eligibility results, ranked recommendations
-- ---------------------------------------------------------------------
CREATE TABLE recommendation_runs (
    id                  BIGSERIAL PRIMARY KEY,
    claim_id            BIGINT       NOT NULL REFERENCES claims (id) ON DELETE CASCADE,
    run_type            VARCHAR(20)  NOT NULL CHECK (run_type IN ('ELIGIBILITY_ONLY', 'RECOMMENDATION')),
    outcome             VARCHAR(30)  NOT NULL CHECK (outcome IN ('ELIGIBLE_FOUND', 'NO_ELIGIBLE_HANDLER')),
    eligible_count      INTEGER      NOT NULL,
    ineligible_count    INTEGER      NOT NULL,
    ml_status           VARCHAR(20)  NOT NULL CHECK (ml_status IN ('USED', 'UNAVAILABLE', 'DISABLED', 'NOT_REQUESTED')),
    model_version       VARCHAR(80),
    settings_id         BIGINT       REFERENCES ranking_settings (id),
    weights_json        TEXT,
    rules_json          TEXT,
    fallback_reason     TEXT,
    created_by          VARCHAR(60)  NOT NULL,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_runs_claim ON recommendation_runs (claim_id, created_at DESC);

CREATE TABLE eligibility_results (
    id                  BIGSERIAL PRIMARY KEY,
    run_id              BIGINT       NOT NULL REFERENCES recommendation_runs (id) ON DELETE CASCADE,
    handler_id          BIGINT       NOT NULL REFERENCES handlers (id) ON DELETE CASCADE,
    eligible            BOOLEAN      NOT NULL,
    rule_results_json   TEXT         NOT NULL,
    exclusion_reasons   TEXT,
    CONSTRAINT uq_eligibility_run_handler UNIQUE (run_id, handler_id)
);
CREATE INDEX ix_eligibility_run_eligible ON eligibility_results (run_id, eligible);

CREATE TABLE assignment_recommendations (
    id                  BIGSERIAL PRIMARY KEY,
    run_id              BIGINT       NOT NULL REFERENCES recommendation_runs (id) ON DELETE CASCADE,
    handler_id          BIGINT       NOT NULL REFERENCES handlers (id) ON DELETE CASCADE,
    rank                INTEGER      NOT NULL CHECK (rank >= 1),
    total_score         NUMERIC(6,2) NOT NULL CHECK (total_score BETWEEN 0 AND 100),
    ml_score            NUMERIC(6,5),
    factor_scores_json  TEXT         NOT NULL,
    fairness_adjusted   BOOLEAN      NOT NULL DEFAULT FALSE,
    explanation         TEXT         NOT NULL,
    CONSTRAINT uq_recommendation_run_rank UNIQUE (run_id, rank),
    CONSTRAINT uq_recommendation_run_handler UNIQUE (run_id, handler_id)
);

-- ---------------------------------------------------------------------
-- Assignments (supervisor decisions). Rows are never deleted or overwritten
-- once decided; a reassignment creates a new row.
-- ---------------------------------------------------------------------
CREATE TABLE assignments (
    id                       BIGSERIAL PRIMARY KEY,
    claim_id                 BIGINT       NOT NULL REFERENCES claims (id) ON DELETE RESTRICT,
    run_id                   BIGINT       REFERENCES recommendation_runs (id) ON DELETE SET NULL,
    recommended_handler_id   BIGINT       REFERENCES handlers (id) ON DELETE RESTRICT,
    assigned_handler_id      BIGINT       REFERENCES handlers (id) ON DELETE RESTRICT,
    status                   VARCHAR(20)  NOT NULL CHECK (status IN ('PENDING', 'APPROVED', 'OVERRIDDEN', 'MANUAL_REVIEW', 'CANCELLED')),
    decision_type            VARCHAR(40)  CHECK (decision_type IN ('APPROVED_RECOMMENDATION', 'OVERRIDE',
                                 'MANUAL_REVIEW', 'FALLBACK_MANUAL_REVIEW')),
    recommendation_score     NUMERIC(6,2),
    assigned_handler_score   NUMERIC(6,2),
    explanation              TEXT,
    override_reason          TEXT,
    review_reason            TEXT,
    fallback_reason          TEXT,
    model_version            VARCHAR(80),
    ml_status                VARCHAR(20),
    created_by               VARCHAR(60)  NOT NULL,
    decided_by               VARCHAR(60),
    decided_at               TIMESTAMPTZ,
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_override_reason CHECK (status <> 'OVERRIDDEN' OR (override_reason IS NOT NULL AND length(trim(override_reason)) >= 10)),
    CONSTRAINT ck_assigned_handler CHECK (status NOT IN ('APPROVED', 'OVERRIDDEN') OR assigned_handler_id IS NOT NULL)
);
CREATE INDEX ix_assignments_claim ON assignments (claim_id, created_at DESC);
CREATE INDEX ix_assignments_status ON assignments (status);
CREATE INDEX ix_assignments_assigned_handler ON assignments (assigned_handler_id);
CREATE INDEX ix_assignments_decided_at ON assignments (decided_at DESC);
-- at most one open (PENDING) assignment per claim
CREATE UNIQUE INDEX uq_assignments_one_pending_per_claim ON assignments (claim_id) WHERE status = 'PENDING';

-- ---------------------------------------------------------------------
-- Audit log (append-only; no FKs so history survives deletions)
-- ---------------------------------------------------------------------
CREATE TABLE audit_logs (
    id                          BIGSERIAL PRIMARY KEY,
    event_type                  VARCHAR(40)  NOT NULL,
    entity_type                 VARCHAR(30)  NOT NULL,
    entity_id                   VARCHAR(40),
    claim_number                VARCHAR(30),
    recommended_handler_code    VARCHAR(20),
    final_handler_code          VARCHAR(20),
    recommendation_score        NUMERIC(6,2),
    eligibility_summary         TEXT,
    explanation                 TEXT,
    supervisor_action           VARCHAR(40),
    override_reason             TEXT,
    model_version               VARCHAR(80),
    actor_username              VARCHAR(60)  NOT NULL,
    details_json                TEXT,
    created_at                  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_audit_created_at ON audit_logs (created_at DESC);
CREATE INDEX ix_audit_event_type ON audit_logs (event_type);
CREATE INDEX ix_audit_claim ON audit_logs (claim_number);
CREATE INDEX ix_audit_actor ON audit_logs (actor_username);

CREATE OR REPLACE FUNCTION audit_logs_block_mutation() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit_logs is append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_audit_logs_no_update BEFORE UPDATE OR DELETE ON audit_logs
    FOR EACH ROW EXECUTE FUNCTION audit_logs_block_mutation();

-- ---------------------------------------------------------------------
-- Strategy benchmarking (synthetic-data simulations)
-- ---------------------------------------------------------------------
CREATE TABLE benchmark_runs (
    id                  BIGSERIAL PRIMARY KEY,
    name                VARCHAR(120) NOT NULL,
    claim_sample_size   INTEGER      NOT NULL CHECK (claim_sample_size BETWEEN 1 AND 7000),
    random_seed         BIGINT       NOT NULL,
    claim_filter        VARCHAR(30)  NOT NULL DEFAULT 'UNASSIGNED',
    status              VARCHAR(20)  NOT NULL CHECK (status IN ('RUNNING', 'COMPLETED', 'FAILED')),
    ml_status           VARCHAR(20),
    model_version       VARCHAR(80),
    settings_id         BIGINT       REFERENCES ranking_settings (id),
    error_message       TEXT,
    synthetic_data      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_by          VARCHAR(60)  NOT NULL,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    completed_at        TIMESTAMPTZ
);
CREATE INDEX ix_benchmark_runs_created ON benchmark_runs (created_at DESC);

CREATE TABLE benchmark_results (
    id                          BIGSERIAL PRIMARY KEY,
    run_id                      BIGINT       NOT NULL REFERENCES benchmark_runs (id) ON DELETE CASCADE,
    strategy                    VARCHAR(30)  NOT NULL CHECK (strategy IN ('ROUND_ROBIN', 'WEIGHTED_WORKLOAD', 'SMARTASSIGN')),
    claims_processed            INTEGER      NOT NULL,
    valid_assignments           INTEGER      NOT NULL,
    fallback_count              INTEGER      NOT NULL,
    eligible_assignment_rate    NUMERIC(6,4) NOT NULL,
    avg_suitability_score       NUMERIC(6,2),
    avg_ml_suitability          NUMERIC(6,5),
    same_city_rate              NUMERIC(6,4),
    utilization_std_dev         NUMERIC(6,4),
    assignment_gini             NUMERIC(6,4),
    max_utilization             NUMERIC(6,4),
    over_capacity_handlers      INTEGER      NOT NULL DEFAULT 0,
    handlers_used               INTEGER      NOT NULL DEFAULT 0,
    assignments_per_handler_json TEXT,
    final_workload_json         TEXT,
    CONSTRAINT uq_benchmark_result UNIQUE (run_id, strategy)
);

CREATE TABLE benchmark_assignments (
    id                  BIGSERIAL PRIMARY KEY,
    run_id              BIGINT       NOT NULL REFERENCES benchmark_runs (id) ON DELETE CASCADE,
    strategy            VARCHAR(30)  NOT NULL,
    sequence_no         INTEGER      NOT NULL,
    claim_number        VARCHAR(30)  NOT NULL,
    handler_code        VARCHAR(20),
    outcome             VARCHAR(20)  NOT NULL CHECK (outcome IN ('ASSIGNED', 'FALLBACK')),
    suitability_score   NUMERIC(6,2),
    ml_score            NUMERIC(6,5),
    eligible_count      INTEGER      NOT NULL
);
CREATE INDEX ix_benchmark_assignments_run ON benchmark_assignments (run_id, strategy, sequence_no);
