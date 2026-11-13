-- A silent step is re-issued from the database, then failed with a typed cause
-- (krizaka-internal#2132, ADR SOV-105 §2).
--
-- The step row carries what the guard needs and a restart must not lose: how many times its
-- command was sent again, and when. The ceiling of the step's lane is measured from the later of
-- the dispatch and the last re-issue, so every re-issue gets a full ceiling to be answered in.
ALTER TABLE studio_run_step
    ADD COLUMN reissues    INT NOT NULL DEFAULT 0,
    ADD COLUMN reissued_at TIMESTAMPTZ,
    ADD CONSTRAINT ck_studio_run_step_reissues CHECK (reissues >= 0);

-- The sweeper finds a command by the job it carries, newest first.
CREATE INDEX idx_studio_outbox_message ON studio_outbox(message_id, created_at DESC)
    WHERE message_id IS NOT NULL;

-- The most times one step's command is sent again before the run fails. A row, like the ceilings;
-- 0 turns the guard off (the step then fails at its ceiling, as before), 5 is the most a lost
-- broker is worth.
INSERT INTO studio_runtime_config
    (config_key, config_value, value_type, description, default_value, min_value, max_value, max_items)
VALUES
 ('run.step-max-reissues', '2', 'int', 'Times a step that has not answered by its lane ceiling is sent again from the database, with the same job id, before its run fails PLATFORM_UNAVAILABLE. 0 = never re-issue.', '2', 0, 5, NULL)
ON CONFLICT (config_key) DO UPDATE SET
    default_value = EXCLUDED.default_value,
    min_value = EXCLUDED.min_value,
    max_value = EXCLUDED.max_value,
    max_items = EXCLUDED.max_items;
