-- ============================================================================
-- SOVKORA — V1 baseline · KNOWLEDGE CONTEXT (RAG sources)
-- ----------------------------------------------------------------------------
-- Owner: Knowledge service — RAG retrieval + tool source lookup + async indexing,
-- in its OWN database (orazaka_knowledge_db) under its own role since Phase 4.
-- MCP registry + tool cache/config are router-hosted (MCP execution) and live in
-- 30-jobs-config.sql; only the RAG *sources* the knowledge service owns are here.
-- The pgvector extension is created by the bootstrap (doadmin / local initdb), never here; V1 only asserts it.
-- user_id columns are OPAQUE ActorIds — no FK into the identity context.
-- ============================================================================

-- pgvector is NOT a 'trusted' extension: it is created by the bootstrap as an admin (local compose: infra/initdb/40-knowledge.sql;
-- dev and prod: the provisioning). This migration only asserts it, so a missing bootstrap fails loudly (krizaka-internal#2255).
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'vector') THEN
        RAISE EXCEPTION 'extension vector is missing: the database bootstrap must create it as an admin before the first migration';
    END IF;
END
$$;

-- Consumer-side idempotency (AGENTS.md §6) — this service's own dedup ledger.
CREATE TABLE processed_messages (
    consumer VARCHAR(100) NOT NULL,
    message_id VARCHAR(100) NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (consumer, message_id)
);

CREATE TABLE orazaka_tools_rag_source (
    id SERIAL PRIMARY KEY,
    tool_id VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    metadata TEXT,
    ingested BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    user_id VARCHAR(255),
    -- ACCESS (sovkora-engine#107, ADR-091). A source belongs to one tenant ('*' = the platform's own
    -- content, written by operators only) and is readable by its owner (user_id) and by the principals
    -- listed here, written 'role:<ROLE>', 'group:<GROUP>' or 'user:<id>'. No tenant, no row: the column
    -- has no default, so an insert that forgets it fails instead of being readable by everyone.
    tenant_id VARCHAR(64) NOT NULL,
    acl_principals TEXT[] NOT NULL DEFAULT '{}',
    CONSTRAINT unique_tool_content UNIQUE (tenant_id, tool_id, content)
);
CREATE INDEX idx_tools_rag_source_user ON orazaka_tools_rag_source(user_id);
CREATE INDEX idx_tools_rag_source_tenant ON orazaka_tools_rag_source(tenant_id, tool_id);

-- Every refused read: who (the verified principal), which resource, why. Never the query or the content (ADR-064).
CREATE TABLE knowledge_access_audit (
    id BIGSERIAL PRIMARY KEY,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    tenant_id VARCHAR(64),
    subject VARCHAR(255),
    action VARCHAR(32) NOT NULL,
    resource_kind VARCHAR(32) NOT NULL,
    resource_id VARCHAR(255) NOT NULL,
    reason VARCHAR(64) NOT NULL
);
CREATE INDEX idx_knowledge_access_audit_time ON knowledge_access_audit(occurred_at);

-- ============================================================================
-- RECOMMENDATIONS (ADR-078) — a two-stage recommender served to a product's
-- service account. Every row carries tenant_id (the account's user id, an opaque
-- ActorId); users are the product's PSEUDONYMS, items and creators its public ids.
-- Titles and descriptions are embedded, never stored. Kept identical to the
-- incremental script infra/migrations/2026-10-15-recommendations.sql (workspace).
-- ============================================================================
CREATE TYPE reco_event_kind AS ENUM
    ('VIEW', 'LIKE', 'SAVE', 'SHARE', 'TIP', 'UNLOCK', 'FOLLOW', 'UNFOLLOW', 'SKIP', 'REPORT');

-- The parameters, versioned: a new version is a new row with its reason; the newest
-- row of the tenant wins, else the newest of '*' (every tenant).
CREATE TABLE reco_settings (
    tenant_id VARCHAR(64) NOT NULL,
    version INTEGER NOT NULL CHECK (version >= 1),
    settings JSONB NOT NULL,
    reason TEXT NOT NULL,
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (tenant_id, version)
);

CREATE TABLE reco_item (
    tenant_id VARCHAR(64) NOT NULL,
    item_id VARCHAR(64) NOT NULL,
    creator_id VARCHAR(64) NOT NULL,
    tags TEXT[] NOT NULL DEFAULT '{}',
    language VARCHAR(35),
    duration_seconds INTEGER,
    published_at TIMESTAMP WITH TIME ZONE NOT NULL,
    content_hash CHAR(64) NOT NULL,
    model VARCHAR(120),
    embedding vector(1024),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (tenant_id, item_id)
);
CREATE INDEX reco_item_embedding_idx ON reco_item USING hnsw (embedding vector_cosine_ops);
CREATE INDEX reco_item_published_idx ON reco_item (tenant_id, published_at DESC);
CREATE INDEX reco_item_creator_idx ON reco_item (tenant_id, creator_id);

CREATE TABLE reco_event (
    tenant_id VARCHAR(64) NOT NULL,
    event_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(128) NOT NULL,
    item_id VARCHAR(64),
    creator_id VARCHAR(64),
    kind reco_event_kind NOT NULL,
    watched_pct SMALLINT CHECK (watched_pct BETWEEN 0 AND 100),
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    received_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (tenant_id, event_id),
    CHECK (item_id IS NOT NULL OR creator_id IS NOT NULL)
);
CREATE INDEX reco_event_user_idx ON reco_event (tenant_id, user_id, occurred_at DESC);
CREATE INDEX reco_event_item_idx ON reco_event (tenant_id, item_id);
CREATE INDEX reco_event_time_idx ON reco_event (tenant_id, occurred_at);

CREATE TABLE reco_user_profile (
    tenant_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(128) NOT NULL,
    embedding vector(1024) NOT NULL,
    interactions INTEGER NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (tenant_id, user_id)
);

-- What was served, for measurement (CTR, offline evaluation) — ids only, purged after
-- the tenant's requestLogDays.
CREATE TABLE reco_request (
    request_id UUID PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(128),
    kind VARCHAR(16) NOT NULL,
    strategy VARCHAR(16) NOT NULL,
    item_ids TEXT[] NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX reco_request_time_idx ON reco_request (tenant_id, created_at);

-- Version 1 of the parameters, for every tenant (ADR-078 §5).
INSERT INTO reco_settings (tenant_id, version, settings, reason, created_by) VALUES ('*', 1, '{
  "enabled": true,
  "weights": {"similarity": 0.45, "coEngagement": 0.2, "freshness": 0.15, "followed": 0.12, "popularity": 0.08},
  "kindWeights": {"VIEW": 1, "LIKE": 3, "SAVE": 3, "SHARE": 2, "TIP": 5, "UNLOCK": 5,
                  "FOLLOW": 0, "UNFOLLOW": 0, "SKIP": -2, "REPORT": -5},
  "halfLifeHours": 168, "freshHalfLifeHours": 72, "mmrLambda": 0.7, "creatorCap": 2,
  "candidatePool": 100, "profileEvents": 200, "popularWindowDays": 7, "excludeSeen": true,
  "retentionDays": 180, "requestLogDays": 30, "minAudience": 3
}', 'ADR-078: the first transparent weights', 'migration');

-- ============================================================================
-- ANALYTICS (ADR-081) — a product's events in; KPIs, cohorts, funnels and top
-- items out, per tenant. Same database and pool as the recommender: one write,
-- two reads. Subjects are PSEUDONYMS (HMAC by the product); items and owners are
-- its public ids. Kept identical to the incremental script
-- infra/migrations/2026-10-17-analytics.sql (workspace).
-- ============================================================================
CREATE TYPE analytics_value_unit AS ENUM ('none', 'percent', 'seconds', 'cents', 'count');
CREATE TYPE analytics_kind_source AS ENUM ('analytics', 'recommender');

-- The kinds a tenant may send — data, not code: '*' for every tenant, or one tenant's own.
-- A 'recommender' kind is sent to /api/v1/recommendations/events, never twice.
CREATE TABLE analytics_event_kind (
    tenant_id VARCHAR(64) NOT NULL,
    kind VARCHAR(32) NOT NULL CHECK (kind ~ '^[a-z][a-z0-9_]{1,31}$'),
    unit analytics_value_unit NOT NULL DEFAULT 'none',
    source analytics_kind_source NOT NULL DEFAULT 'analytics',
    description TEXT NOT NULL,
    PRIMARY KEY (tenant_id, kind)
);

CREATE TABLE analytics_event (
    tenant_id VARCHAR(64) NOT NULL,
    event_id VARCHAR(64) NOT NULL,
    subject_id VARCHAR(128) NOT NULL,
    kind VARCHAR(32) NOT NULL,
    item_id VARCHAR(64),
    owner_id VARCHAR(64),
    value DOUBLE PRECISION,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    received_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (tenant_id, event_id)
);
CREATE INDEX analytics_event_time_idx ON analytics_event (tenant_id, occurred_at);
CREATE INDEX analytics_event_subject_idx ON analytics_event (tenant_id, subject_id, occurred_at);
CREATE INDEX analytics_event_owner_idx ON analytics_event (tenant_id, owner_id, occurred_at);

-- Every insight reads this view: the analytics events and the recommender's, the latter
-- lower-cased, its watched share as the value and its item's creator as the owner.
CREATE VIEW analytics_event_all AS
    SELECT tenant_id, event_id, subject_id, kind, item_id, owner_id, value, occurred_at
      FROM analytics_event
    UNION ALL
    SELECT e.tenant_id, e.event_id, e.user_id, lower(e.kind::text), e.item_id,
           COALESCE(e.creator_id, i.creator_id), e.watched_pct::double precision, e.occurred_at
      FROM reco_event e
      LEFT JOIN reco_item i ON i.tenant_id = e.tenant_id AND i.item_id = e.item_id;

-- The parameters, versioned like reco_settings (ADR-081 §5).
CREATE TABLE analytics_settings (
    tenant_id VARCHAR(64) NOT NULL,
    version INTEGER NOT NULL CHECK (version >= 1),
    settings JSONB NOT NULL,
    reason TEXT NOT NULL,
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (tenant_id, version)
);

INSERT INTO analytics_event_kind (tenant_id, kind, unit, source, description) VALUES
    ('*', 'view', 'percent', 'recommender', 'An item was viewed; the value is the share watched.'),
    ('*', 'like', 'none', 'recommender', 'An item was liked.'),
    ('*', 'save', 'none', 'recommender', 'An item was saved.'),
    ('*', 'share', 'none', 'recommender', 'An item was shared.'),
    ('*', 'tip', 'none', 'recommender', 'A creator was tipped on an item.'),
    ('*', 'unlock', 'none', 'recommender', 'A paid item was unlocked.'),
    ('*', 'follow', 'none', 'recommender', 'A creator was followed.'),
    ('*', 'unfollow', 'none', 'recommender', 'A creator was unfollowed.'),
    ('*', 'skip', 'none', 'recommender', 'An item was skipped.'),
    ('*', 'report', 'none', 'recommender', 'An item was reported.'),
    ('*', 'session_start', 'none', 'analytics', 'A person opened the product.'),
    ('*', 'watch', 'percent', 'analytics', 'A person left an item; the value is the share watched.'),
    ('*', 'talk', 'seconds', 'analytics', 'A voice conversation ended; the value is its length.'),
    ('*', 'batch_open', 'none', 'analytics', 'A batch of candidates was opened.'),
    ('*', 'batch_answer', 'none', 'analytics', 'A candidate of a batch was answered.'),
    ('*', 'intro_start', 'none', 'analytics', 'An introduction was started.'),
    ('*', 'report_filed', 'none', 'analytics', 'A person was reported.'),
    ('*', 'purchase', 'cents', 'analytics', 'Something was bought; the value is its price.');

INSERT INTO analytics_settings (tenant_id, version, settings, reason, created_by) VALUES ('*', 1, '{
  "retentionDays": 180, "minAudience": 3, "maxRangeDays": 366, "cohortWeeks": 8,
  "funnelWindowHours": 168, "topItems": 10,
  "positiveKinds": ["like", "save", "share", "tip", "unlock", "batch_answer", "intro_start", "purchase"]
}', 'ADR-081: the first analytics parameters', 'migration');

-- ============================================================================
-- KNOWLEDGE SEED DATA
-- ============================================================================

