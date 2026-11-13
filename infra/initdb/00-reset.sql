-- ============================================================================
-- SOVKORA — Local DB bootstrap (AGENTS.md §5) · 00 — RESET
-- ----------------------------------------------------------------------------
-- The bootstrap is one initdb directory, one file per bounded context / future
-- service owner (strangler-fig data seam audit, Phase 0):
--   00-reset.sql          — cascade drop of every table (fresh-volume determinism)
--   10-identity.sql       — Identity service        (OWN DATABASE krizaka_users_db)
--   20-conversation.sql   — Conversation service    (chat sessions/messages)
--   30-jobs-config.sql    — Job Orchestration svc   (jobs, outbox, dedup, config plane)
--   40-knowledge.sql      — Knowledge service       (OWN DATABASE orazaka_knowledge_db)
--   50-automation.sql     — Automation service      (connectors, exec log, Quartz)
--   60-governance.sql     — Config plane            (interceptor policies + audit)
--   70-billing.sql        — Billing & Credits svc   (OWN DATABASE krizaka_billing_db)
--   80-studio.sql         — Studio service          (OWN DATABASE orazaka_studio_db)
--
-- Files run alphabetically via psql on a fresh pgvector volume (Flyway disabled,
-- spring.sql.init.mode=never). Cross-context FOREIGN KEYS are BANNED — references
-- between contexts are opaque ids only (enforced by SqlBoundaryRules on every build).
-- ============================================================================

-- Identity context: its own database since the Phase 2 cutover — dropped wholesale.
DROP DATABASE IF EXISTS krizaka_users_db WITH (FORCE);
DROP ROLE IF EXISTS krizaka_users;

-- Automation context: its own database since Phase 3a — dropped wholesale.
DROP DATABASE IF EXISTS orazaka_automation_db WITH (FORCE);
DROP ROLE IF EXISTS orazaka_automation;

-- Knowledge context: its own database since Phase 4 — dropped wholesale.
DROP DATABASE IF EXISTS orazaka_knowledge_db WITH (FORCE);
DROP ROLE IF EXISTS orazaka_knowledge;

-- Billing context: its own database — dropped wholesale.
DROP DATABASE IF EXISTS krizaka_billing_db WITH (FORCE);
DROP ROLE IF EXISTS krizaka_billing;

-- Studio context: its own database — dropped wholesale.
DROP DATABASE IF EXISTS orazaka_studio_db WITH (FORCE);
DROP ROLE IF EXISTS orazaka_studio;

DROP TABLE IF EXISTS policy_version_history CASCADE;
DROP TABLE IF EXISTS interceptor_policy_predicate CASCADE;
DROP TABLE IF EXISTS interceptor_policy CASCADE;

DROP TABLE IF EXISTS validation_pipeline_configs CASCADE;
DROP TABLE IF EXISTS pipeline_interceptor_config CASCADE;
DROP TABLE IF EXISTS platform_tool_configs CASCADE;
DROP TABLE IF EXISTS platform_mcp_servers CASCADE;
DROP TABLE IF EXISTS ai_providers CASCADE;
DROP TABLE IF EXISTS orazaka_chat_messages CASCADE;
DROP TABLE IF EXISTS orazaka_chat_sessions CASCADE;
DROP TABLE IF EXISTS orazaka_models CASCADE;
DROP TABLE IF EXISTS worker_registry CASCADE;
DROP TABLE IF EXISTS orazaka_capabilities CASCADE;
DROP TABLE IF EXISTS orazaka_runtime_config CASCADE;
DROP TABLE IF EXISTS orazaka_jobs CASCADE;
DROP TABLE IF EXISTS orazaka_job_purge CASCADE;
DROP TABLE IF EXISTS orazaka_routing_rules CASCADE;
DROP TABLE IF EXISTS outbox_events CASCADE;
DROP TABLE IF EXISTS processed_messages CASCADE;

DROP TABLE IF EXISTS user_mcp_servers CASCADE;

DROP TABLE IF EXISTS orazaka_tools_cache CASCADE;
