-- The semantic cache of the AI engine (ai-engine#40): answers kept per tenant, scope guard and
-- model tier, found by exact key or by nearest embedding. Idempotent. Needs the pgvector extension,
-- created by a superuser (it is not a trusted extension): CREATE EXTENSION IF NOT EXISTS vector;
-- The vector size is the embedding model's (bge-m3 = 1024, as the knowledge service): another
-- model is a migration, not a setting.
CREATE TABLE IF NOT EXISTS semantic_cache_entry (
    id               BIGSERIAL    PRIMARY KEY,
    tenant_id        VARCHAR(255) NOT NULL,
    scope_guard_hash CHAR(64)     NOT NULL,
    model_tier       VARCHAR(100) NOT NULL,
    exact_key        CHAR(64)     NOT NULL,
    embedding        vector(1024),                 -- NULL for an entry kept for the exact tier only
    signature        TEXT         NOT NULL,        -- the turn's numbers and negations (TurnSignature)
    response         TEXT         NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    expires_at       TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_semantic_cache_key UNIQUE (tenant_id, scope_guard_hash, model_tier, exact_key)
);
CREATE INDEX IF NOT EXISTS idx_semantic_cache_expiry ON semantic_cache_entry (expires_at);
CREATE INDEX IF NOT EXISTS idx_semantic_cache_embedding
    ON semantic_cache_entry USING hnsw (embedding vector_cosine_ops);
