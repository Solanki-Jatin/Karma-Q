CREATE TABLE jobs (
    id              UUID PRIMARY KEY,
    type            VARCHAR(255)    NOT NULL,
    payload         TEXT,
    status          VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    run_at          TIMESTAMPTZ     NOT NULL,
    cron_expression VARCHAR(120),
    idempotency_key VARCHAR(255)    UNIQUE,
    attempt_count   INT             NOT NULL DEFAULT 0,
    max_attempts    INT             NOT NULL DEFAULT 5,
    locked_at       TIMESTAMPTZ,
    locked_by       VARCHAR(255),
    last_error      TEXT,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ
);

-- Supports the worker claim query: find PENDING jobs due now, or IN_PROGRESS
-- jobs whose lock has expired (crash recovery), ordered by run_at.
CREATE INDEX idx_jobs_claimable ON jobs (status, run_at);

-- Fast status lookups for the REST API (GET /jobs/{id}, filtering by status).
CREATE INDEX idx_jobs_status ON jobs (status);
