-- ============================================================================
-- The document readers' door (krizaka-internal#4198): the routing as data, and one measurement per page read.
-- ----------------------------------------------------------------------------
-- POST /api/v1/document-reads reads document_reader_setting at each call and sends it to the reader worker
-- (workers/reader), which checks every value with the bounds of document_reader/routing.py. Switching the
-- default reader of a kind is an UPDATE of one row: no product deploy, no worker restart.
-- ============================================================================

-- One row per routing key (the keys of workers/reader/routing.yaml, the worker's own seed). The value is checked
-- by the worker on every call; a value out of its bounds answers ROUTING_INVALID, named by its key.
CREATE TABLE document_reader_setting (
    setting_key   VARCHAR(80)  PRIMARY KEY,
    setting_value VARCHAR(200) NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by    VARCHAR(255) NOT NULL DEFAULT 'seed',
    CONSTRAINT document_reader_setting_key_shape CHECK (setting_key ~ '^reader\.[a-z0-9.-]{1,73}$')
);

-- The seed: the values of routing.yaml. The default per kind is chosen by the measurement (krizaka-internal#4060).
INSERT INTO document_reader_setting (setting_key, setting_value) VALUES
    ('reader.default.pdf-scan',   'paddleocr'),
    ('reader.default.screenshot', 'paddleocr'),
    ('reader.default.photo',      'paddleocr'),
    ('reader.fallback',           'tesseract'),
    -- A generative reader (a vision-language model) is the last resort: off, and off in production.
    ('reader.vlm.enabled',        'false'),
    ('reader.page-budget',        '12'),
    ('reader.timeout-ms',         '30000'),
    -- AWS's list price of DetectDocumentText, 0.0015 USD a page (US West); the Montréal price is confirmed when
    -- the account exists. The local readers cost the host they run on: 0 until #4060 measures it.
    ('reader.cost-micros.textract', '1500');

-- Every change of a setting, appended by a trigger: who changed which key, from what, to what, and when.
CREATE TABLE document_reader_setting_history (
    id            BIGSERIAL    PRIMARY KEY,
    setting_key   VARCHAR(80)  NOT NULL,
    old_value     VARCHAR(200),
    new_value     VARCHAR(200),
    changed_by    VARCHAR(255) NOT NULL,
    changed_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_document_reader_setting_history_key ON document_reader_setting_history (setting_key, changed_at DESC);

CREATE FUNCTION document_reader_setting_audit() RETURNS trigger LANGUAGE plpgsql AS $fn$
BEGIN
    IF TG_OP = 'DELETE' THEN
        INSERT INTO document_reader_setting_history (setting_key, old_value, new_value, changed_by)
        VALUES (OLD.setting_key, OLD.setting_value, NULL, OLD.updated_by);
        RETURN OLD;
    END IF;
    INSERT INTO document_reader_setting_history (setting_key, old_value, new_value, changed_by)
    VALUES (NEW.setting_key, CASE WHEN TG_OP = 'UPDATE' THEN OLD.setting_value END, NEW.setting_value, NEW.updated_by);
    RETURN NEW;
END;
$fn$;
CREATE TRIGGER trg_document_reader_setting_audit AFTER INSERT OR UPDATE OR DELETE ON document_reader_setting
    FOR EACH ROW EXECUTE FUNCTION document_reader_setting_audit();

-- One line per page read through the door: who asked (tenant, actor: opaque ids), which reader answered in which
-- version, what it cost and how long it took. Never a word of the page, nor its hash.
CREATE TABLE document_read_measurement (
    id             BIGSERIAL    PRIMARY KEY,
    at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    tenant_id      VARCHAR(100) NOT NULL,
    actor_id       VARCHAR(255) NOT NULL,
    page_kind      VARCHAR(20)  NOT NULL CHECK (page_kind IN ('pdf-scan', 'screenshot', 'photo')),
    reader         VARCHAR(40)  NOT NULL,
    reader_version VARCHAR(80)  NOT NULL,
    cost_micros    BIGINT       NOT NULL CHECK (cost_micros >= 0),
    latency_ms     BIGINT       NOT NULL CHECK (latency_ms >= 0),
    fallback_from  VARCHAR(40)
);
CREATE INDEX idx_document_read_measurement_tenant_at ON document_read_measurement (tenant_id, at DESC);

CREATE FUNCTION document_read_measurement_append_only() RETURNS trigger LANGUAGE plpgsql AS $fn$
BEGIN
    RAISE EXCEPTION 'document_read_measurement is append-only: a line is never rewritten';
END;
$fn$;
CREATE TRIGGER trg_document_read_measurement_append_only BEFORE UPDATE ON document_read_measurement
    FOR EACH ROW EXECUTE FUNCTION document_read_measurement_append_only();
