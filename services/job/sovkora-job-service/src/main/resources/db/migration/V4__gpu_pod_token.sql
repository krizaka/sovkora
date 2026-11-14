-- A GPU pod's own job service token (krizaka-internal#3645): one row per token the job service minted.
-- A pod token is accepted only while its row is live (not revoked, not expired), so a token this table never
-- recorded is refused, and `gpu-dev.sh runpod stop` (or the sweep, for a pod its watchdog ended) revokes it
-- before its expiry. Rows are purged a day after they expire: the table holds at most a few days of pods.
CREATE TABLE gpu_pod_tokens (
    jti        UUID PRIMARY KEY,
    pod_key    VARCHAR(21) NOT NULL CONSTRAINT ck_gpu_pod_tokens_key CHECK (pod_key ~ '^[0-9]{14}-[0-9a-f]{6}$'),
    issued_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ck_gpu_pod_tokens_lifetime CHECK (expires_at > issued_at)
);

-- One live token per pod: a second mint for the same pod replaces the first one's id in its row (ON CONFLICT).
CREATE UNIQUE INDEX uq_gpu_pod_tokens_live ON gpu_pod_tokens(pod_key) WHERE revoked_at IS NULL;
-- What the purge reads.
CREATE INDEX idx_gpu_pod_tokens_expires ON gpu_pod_tokens(expires_at);
