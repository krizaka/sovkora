-- A job claimed by a worker carries the worker's lease (krizaka-internal#669).
-- The executor renews the lease on a heartbeat while it runs the job; a sweeper re-enqueues, then
-- fails, a PROCESSING job whose lease expired (the worker died: out of memory, eviction, hard kill).
-- Nothing here holds the job's material: the hold and the model are what re-publishing the command
-- and releasing the credit need, and the row's own payload carries the rest.
ALTER TABLE orazaka_jobs
    ADD COLUMN locked_by_worker VARCHAR(255),
    ADD COLUMN lease_expires_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN retry_count INT NOT NULL DEFAULT 0 CONSTRAINT ck_orazaka_jobs_retry_count CHECK (retry_count >= 0),
    ADD COLUMN hold_id VARCHAR(255),
    ADD COLUMN model VARCHAR(255);

-- What the recovery sweep reads, and only that: jobs a worker is running.
CREATE INDEX idx_orazaka_jobs_leased ON orazaka_jobs(lease_expires_at) WHERE status = 'PROCESSING';
