-- =============================================================================
-- The provider-hosted models of the dev deployment, with their registry metadata (ORA-MODELS).
-- Run after infra/deploy/dev-data.sql (which creates the provider and the first rows) and after
-- infra/migrations/2026-10-14-model-registry.sql and 2026-10-15-commercial-defaults.sql. Idempotent; routing and
-- defaults are never touched here.
--
-- 'available' = the dev inference key was measured to serve the model (2026-10-14: mistral-3-14B, deepseek-v4-pro (2026-11-18: deepseek-v4-pro-0813 replaces it),
-- llama-4-maverick, gemma-4-31B-it and the gte-large-en-v1.5 embeddings). 'planned' would mean the row exists but the
-- key does not serve it yet — the site publishes what this says. Licences and context windows are those of each card.
-- =============================================================================
BEGIN;

INSERT INTO orazaka_models (model_name, model_label, category, options, provider_name, is_default)
VALUES ('mistral-3-14B', 'Mistral 3 14B', 'chat', NULL, 'do-inference', FALSE)
ON CONFLICT (model_name) DO NOTHING;
UPDATE orazaka_models SET vendor = 'Mistral AI', family = 'Mistral 3', capabilities = 'chat', runs_on = 'provider', runtime = 'DigitalOcean serverless inference', open_weights = TRUE, licence = 'Apache-2.0', licence_url = 'https://huggingface.co/mistralai/Ministral-3-14B-Instruct-2512/blob/main/README.md', commercial_use = 'yes', context_window = 262144, status = 'available' WHERE model_name = 'mistral-3-14B';

-- deepseek-v4-pro is retired by DigitalOcean on 2026-11-23 (404 after): its row leaves the catalogue ('internal') and deepseek-v4-pro-0813
-- (same 1,048,576-token context, same licence) takes its place (krizaka-internal#4355).
INSERT INTO orazaka_models (model_name, model_label, category, options, provider_name, is_default)
VALUES ('deepseek-v4-pro-0813', 'DeepSeek V4 Pro (0813)', 'chat', NULL, 'do-inference', FALSE)
ON CONFLICT (model_name) DO NOTHING;
UPDATE orazaka_models SET vendor = 'DeepSeek', family = 'DeepSeek V4', capabilities = 'chat,reasoning', runs_on = 'provider', runtime = 'DigitalOcean serverless inference', open_weights = TRUE, licence = 'MIT', licence_url = 'https://huggingface.co/deepseek-ai/DeepSeek-V4-Pro/blob/main/LICENSE', commercial_use = 'yes', context_window = 1048576, status = 'available' WHERE model_name = 'deepseek-v4-pro-0813';

-- Retired: DigitalOcean answers 404 for deepseek-v4-pro from 2026-11-23 (krizaka-internal#4355); 'internal' keeps it out of the published catalogue.
INSERT INTO orazaka_models (model_name, model_label, category, options, provider_name, is_default)
VALUES ('deepseek-v4-pro', 'DeepSeek V4 Pro', 'chat', NULL, 'do-inference', FALSE)
ON CONFLICT (model_name) DO NOTHING;
UPDATE orazaka_models SET vendor = 'DeepSeek', family = 'DeepSeek V4', capabilities = 'chat,reasoning', runs_on = 'provider', runtime = 'DigitalOcean serverless inference', open_weights = TRUE, licence = 'MIT', licence_url = 'https://huggingface.co/deepseek-ai/DeepSeek-V4-Pro/blob/main/LICENSE', commercial_use = 'yes', context_window = 1048576, status = 'internal' WHERE model_name = 'deepseek-v4-pro';

INSERT INTO orazaka_models (model_name, model_label, category, options, provider_name, is_default)
VALUES ('llama-4-maverick', 'Llama 4 Maverick', 'chat', NULL, 'do-inference', FALSE)
ON CONFLICT (model_name) DO NOTHING;
UPDATE orazaka_models SET vendor = 'Meta', family = 'Llama 4', capabilities = 'chat', runs_on = 'provider', runtime = 'DigitalOcean serverless inference', open_weights = TRUE, licence = 'Llama 4 Community Licence', licence_url = 'https://github.com/meta-llama/llama-models/blob/main/models/llama4/LICENSE', commercial_use = 'conditions', context_window = 1048576, status = 'available' WHERE model_name = 'llama-4-maverick';

INSERT INTO orazaka_models (model_name, model_label, category, options, provider_name, is_default)
VALUES ('gemma-4-31B-it', 'Gemma 4 31B', 'chat', NULL, 'do-inference', FALSE)
ON CONFLICT (model_name) DO NOTHING;
UPDATE orazaka_models SET vendor = 'Google', family = 'Gemma 4', capabilities = 'chat', runs_on = 'provider', runtime = 'DigitalOcean serverless inference', open_weights = TRUE, licence = 'Apache-2.0', licence_url = 'https://huggingface.co/google/gemma-4-31B-it/blob/main/README.md', commercial_use = 'yes', context_window = 262144, status = 'available' WHERE model_name = 'gemma-4-31B-it';

INSERT INTO orazaka_models (model_name, model_label, category, options, provider_name, is_default)
VALUES ('gte-large-en-v1.5', 'GTE Large (en) v1.5', 'embedding', NULL, 'do-inference', FALSE)
ON CONFLICT (model_name) DO NOTHING;
UPDATE orazaka_models SET vendor = 'Alibaba-NLP', family = 'GTE', capabilities = 'embeddings', runs_on = 'provider', runtime = 'DigitalOcean serverless inference', open_weights = TRUE, licence = 'Apache-2.0', licence_url = 'https://huggingface.co/Alibaba-NLP/gte-large-en-v1.5/blob/main/README.md', commercial_use = 'yes', context_window = 8192, status = 'available' WHERE model_name = 'gte-large-en-v1.5';

INSERT INTO orazaka_models (model_name, model_label, category, options, provider_name, is_default)
VALUES ('nemotron-nano-12b-v2-vl', 'Nemotron Nano 12B v2 VL', 'vision', NULL, 'do-inference', FALSE)
ON CONFLICT (model_name) DO NOTHING;
UPDATE orazaka_models SET vendor = 'NVIDIA', family = 'Nemotron Nano v2 VL', capabilities = 'vision,chat', runs_on = 'provider', runtime = 'DigitalOcean serverless inference', open_weights = TRUE, licence = 'NVIDIA Open Model License', licence_url = 'https://huggingface.co/nvidia/NVIDIA-Nemotron-Nano-12B-v2-VL-BF16', commercial_use = 'yes', status = 'available' WHERE model_name = 'nemotron-nano-12b-v2-vl';

-- The embedding model of dev (POST /api/v1/embeddings resolves the default of the `embedding` category, never a model the caller
-- names). GTE Large (en) v1.5 is the one the dev inference key may call (1024 dimensions, the size a product's Explorer stores in
-- pgvector); BGE-M3, multilingual (English and French), answers 403 on that key today and replaces it as the default the day the
-- model is enabled on the key (`UPDATE orazaka_models SET status = 'available'` for it, then the default line below).
UPDATE orazaka_models SET status = 'available' WHERE model_name = 'gte-large-en-v1.5';
INSERT INTO orazaka_models (model_name, model_label, category, options, provider_name, is_default, description)
VALUES ('bge-m3', 'BGE-M3', 'embedding', NULL, 'do-inference', FALSE,
        'BGE-M3 (MIT) on DigitalOcean serverless inference — multilingual text embeddings (English, French), 1024 dimensions. Planned: the inference key of dev may not call it yet.')
ON CONFLICT (model_name) DO NOTHING;
UPDATE orazaka_models SET vendor = 'BAAI', family = 'BGE-M3', capabilities = 'embeddings', runs_on = 'provider', runtime = 'DigitalOcean serverless inference', open_weights = TRUE, licence = 'MIT', licence_url = 'https://huggingface.co/BAAI/bge-m3/blob/main/LICENSE', commercial_use = 'yes', context_window = 8192, status = 'planned' WHERE model_name = 'bge-m3';
UPDATE orazaka_models SET is_default = (model_name = 'gte-large-en-v1.5') WHERE category = 'embedding';

COMMIT;
