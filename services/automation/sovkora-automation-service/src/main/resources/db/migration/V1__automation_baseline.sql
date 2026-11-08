-- ============================================================================
-- SOVKORA — V1 baseline · AUTOMATION CONTEXT
-- ----------------------------------------------------------------------------
-- Owner: Automation service (ex worker-integrations). Third-party connector
-- credentials (encrypted), execution audit log, and the service's own dedup
-- ledger — all in its OWN database (orazaka_automation_db) under the service's
-- own role since Phase 3a. user_id columns are OPAQUE ActorIds — no FK into
-- the identity context.
-- ============================================================================

-- Consumer-side idempotency (AGENTS.md §6) — this service's own copy of the
-- dedup ledger, in its own database (contract-copy doctrine, no shared table).
CREATE TABLE processed_messages (
    consumer VARCHAR(100) NOT NULL,
    message_id VARCHAR(100) NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (consumer, message_id)
);
CREATE TABLE connector_credentials (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         VARCHAR(255) NOT NULL,
    connector_type  VARCHAR(50)  NOT NULL,
    credential_key  VARCHAR(255) NOT NULL,
    encrypted_value TEXT         NOT NULL,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_connector_credentials_user_type_key UNIQUE (user_id, connector_type, credential_key)
);
CREATE INDEX idx_connector_credentials_user_type ON connector_credentials(user_id, connector_type);

CREATE TABLE automation_job_execution_log (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id           VARCHAR(255) NOT NULL,
    user_id          VARCHAR(255) NOT NULL,
    connector_type   VARCHAR(50)  NOT NULL,
    action           VARCHAR(255) NOT NULL,
    status           VARCHAR(50)  NOT NULL,
    payload          JSONB,
    result           JSONB,
    error_message    TEXT,
    started_at       TIMESTAMP    NOT NULL DEFAULT NOW(),
    completed_at     TIMESTAMP,
    duration_ms      BIGINT
);
CREATE INDEX idx_job_exec_log_job_id ON automation_job_execution_log(job_id);
CREATE INDEX idx_job_exec_log_user_status ON automation_job_execution_log(user_id, status);
CREATE INDEX idx_job_exec_log_started_at ON automation_job_execution_log(started_at);

-- ── Quartz (JDBC job store of the automation scheduler) ─────────────────────
-- Internal Quartz FKs are same-context and stay.


-- ─────────────────────────────────────────────────────────────────────────────
-- THE AUTOMATION MODEL (ADR-079, slice 2 foundation; sovkora#195)
-- An automation is trigger + recipe + policy; a run is a job with a trace; a side effect needs a grant or an approval;
-- everything that happens is an append-only, hash-chained audit event. Enums are Postgres enums, the words of the
-- limits are rows (`automation_settings`), never constants. user_id / tenant columns are OPAQUE ActorIds — no FK into the
-- identity context. Idempotent: safe to re-apply.
-- ─────────────────────────────────────────────────────────────────────────────
DO $$ BEGIN CREATE TYPE automation_where AS ENUM ('DEVICE', 'CLOUD'); EXCEPTION WHEN duplicate_object THEN NULL; END $$;
DO $$ BEGIN CREATE TYPE automation_trigger_kind AS ENUM ('SCHEDULE', 'FOLDER_WATCH', 'WEBHOOK', 'EMAIL'); EXCEPTION WHEN duplicate_object THEN NULL; END $$;
DO $$ BEGIN CREATE TYPE automation_catch_up AS ENUM ('ONCE', 'SKIP'); EXCEPTION WHEN duplicate_object THEN NULL; END $$;
DO $$ BEGIN CREATE TYPE automation_run_status AS ENUM ('QUEUED', 'RUNNING', 'AWAITING_APPROVAL', 'SUCCEEDED', 'FAILED', 'CANCELLED', 'HALTED', 'SKIPPED_BUDGET'); EXCEPTION WHEN duplicate_object THEN NULL; END $$;
DO $$ BEGIN CREATE TYPE automation_tool_effect AS ENUM ('READ', 'WRITE', 'EXTERNAL', 'EXEC'); EXCEPTION WHEN duplicate_object THEN NULL; END $$;
DO $$ BEGIN CREATE TYPE automation_approval_status AS ENUM ('PENDING', 'APPROVED_ONCE', 'APPROVED_ALWAYS', 'DENIED', 'EXPIRED'); EXCEPTION WHEN duplicate_object THEN NULL; END $$;
DO $$ BEGIN CREATE TYPE automation_device_status AS ENUM ('ACTIVE', 'REVOKED'); EXCEPTION WHEN duplicate_object THEN NULL; END $$;

-- The versioned rule set the limits come from (edited in the admin console, bounded in code). Exactly one is active.
CREATE TABLE IF NOT EXISTS automation_settings (
    version    INT PRIMARY KEY,
    rules      JSONB       NOT NULL,
    active     BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_automation_settings_active ON automation_settings (active) WHERE active;
INSERT INTO automation_settings (version, rules, active) VALUES (1, '{
  "approvalTtlHours": 24,
  "maxRunsPerDay": 20,
  "maxFilesPerRun": 50,
  "maxTokensPerRun": 200000,
  "maxCreditsPerDay": 100,
  "retryMax": 3,
  "retryBackoffSeconds": 60,
  "catchUp": "ONCE"
}'::jsonb, TRUE) ON CONFLICT (version) DO NOTHING;

-- Rule set v2 (sovkora#195, ADR-084): the limits of the cloud automation API. v1's keys are kept, the new ones added;
-- exactly one version is active. Code clamps every value to hard bounds and falls back to a default when a key is absent.
INSERT INTO automation_settings (version, rules, active)
SELECT 2, rules || '{
  "maxAutomationsPerUser": 20,
  "maxInputsBytes": 16384
}'::jsonb, FALSE FROM automation_settings WHERE version = 1
ON CONFLICT (version) DO NOTHING;
UPDATE automation_settings SET active = FALSE
 WHERE version = 1 AND active AND EXISTS (SELECT 1 FROM automation_settings WHERE version = 2);
UPDATE automation_settings SET active = TRUE
 WHERE version = 2 AND NOT EXISTS (SELECT 1 FROM automation_settings WHERE active);

-- A machine that runs automations for a user: revocable token (only its hash is kept), a kill switch the server can set.
CREATE TABLE IF NOT EXISTS device (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      VARCHAR(255) NOT NULL,
    name         VARCHAR(120) NOT NULL,
    platform     VARCHAR(30)  NOT NULL,
    token_hash   VARCHAR(128) NOT NULL,
    status       automation_device_status NOT NULL DEFAULT 'ACTIVE',
    halted       BOOLEAN      NOT NULL DEFAULT FALSE,
    last_seen_at TIMESTAMPTZ,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_device_user ON device (user_id);

CREATE TABLE IF NOT EXISTS automation (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          VARCHAR(255) NOT NULL,
    name             VARCHAR(120) NOT NULL,
    recipe           VARCHAR(80)  NOT NULL,
    runs_where       automation_where NOT NULL DEFAULT 'DEVICE',
    device_id        UUID REFERENCES device(id) ON DELETE SET NULL,
    inputs           JSONB        NOT NULL DEFAULT '{}',
    enabled          BOOLEAN      NOT NULL DEFAULT TRUE,
    dry_run          BOOLEAN      NOT NULL DEFAULT FALSE,
    settings_version INT          NOT NULL REFERENCES automation_settings(version),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now());
CREATE INDEX IF NOT EXISTS idx_automation_user ON automation (user_id);
CREATE INDEX IF NOT EXISTS idx_automation_device ON automation (device_id) WHERE device_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS automation_trigger (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    automation_id UUID NOT NULL REFERENCES automation(id) ON DELETE CASCADE,
    kind          automation_trigger_kind NOT NULL,
    spec          JSONB NOT NULL DEFAULT '{}',
    catch_up      automation_catch_up NOT NULL DEFAULT 'ONCE'
);
CREATE INDEX IF NOT EXISTS idx_automation_trigger_automation ON automation_trigger (automation_id);

-- Per-automation budgets over the rule set's defaults (a missing key means "the rule set's value").
CREATE TABLE IF NOT EXISTS automation_policy (
    automation_id UUID PRIMARY KEY REFERENCES automation(id) ON DELETE CASCADE,
    budgets       JSONB NOT NULL DEFAULT '{}'
);

-- The catalogue of tools and what each one DOES. A tool the catalogue does not know is EXEC, which v1 refuses.
CREATE TABLE IF NOT EXISTS mcp_server (
    server_key     VARCHAR(60) PRIMARY KEY,
    command        TEXT        NOT NULL,
    pinned_version VARCHAR(40) NOT NULL,
    enabled        BOOLEAN     NOT NULL DEFAULT TRUE
);
CREATE TABLE IF NOT EXISTS mcp_tool (
    server_key VARCHAR(60) NOT NULL REFERENCES mcp_server(server_key) ON DELETE CASCADE,
    tool       VARCHAR(80) NOT NULL,
    effect     automation_tool_effect NOT NULL,
    PRIMARY KEY (server_key, tool)
);

-- "Always allow this tool in this folder for this automation": listed, revocable.
CREATE TABLE IF NOT EXISTS tool_grant (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    automation_id UUID NOT NULL REFERENCES automation(id) ON DELETE CASCADE,
    server_key    VARCHAR(60) NOT NULL,
    tool          VARCHAR(80) NOT NULL,
    scope         TEXT        NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    revoked_at    TIMESTAMPTZ
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_tool_grant_active ON tool_grant (automation_id, server_key, tool, scope) WHERE revoked_at IS NULL;

-- A run is an ordinary Sovkora job (job_id, feature key automation.<recipe>) with a trace of steps.
CREATE TABLE IF NOT EXISTS automation_run (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    automation_id UUID NOT NULL REFERENCES automation(id) ON DELETE CASCADE,
    device_id     UUID REFERENCES device(id) ON DELETE SET NULL,
    job_id        VARCHAR(36),
    status        automation_run_status NOT NULL DEFAULT 'QUEUED',
    dry_run       BOOLEAN     NOT NULL DEFAULT FALSE,
    usage         JSONB       NOT NULL DEFAULT '{}',
    started_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    finished_at   TIMESTAMPTZ,
    -- A device pushes a run once even if it retries: its own id for the run.
    device_run_id VARCHAR(80)
);
CREATE INDEX IF NOT EXISTS idx_automation_run_automation ON automation_run (automation_id, started_at DESC);
CREATE UNIQUE INDEX IF NOT EXISTS ux_automation_run_device_run ON automation_run (device_id, device_run_id) WHERE device_run_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS run_step (
    run_id      UUID NOT NULL REFERENCES automation_run(id) ON DELETE CASCADE,
    ordinal     INT  NOT NULL,
    kind        VARCHAR(30) NOT NULL,
    summary     TEXT        NOT NULL,
    duration_ms BIGINT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (run_id, ordinal)
);

CREATE TABLE IF NOT EXISTS approval_request (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    run_id     UUID NOT NULL REFERENCES automation_run(id) ON DELETE CASCADE,
    server_key VARCHAR(60) NOT NULL,
    tool       VARCHAR(80) NOT NULL,
    effect     automation_tool_effect NOT NULL,
    proposal   JSONB       NOT NULL DEFAULT '{}',
    status     automation_approval_status NOT NULL DEFAULT 'PENDING',
    expires_at TIMESTAMPTZ NOT NULL,
    decided_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_approval_not_exec CHECK (effect <> 'EXEC')
);
CREATE INDEX IF NOT EXISTS idx_approval_pending ON approval_request (expires_at) WHERE status = 'PENDING';

-- The audit trail: append-only (no update, no delete, whoever the role) and hash-chained per device — each event
-- carries the hash of the one before it. The device computes the hash (SHA-256 over its own canonical form of the event)
-- and the app verifies the chain end to end; the database refuses a gap, a repeat or a broken link, so an event can
-- never be slipped in or taken out afterwards.
CREATE TABLE IF NOT EXISTS audit_event (
    device_id  UUID        NOT NULL REFERENCES device(id),   -- a device with a trail is revoked, never deleted
    seq        BIGINT      NOT NULL,
    kind       VARCHAR(40) NOT NULL,
    detail     JSONB       NOT NULL DEFAULT '{}',
    prev_hash  CHAR(64)    NOT NULL,
    hash       CHAR(64)    NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (device_id, seq),
    CONSTRAINT ck_audit_hash_hex CHECK (hash ~ '^[0-9a-f]{64}$' AND prev_hash ~ '^[0-9a-f]{64}$')
);

CREATE OR REPLACE FUNCTION audit_event_chain() RETURNS trigger AS $$
DECLARE
    last_seq  BIGINT;
    last_hash CHAR(64);
BEGIN
    -- one writer per device at a time, so "the last event" cannot change under this check
    PERFORM pg_advisory_xact_lock(hashtextextended('audit-event:' || NEW.device_id::text, 0));
    SELECT seq, hash INTO last_seq, last_hash FROM audit_event WHERE device_id = NEW.device_id ORDER BY seq DESC LIMIT 1;
    IF last_seq IS NULL THEN
        IF NEW.seq <> 1 OR NEW.prev_hash <> repeat('0', 64) THEN
            RAISE EXCEPTION 'audit chain: the first event of a device is #1 after the all-zero hash' USING ERRCODE = 'check_violation';
        END IF;
    ELSIF NEW.seq <> last_seq + 1 OR NEW.prev_hash <> last_hash THEN
        RAISE EXCEPTION 'audit chain: event #% must follow #% and carry its hash', NEW.seq, last_seq USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END $$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION audit_event_immutable() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit_event is append-only' USING ERRCODE = 'insufficient_privilege';
END $$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_audit_event_chain ON audit_event;
CREATE TRIGGER trg_audit_event_chain BEFORE INSERT ON audit_event FOR EACH ROW EXECUTE FUNCTION audit_event_chain();
DROP TRIGGER IF EXISTS trg_audit_event_immutable ON audit_event;
CREATE TRIGGER trg_audit_event_immutable BEFORE UPDATE OR DELETE ON audit_event FOR EACH ROW EXECUTE FUNCTION audit_event_immutable();

-- ─────────────────────────────────────────────────────────────────────────────
-- TRIGGERS (ADR-084; sovkora#195, #196). A webhook trigger's secret is stored SEALED under the master keyring
-- (v1.<keyId>.<wrapped>.<nonce>.<ciphertext>), never in clear; `secret_hint` is its last four characters, and the previous
-- secret keeps verifying until `previous_secret_until` (rotation without losing a call). A schedule trigger keeps its slot
-- in `spec` ({"time":"08:00","zone":"America/Toronto","days":["MON",…]}) and its next slot in `next_fire_at`.
-- Idempotent: safe to re-apply.
-- ─────────────────────────────────────────────────────────────────────────────
ALTER TABLE automation_trigger ADD COLUMN IF NOT EXISTS enabled                BOOLEAN     NOT NULL DEFAULT TRUE;
ALTER TABLE automation_trigger ADD COLUMN IF NOT EXISTS secret_sealed          TEXT;
ALTER TABLE automation_trigger ADD COLUMN IF NOT EXISTS secret_hint            VARCHAR(8);
ALTER TABLE automation_trigger ADD COLUMN IF NOT EXISTS previous_secret_sealed TEXT;
ALTER TABLE automation_trigger ADD COLUMN IF NOT EXISTS previous_secret_until  TIMESTAMPTZ;
ALTER TABLE automation_trigger ADD COLUMN IF NOT EXISTS next_fire_at           TIMESTAMPTZ;
ALTER TABLE automation_trigger ADD COLUMN IF NOT EXISTS last_fired_at          TIMESTAMPTZ;
ALTER TABLE automation_trigger ADD COLUMN IF NOT EXISTS created_at             TIMESTAMPTZ NOT NULL DEFAULT now();
DO $$ BEGIN
    ALTER TABLE automation_trigger ADD CONSTRAINT ck_trigger_webhook_secret
        CHECK (kind <> 'WEBHOOK' OR secret_sealed IS NOT NULL);
EXCEPTION WHEN duplicate_object THEN NULL; END $$;
CREATE INDEX IF NOT EXISTS idx_automation_trigger_due ON automation_trigger (next_fire_at)
    WHERE kind = 'SCHEDULE' AND enabled;

-- The limits of triggers, as rows of the active rule set (v2). A value already set is kept.
UPDATE automation_settings SET rules = '{"maxTriggersPerAutomation": 5, "webhookSecretGraceHours": 24}'::jsonb || rules
 WHERE version = 2;

-- ─────────────────────────────────────────────────────────────────────────────
-- SIGNED WEBHOOK → RUN (ADR-084 §2, §3). `webhook_delivery` is the replay guard: every webhook-id accepted for a trigger,
-- its PRIMARY KEY (trigger_id, webhook_id) is what makes a reused id fail, whoever the instance. The run row gains who
-- started it, the Studio run it is, why it failed, and the webhook body it was started with (a JSON object, capped).
-- Idempotent: safe to re-apply.
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS webhook_delivery (
    trigger_id  UUID         NOT NULL REFERENCES automation_trigger(id) ON DELETE CASCADE,
    webhook_id  VARCHAR(128) NOT NULL,
    run_id      UUID         NOT NULL,
    received_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (trigger_id, webhook_id)
);
CREATE INDEX IF NOT EXISTS idx_webhook_delivery_received ON webhook_delivery (received_at);

ALTER TABLE automation_run ADD COLUMN IF NOT EXISTS trigger_id    UUID REFERENCES automation_trigger(id) ON DELETE SET NULL;
ALTER TABLE automation_run ADD COLUMN IF NOT EXISTS trigger_kind  automation_trigger_kind;
ALTER TABLE automation_run ADD COLUMN IF NOT EXISTS studio_run_id UUID;
ALTER TABLE automation_run ADD COLUMN IF NOT EXISTS error_code    VARCHAR(60);
ALTER TABLE automation_run ADD COLUMN IF NOT EXISTS error_message VARCHAR(500);
ALTER TABLE automation_run ADD COLUMN IF NOT EXISTS webhook_id    VARCHAR(128);
ALTER TABLE automation_run ADD COLUMN IF NOT EXISTS payload       JSONB;
ALTER TABLE automation_run ADD COLUMN IF NOT EXISTS payload_bytes INT;
CREATE UNIQUE INDEX IF NOT EXISTS ux_automation_run_studio ON automation_run (studio_run_id) WHERE studio_run_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_automation_run_status ON automation_run (status, started_at) WHERE status IN ('QUEUED', 'RUNNING');

-- The limits of the webhook and of the run sweep, as rows of the active rule set (v2). A value already set is kept.
UPDATE automation_settings SET rules = '{
  "webhookBodyMaxBytes": 65536,
  "webhookToleranceSeconds": 300,
  "webhookGlobalPerMinute": 3000,
  "webhookPreverifyPerMinute": 600,
  "webhookRunsPerMinute": 60,
  "webhookReplayRetentionHours": 24,
  "runQueuedStaleSeconds": 30,
  "runReconcileAfterSeconds": 120
}'::jsonb || rules WHERE version = 2;

-- The late tolerance of a schedule slot (seconds before the catch-up policy decides), a row of the active rule set (v2).
UPDATE automation_settings SET rules = '{"scheduleLateToleranceSeconds": 120}'::jsonb || rules WHERE version = 2;
