-- What Hibernate's generated schema lacks of the job-service law tables
-- (sovkora-job-service infra/initdb/30-jobs-config.sql). Keep in step with it.

-- At most one open version of an article: the importer closes the old row before it writes the new one.
CREATE UNIQUE INDEX IF NOT EXISTS uq_law_article_version_open
    ON law_article_version (instrument_id, ref, language) WHERE in_force_to IS NULL;

DO $do$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_law_article_version_span') THEN
        ALTER TABLE law_article_version ADD CONSTRAINT ck_law_article_version_span
            CHECK (in_force_to IS NULL OR in_force_to >= in_force_from);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_law_import_manual_traceable') THEN
        ALTER TABLE law_import ADD CONSTRAINT ck_law_import_manual_traceable
            CHECK (mode = 'AUTOMATED' OR (imported_by IS NOT NULL AND dataset_sha256 IS NOT NULL));
    END IF;
END
$do$;

-- rule_proposal (ADR-089 §3): the statuses and the reviewed rows, as the job-service declares them.
DO $do$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_rule_proposal_status') THEN
        ALTER TABLE rule_proposal ADD CONSTRAINT ck_rule_proposal_status CHECK (status IN
            ('PROPOSED','REJECTED_AUTO','APPROVED','REJECTED','PROMOTED','SUPERSEDED'));
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_rule_proposal_reviewed') THEN
        ALTER TABLE rule_proposal ADD CONSTRAINT ck_rule_proposal_reviewed CHECK (
            status NOT IN ('APPROVED','REJECTED','PROMOTED') OR (reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL));
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_rule_proposal_revision') THEN
        ALTER TABLE rule_proposal ADD CONSTRAINT ck_rule_proposal_revision CHECK (proposal_revision >= 1);
    END IF;
END
$do$;
