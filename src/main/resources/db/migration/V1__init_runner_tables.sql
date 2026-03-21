-- V1__init_runner_tables.sql — initial Flyway baseline (public release schema).

-- =========================
-- ENUM TYPES
-- =========================

CREATE TYPE job_status AS ENUM (
    'QUEUED',
    'RUNNING',
    'AWAITING_INPUT',
    'PAUSED',
    'FAILED',
    'SUCCESS',
    'CANCELLED'
);

CREATE TYPE approval_state AS ENUM (
    'PENDING',
    'APPROVED',
    'REJECTED'
);

CREATE TYPE risk_level AS ENUM (
    'LOW',
    'MEDIUM',
    'HIGH'
);

CREATE TYPE job_log_source AS ENUM (
    'BACKEND',
    'AGENT'
);

CREATE TYPE plan_state AS ENUM (
    'PLANNING',
    'AWAITING_INPUT',
    'PLAN_READY',
    'PAUSED',
    'APPROVED',
    'REJECTED',
    'CANCELLED'
);

CREATE TYPE todo_status AS ENUM (
    'OPEN',
    'IN_PROGRESS',
    'DONE',
    'CANCELLED'
);

CREATE TYPE todo_source AS ENUM (
    'WEB',
    'TELEGRAM'
);

-- =========================
-- JOBS TABLE
-- =========================

CREATE TABLE jobs (
    id BIGSERIAL PRIMARY KEY,

    requester_user_id BIGINT NOT NULL,
    requester_username VARCHAR(255),
    requester_chat_id BIGINT NOT NULL,

    intent TEXT NOT NULL,
    task_type VARCHAR(64) NOT NULL,

    risk_level risk_level NOT NULL,
    approval_state approval_state NOT NULL DEFAULT 'PENDING',
    status job_status NOT NULL DEFAULT 'QUEUED',

    target_executor VARCHAR(64) NOT NULL DEFAULT 'mac_mini',
    executor_id VARCHAR(128),

    task_payload_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    result_json JSONB,

    attempt INT NOT NULL DEFAULT 1,
    max_attempts INT NOT NULL DEFAULT 3,
    last_error TEXT,

    approved_by VARCHAR(255),

    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_jobs_attempts_valid
        CHECK (attempt >= 1 AND max_attempts >= attempt),

    CONSTRAINT chk_jobs_time_order
        CHECK (
            started_at IS NULL
            OR finished_at IS NULL
            OR started_at <= finished_at
        )
);

-- =========================
-- JOB LOGS
-- =========================

CREATE TABLE job_logs (
    id BIGSERIAL PRIMARY KEY,

    job_id BIGINT NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,

    seq INTEGER NOT NULL,
    level VARCHAR(16) NOT NULL DEFAULT 'INFO',
    source job_log_source NOT NULL DEFAULT 'BACKEND',

    line TEXT NOT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_job_logs_job_seq UNIQUE(job_id, seq),
    CONSTRAINT chk_job_logs_seq_positive CHECK (seq > 0)
);

-- =========================
-- JOB EVENTS
-- =========================

CREATE TABLE job_events (
    id BIGSERIAL PRIMARY KEY,

    job_id BIGINT NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,

    event_type VARCHAR(128) NOT NULL,
    data_json JSONB,

    correlation_id UUID,

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- =========================
-- PLAN SESSIONS (PLAN_TASK jobs)
-- =========================

CREATE TABLE plan_sessions (
    id          BIGSERIAL PRIMARY KEY,
    job_id      BIGINT       NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,
    cli_session_id VARCHAR(255),
    state       plan_state   NOT NULL DEFAULT 'PLANNING',
    plan_text   TEXT,
    round       INT          NOT NULL DEFAULT 1,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_plan_sessions_job UNIQUE (job_id)
);

CREATE TABLE plan_questions (
    id               BIGSERIAL PRIMARY KEY,
    plan_session_id  BIGINT       NOT NULL REFERENCES plan_sessions(id) ON DELETE CASCADE,
    round            INT          NOT NULL,
    seq              INT          NOT NULL,
    question_text    TEXT         NOT NULL,
    options          JSONB,
    answer           TEXT,
    answered_at      TIMESTAMPTZ,

    CONSTRAINT uq_plan_questions_session_round_seq UNIQUE (plan_session_id, round, seq),
    CONSTRAINT chk_plan_questions_seq_positive CHECK (seq > 0)
);

-- =========================
-- TODOS
-- =========================

CREATE TABLE todos (
    id              BIGSERIAL    PRIMARY KEY,

    title           VARCHAR(500) NOT NULL,
    description     TEXT,

    status          todo_status  NOT NULL DEFAULT 'OPEN',
    source          todo_source  NOT NULL,

    workspace       VARCHAR(1000),
    linked_job_id   BIGINT       REFERENCES jobs(id) ON DELETE SET NULL,

    requester_user_id  BIGINT    NOT NULL,
    requester_username VARCHAR(255),
    requester_chat_id  BIGINT    NOT NULL,

    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- =========================
-- INDEXES
-- =========================

CREATE INDEX idx_jobs_status ON jobs(status);
CREATE INDEX idx_jobs_created_at ON jobs(created_at DESC);
CREATE INDEX idx_jobs_status_created ON jobs(status, created_at DESC);

CREATE INDEX idx_job_logs_job_seq ON job_logs(job_id, seq DESC);

CREATE INDEX idx_job_events_job_created ON job_events(job_id, created_at DESC);
CREATE INDEX idx_job_events_correlation ON job_events(correlation_id);

CREATE INDEX idx_plan_sessions_state ON plan_sessions(state);
CREATE INDEX idx_plan_sessions_job ON plan_sessions(job_id);
CREATE INDEX idx_plan_questions_session ON plan_questions(plan_session_id, round, seq);

CREATE INDEX idx_todos_status ON todos(status);
CREATE INDEX idx_todos_created_at ON todos(created_at DESC);
CREATE INDEX idx_todos_linked_job ON todos(linked_job_id) WHERE linked_job_id IS NOT NULL;
