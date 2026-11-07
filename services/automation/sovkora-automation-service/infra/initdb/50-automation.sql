-- ============================================================================
-- SOVKORA — Local DB bootstrap · 50 — AUTOMATION CONTEXT
-- ----------------------------------------------------------------------------
-- Role and database ONLY. The schema is Flyway's: V1__automation_baseline.sql in
-- src/main/resources/db/migration, applied by the service at startup as the database's owner
-- role (krizaka-internal#2255). A table or a row here would be a second copy of the schema.
-- ============================================================================

-- The password is NOT set here. psql 15 cannot read the environment (\getenv is 16+)
-- and ERR-125 bans a shell script, so `sovkora start` applies ALTER ROLE from
-- AUTOMATION_DB_PASSWORD once the container is healthy. A role created without a
-- password cannot authenticate, so a skipped step fails closed rather than leaving a
-- guessable one — which is what the committed literal was (ADR-035, audit #5).
CREATE ROLE orazaka_automation LOGIN;
CREATE DATABASE orazaka_automation_db OWNER orazaka_automation;
