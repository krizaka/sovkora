-- krizaka-restricted#279: a run's input files are purged at its terminal state when its retention policy row says so.
--
-- The retention window (retention.run-days, retention.sensitive-run-days) deletes a whole run long after it ended: a
-- SENSITIVE run, with the file it was given, lived 30 days. A document check needs the file only while it runs. What
-- it leaves (run id, status, counters, the input fingerprint, the findings in its outputs) is what the person and the
-- trail need afterwards; the file is not.
--
-- The policy is a row, never code: the engine knows the mechanism, the row names the Studio and the input fields that
-- carry a file (declared, never deduced from a field's name or shape, docs/agents.md §12).

-- One row per (tenant, Studio). tenant_id is the run's actor_id (the opaque billable subject, as content_policy_tenant
-- .subject), or '*' for every tenant. The strictest applicable row wins: a tenant's KEEP never lifts a '*' purge,
-- because a retention setting may only ever shorten what is kept (ADR-051, as retentionDays on an installation).
-- studio_key has no FK: Studios are imported from pack bundles after the schema exists, and a row naming a Studio not
-- yet imported simply waits for it.
CREATE TABLE studio_run_retention_policy (
    tenant_id    VARCHAR(255) NOT NULL DEFAULT '*',
    studio_key   VARCHAR(60)  NOT NULL,
    -- KEEP: the inputs live as long as the run (today's behaviour: the retention window deletes the run).
    -- PURGE_AT_TERMINAL: the named input fields are removed when the run reaches SUCCEEDED, FAILED or CANCELLED.
    inputs_mode  VARCHAR(30)  NOT NULL,
    -- The names of the run inputs that carry a file, e.g. ["documentBase64"]. Names only.
    input_fields JSONB        NOT NULL DEFAULT '[]',
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, studio_key),
    CONSTRAINT ck_run_retention_mode   CHECK (inputs_mode IN ('KEEP', 'PURGE_AT_TERMINAL')),
    CONSTRAINT ck_run_retention_fields CHECK (jsonb_typeof(input_fields) = 'array'),
    CONSTRAINT ck_run_retention_purge_names_fields
        CHECK (inputs_mode <> 'PURGE_AT_TERMINAL' OR jsonb_array_length(input_fields) > 0)
);
CREATE INDEX idx_run_retention_studio ON studio_run_retention_policy(studio_key);

-- When the run's input files were purged; NULL while they are kept. Makes the purge idempotent and lets the sweeper,
-- the safety net, find a finished run the terminal transition did not purge.
ALTER TABLE studio_run ADD COLUMN inputs_purged_at TIMESTAMPTZ;
CREATE INDEX idx_studio_run_purge_pending ON studio_run(studio_key, actor_id)
    WHERE finished_at IS NOT NULL AND inputs_purged_at IS NULL;

-- The trail of each purge: which run, whose, which Studio, which fields (names, never content). Append-only like
-- studio_run_audit, and kept when retention deletes the run (no FK), so a purge can be shown to have happened.
CREATE TABLE studio_run_purge_audit (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    run_id      UUID         NOT NULL,
    tenant_id   VARCHAR(255) NOT NULL,
    studio_key  VARCHAR(60)  NOT NULL,
    event       VARCHAR(40)  NOT NULL,
    fields      JSONB        NOT NULL,
    recorded_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_run_purge_audit_event CHECK (event IN ('run.inputs_purged'))
);
CREATE INDEX idx_run_purge_audit_run    ON studio_run_purge_audit(run_id);
CREATE INDEX idx_run_purge_audit_tenant ON studio_run_purge_audit(tenant_id, recorded_at DESC);

CREATE OR REPLACE FUNCTION studio_run_purge_audit_immutable() RETURNS TRIGGER AS $$
BEGIN
  RAISE EXCEPTION 'studio_run_purge_audit is append-only (krizaka-restricted#279) — % rejected', TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_studio_run_purge_audit_immutable
  BEFORE UPDATE OR DELETE ON studio_run_purge_audit
  FOR EACH ROW EXECUTE FUNCTION studio_run_purge_audit_immutable();

-- The document check: the file is needed while the check runs, never after (Loi 25 minimisation). Its findings and
-- counters stay with the run.
INSERT INTO studio_run_retention_policy (tenant_id, studio_key, inputs_mode, input_fields)
VALUES ('*', 'document-authenticity', 'PURGE_AT_TERMINAL', '["documentBase64"]')
ON CONFLICT (tenant_id, studio_key) DO NOTHING;

-- The most finished runs one sweep of the safety net purges (RunInputPurgeSweeper, every minute). A row, like the ceilings.
INSERT INTO studio_runtime_config
    (config_key, config_value, value_type, description, default_value, min_value, max_value, max_items)
VALUES
 ('run.input-purge.batch-size', '100', 'int', 'Finished runs whose input files a retention policy row purges at terminal state and that a terminal path left unpurged: how many one sweep purges (krizaka-restricted#279).', '100', 1, 1000, NULL)
ON CONFLICT (config_key) DO NOTHING;
