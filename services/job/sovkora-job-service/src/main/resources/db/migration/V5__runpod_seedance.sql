-- Seedance 2.5 Image-to-Video (ByteDance) on Runpod Public Endpoints, routed by the media worker
-- (workers/media, app/runpod_video.py; the models' facts are workers/media/models.yaml). The clip is made by the
-- provider `runpod-public-endpoint`; the run, its seed, its metering and its sealed asset are Sovkora's.
-- One row per resolution because the provider prices by resolution: $0.18 a second at 480p, $0.90 at 1080p
-- (docs.runpod.io/public-endpoints/models/seedance-2-5-i2v, read 2026-11-14). price_cents is one run of the shortest
-- clip the model makes (4 s). typical_seconds stays NULL until a run measures it. The worker reads the key from
-- AI_GPU_RUNPOD_API_KEY; the provider row carries none.
INSERT INTO ai_providers (name, base_url, api_key) VALUES
('runpod-public-endpoint', 'https://api.runpod.ai/v2', 'not-required')
ON CONFLICT (name) DO NOTHING;

INSERT INTO orazaka_models (model_name, model_label, category, options, provider_name, is_default, max_steps, recommended_fps, supported_hardware) VALUES
('seedance-2-5-i2v-480p', 'Seedance 2.5 I2V 480p (Runpod)', 'video', NULL, 'runpod-public-endpoint', FALSE, 0, 24, NULL),
('seedance-2-5-i2v-1080p', 'Seedance 2.5 I2V 1080p (Runpod)', 'video', NULL, 'runpod-public-endpoint', FALSE, 0, 24, NULL)
ON CONFLICT (model_name) DO NOTHING;

UPDATE orazaka_models SET vendor = 'ByteDance', family = 'Seedance 2.5', capabilities = 'video', runs_on = 'provider', runtime = 'Runpod Public Endpoints', open_weights = FALSE, licence = 'Provider terms (ByteDance Seedance, served by Runpod)', licence_url = 'https://docs.runpod.io/public-endpoints/models/seedance-2-5-i2v', commercial_use = 'conditions', context_window = NULL, status = 'available', price_cents = 72, description = 'Seedance 2.5 image-to-video (ByteDance) on Runpod Public Endpoints, routed by Sovkora — animates a starting image into 4 to 30 s of 9:16 video at 480p, $0.18 a second (a 4 s clip is $0.72). A starting image is required.' WHERE model_name = 'seedance-2-5-i2v-480p';
UPDATE orazaka_models SET vendor = 'ByteDance', family = 'Seedance 2.5', capabilities = 'video', runs_on = 'provider', runtime = 'Runpod Public Endpoints', open_weights = FALSE, licence = 'Provider terms (ByteDance Seedance, served by Runpod)', licence_url = 'https://docs.runpod.io/public-endpoints/models/seedance-2-5-i2v', commercial_use = 'conditions', context_window = NULL, status = 'available', price_cents = 360, description = 'Seedance 2.5 image-to-video (ByteDance) on Runpod Public Endpoints, routed by Sovkora — animates a starting image into 4 to 30 s of 9:16 video at 1080p, $0.90 a second (a 4 s clip is $3.60). A starting image is required.' WHERE model_name = 'seedance-2-5-i2v-1080p';
