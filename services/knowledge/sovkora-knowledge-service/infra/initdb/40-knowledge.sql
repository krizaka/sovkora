-- ============================================================================
-- SOVKORA — Local DB bootstrap · 40 — KNOWLEDGE CONTEXT (RAG sources)
-- ----------------------------------------------------------------------------
-- Role, database and extension ONLY. The schema is Flyway's: V1__knowledge_baseline.sql in
-- src/main/resources/db/migration, applied by the service at startup as the database's owner
-- role (krizaka-internal#2255). A table or a row here would be a second copy of the schema.
-- ============================================================================

-- The password is NOT set here. psql 15 cannot read the environment (\getenv is 16+)
-- and ERR-125 bans a shell script, so `sovkora start` applies ALTER ROLE from
-- KNOWLEDGE_DB_PASSWORD once the container is healthy. A role created without a
-- password cannot authenticate, so a skipped step fails closed rather than leaving a
-- guessable one — which is what the committed literal was (ADR-035, audit #5).
CREATE ROLE orazaka_knowledge LOGIN;
CREATE DATABASE orazaka_knowledge_db OWNER orazaka_knowledge;
\c orazaka_knowledge_db

-- pgvector is NOT a 'trusted' extension, so CREATE EXTENSION requires a superuser: created here as the
-- bootstrap superuser (dev and prod: the provisioning, as doadmin); V1 asserts it exists.
CREATE EXTENSION IF NOT EXISTS vector;
