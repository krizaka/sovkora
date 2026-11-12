-- The chat route of the dev environment (ADR-095, krizaka-internal#3285): hosted per-token inference, never a GPU.
-- Routing is data (AGENTS.md §4). This location is added only by the dev core container's supervisor
-- (infra/deploy/run-services.sh, DATABASE_JOB_MIGRATIONS_LOCATIONS), so a local or customer database keeps the baseline's
-- local default. Repeatable and idempotent: the service applies it at startup as the owner role, and a re-baselined dev
-- database gets its route back at the next deploy instead of drifting to the GPU relay's ollama (Connection refused).
-- The provider row (do-inference) comes from the baseline; its key is set by `node scripts/dev-deploy.mjs data`.
INSERT INTO orazaka_models (model_name, model_label, category, options, provider_name, is_default, description)
VALUES ('mistral-3-14B', 'Mistral 3 14B', 'chat', NULL, 'do-inference', FALSE,
        'Mistral 3 14B (Apache-2.0) on DigitalOcean serverless inference — the chat default of the dev '
        || 'environment: fluent English and French, billed per token, no GPU to run.')
ON CONFLICT (model_name) DO UPDATE SET provider_name = EXCLUDED.provider_name;
UPDATE orazaka_models SET is_default = (model_name = 'mistral-3-14B') WHERE category = 'chat';
