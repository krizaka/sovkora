# Measured runs

What the video and image models Orazaka routes to really did, measured on **2026-10-17**: each figure below is read from the **run records** of the platform (start and finish of every Studio run, its size and seed, its outcome) and from the engine's own log — none is typed by hand. Every number is for **this setup** and is measured again when the setup changes (a new model, engine version, provider or machine).

## Setup

| Part            | What                                                                                                                          |
| --------------- | ----------------------------------------------------------------------------------------------------------------------------- |
| Machine         | MacBook Pro, Apple M1 Max, 64 GB unified memory (Metal)                                                                       |
| Local engine    | stable-diffusion.cpp master-647-72e512a-1-ga397e03 (sd-server, Metal)                                                         |
| Platform        | Orazaka dev (DigitalOcean App Platform, Toronto) — core, relay, broker; the Mac joins through the GPU relay (chisel over TLS) |
| Hosted provider | DigitalOcean serverless inference (https://inference.do-ai.run)                                                               |
| Orazaka commit  | `dbe135b`                                                                                                                     |

Times are **command to file**: from the start of the Studio run to the finished output (queueing, transfer and storage included). The engine's own time, where it is logged, is given beside it.

## Per model

| Model                                                            | Where                                                       | Runs (ok / all) | Time, command to file (median, min–max) | Engine time                | Price                   | Spent  |
| ---------------------------------------------------------------- | ----------------------------------------------------------- | --------------- | --------------------------------------- | -------------------------- | ----------------------- | ------ |
| **Wan 2.2 T2V A14B** (video)                                     | DigitalOcean serverless inference                           | 21 / 21         | 131 s (129–203, n=18)                   | —                          | $0.60 per video         | $12.60 |
| **FLUX.2 [klein] 4B (Q8_0 GGUF)** (image)                        | One Mac (Metal), local                                      | 6 / 18          | 62 s (61–66, n=6)                       | 72.7 s (60.8–75.0, n=20)   | none (your own machine) | —      |
| **Stable Diffusion 1.5** (image)                                 | One Mac (Metal), local                                      | 6 / 6           | 58 s (58–58, n=6)                       | 56.6 s (56.5–56.6, n=6)    | none (your own machine) | —      |
| **Stable Diffusion 3.5 Large** (image)                           | DigitalOcean serverless inference                           | 51 / 51         | 8 s (7–24, n=51)                        | —                          | $0.08 per image         | $4.08  |
| **Z-Image Turbo (Q8_0 GGUF)** (image)                            | One Mac (Metal), local                                      | 0 / 5           | —                                       | 157.8 s (156.7–158.9, n=2) | none (your own machine) | —      |
| **Wan 2.2 TI2V 5B (image to video, H100 80 GB)** (video)         | A DigitalOcean GPU droplet, by the hour (through the relay) | 28 / 28         | 177 s (168–279, n=28)                   | —                          | $3.99 per GPU hour      | $5.93  |
| **Wan 2.2 TI2V 5B (image to video, RTX 4000 Ada 20 GB)** (video) | A DigitalOcean GPU droplet, by the hour (through the relay) | 1 / 1           | 1106 s (1106–1106, n=1)                 | —                          | $0.76 per GPU hour      | $0.23  |
| **Wan 2.2 T2V A14B (720p, H100 80 GB)** (video)                  | A DigitalOcean GPU droplet, by the hour (through the relay) | 8 / 8           | 1116 s (1104–1302, n=8)                 | —                          | $3.99 per GPU hour      | $10.24 |
| **Wan 2.2 A14B, image to video (720p, H100 80 GB)** (video)      | A DigitalOcean GPU droplet, by the hour (through the relay) | 7 / 7           | 1179 s (1117–1350, n=7)                 | —                          | $3.99 per GPU hour      | $9.16  |

3 runs of Wan 2.2 T2V A14B succeeded but are left out of its time statistics (worker restart during the run).

## Parameters

### Wan 2.2 T2V A14B

DigitalOcean serverless inference · licence Apache-2.0

- **size:** 832×480 (fixed by the provider: 720×1280, 480×832 and 1280×720 were all answered at 832×480)
- **frames:** 81
- **fps:** 16
- **duration:** 5.06 s
- **seed:** honoured — four calls with the same prompt and seed gave identical frames
- **steps / guidance / scheduler:** not exposed by the provider's API

### FLUX.2 [klein] 4B (Q8_0 GGUF)

One Mac (Metal), local · licence Apache-2.0

- **size:** 1024×1024
- **steps:** 4
- **guidance (cfg scale):** 1
- **sampler:** Euler
- **scheduler:** discrete
- **flags:** --diffusion-fa
- **textEncoder:** Qwen3-4B Q8_0
- **vae:** flux2-vae
- **seed:** honoured
- **memory:** 8.3 GB (VRAM)

### Stable Diffusion 1.5

One Mac (Metal), local · licence CreativeML OpenRAIL-M

- **size:** 512×512
- **steps:** 20
- **guidance (cfg scale):** 7
- **sampler:** Euler a
- **scheduler:** discrete
- **seed:** honoured

### Stable Diffusion 3.5 Large

DigitalOcean serverless inference · licence Stability AI Community Licence

- **size:** 1024×1024
- **steps / guidance / scheduler:** not exposed by the provider's API
- **seed:** not honoured (the provider ignores it)
- **output:** uncompressed PNG, about 3.1 MB

### Z-Image Turbo (Q8_0 GGUF)

One Mac (Metal), local · licence Apache-2.0

- **size:** 1024×1024
- **steps:** 8
- **guidance (cfg scale):** 1
- **sampler:** Euler
- **scheduler:** discrete
- **memory:** 10.5 GB (VRAM)

### Wan 2.2 TI2V 5B (image to video, H100 80 GB)

A DigitalOcean GPU droplet, by the hour (through the relay) · licence Apache-2.0

- **size:** 1280×704 (16:9) and 704×1280 (9:16), the model's native sizes — the photo is cut to the frame around its subject, never letterboxed
- **frames:** 121
- **fps:** 24
- **duration:** 5.04 s (10 s = two linked runs, 238 frames)
- **steps / guidance:** 40 / 5.0
- **seed:** honoured
- **GPU:** NVIDIA H100 80 GB (RunPod), model resident, CPU offload off the 80 GB card
- **pipeline:** diffusers WanImageToVideoPipeline (the checkpoint's expand_timesteps)

### Wan 2.2 TI2V 5B (image to video, RTX 4000 Ada 20 GB)

A DigitalOcean GPU droplet, by the hour (through the relay) · licence Apache-2.0

- **size:** 1280×704 — native, it fits (19.7 GB of VRAM at the peak) with model CPU offload and VAE tiling; no size ladder step was needed
- **frames:** 121
- **fps:** 24
- **duration:** 5.04 s
- **steps / guidance:** 40 / 5.0
- **seed:** honoured
- **host RAM:** 25.5 GB resident at the peak on a 31 GB host (the image engine and Ollama were stopped)
- **GPU:** NVIDIA RTX 4000 Ada 20 GB (DigitalOcean droplet)

### Wan 2.2 T2V A14B (720p, H100 80 GB)

A DigitalOcean GPU droplet, by the hour (through the relay) · licence Apache-2.0

- **size:** 1280×720 (16:9) and 720×1280 (9:16)
- **frames:** 81
- **fps:** 16
- **duration:** 5.06 s
- **steps / guidance:** 40 / 4.0 (high-noise expert) and 3.0 (low-noise expert), boundary 0.875
- **seed:** honoured
- **GPU:** NVIDIA H100 80 GB (RunPod), two bf16 experts (57 GB) swapped by CPU offload — about 26 s per step
- **load:** 129 s from the network volume

### Wan 2.2 A14B, image to video (720p, H100 80 GB)

A DigitalOcean GPU droplet, by the hour (through the relay) · licence Apache-2.0

- **size:** 1280×720 (16:9), the starting image cut to the frame around its subject, never letterboxed
- **frames:** 81
- **fps:** 16
- **duration:** 5.06 s
- **steps / guidance:** 40 / 4.0 (high-noise expert) and 3.0 (low-noise expert)
- **seed:** honoured
- **GPU:** NVIDIA H100 80 GB (RunPod), two bf16 experts swapped by CPU offload — about 28 s per step, 19 to 22 minutes a clip
- **checkpoint:** Wan-AI/Wan2.2-I2V-A14B-Diffusers (the image-to-video checkpoint of the family, fetched to the pod's own disk: 118 GB in 2 minutes)

## Video upscaling

The same clips, taken from 720p (or 480p) to 1080p or 4K by each candidate, on one NVIDIA H100 80 GB. The verdict is a person's, after looking at the same frames side by side with a plain Lanczos upscale; the cost is the run's own time at the GPU's hourly rate.

| Model                                  | Licence                 | Commercial use                              | Size                 | Time (5 s clip) | Cost per clip | Verdict                                                                                                      |
| -------------------------------------- | ----------------------- | ------------------------------------------- | -------------------- | --------------- | ------------- | ------------------------------------------------------------------------------------------------------------ |
| Real-ESRGAN x4plus (per frame, ffmpeg) | BSD-3-Clause            | yes                                         | 1280×704 → 1920×1080 | 104.3 s         | $0.12         | sharper than Lanczos but gritty, with a painterly texture on fine detail                                     |
| Real-ESRGAN x4plus (per frame, ffmpeg) | BSD-3-Clause            | yes                                         | 1280×704 → 3840×2160 | 127 s           | $0.14         | fastest, but the 4K keeps the grain: not chosen                                                              |
| SeedVR2 3B (video diffusion)           | Apache-2.0              | yes                                         | 832×480 → 1872×1080  | 58 s            | $0.06         | winner — clean detail without grain, temporally stable; 480p sources (the product feed) become convincing HD |
| SeedVR2 3B (video diffusion)           | Apache-2.0              | yes                                         | 1280×720 → 1920×1080 | 107.4 s         | $0.12         | winner — the default of the Studio's `upscale: 1080p` step                                                   |
| SeedVR2 3B (video diffusion)           | Apache-2.0              | yes                                         | 1280×720 → 3840×2160 | 280.3 s         | $0.31         | winner — 4K holds (tiled VAE, 31 GB): `upscale: 2160p`, for a hero                                           |
| LTX-2 19B (native high resolution)     | LTX-2 Community License | conditions (free under $10M annual revenue) | —                    | not run         | —             | not run: weights larger than the pod's free disk; licence conditions to read first                           |

## Image-to-video bake-off

The same FLUX.2 [klein] still made at the video's own aspect through the image-generation Studio (1360×768 and 768×1360; Studio runs c3efac13-6c5b-4743-af3b-e856204f3d96 and 12cee392-9a0e-4a18-a496-44bf0d324c8a), the same motion-only prompt and negatives, the same seed. Direct diffusers runs on the pod, not Studio runs. Landscape: a cable car in the Alps at dawn (a dolly-in is asked); 9:16: a woman and a golden retriever walking in the snow (motion and anatomy). One NVIDIA H100 80 GB; the cost is the run's own time at the GPU's hourly rate. The verdict is a person's, after looking at the frame grid (first, quarter, middle, three quarters, last) of both shots.

| Model                                     | Licence                                                                                              | Commercial use                                                   | Shot           | Size                       | Time         | Cost  | VRAM    | Verdict                                                                                                                                             |
| ----------------------------------------- | ---------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------- | -------------- | -------------------------- | ------------ | ----- | ------- | --------------------------------------------------------------------------------------------------------------------------------------------------- |
| Wan 2.2 I2V A14B (40 steps, guidance 3.5) | Apache-2.0                                                                                           | yes                                                              | 16:9 landscape | 1280×720 · 81 fr · 16 fps  | 1085.8 s     | $1.20 | 35.4 GB | clean and stable, but the camera barely obeys (the cabin recedes instead of the dolly-in); 18 minutes                                               |
| Wan 2.2 I2V A14B (40 steps, guidance 3.5) | Apache-2.0                                                                                           | yes                                                              | 9:16 people    | 720×1280 · 81 fr · 16 fps  | 1084.8 s     | $1.20 | 35.4 GB | the best anatomy: a natural gait, the dog's tail, the coat; no warped limb                                                                          |
| LTX-Video 0.9.8 13B distilled (8 steps)   | LTX-Video Open Weights License 0.X                                                                   | yes (no revenue clause)                                          | 16:9 landscape | 1216×704 · 121 fr · 24 fps | 13.5 s       | $0.01 | 38.7 GB | the winner: sharp, the dolly-in is obeyed, 80× faster than Wan                                                                                      |
| LTX-Video 0.9.8 13B distilled (8 steps)   | LTX-Video Open Weights License 0.X                                                                   | yes (no revenue clause)                                          | 9:16 people    | 704×1216 · 121 fr · 24 fps | 14.5 s       | $0.02 | 38.8 GB | a good preview: natural walk, but a dark boot-sole artefact on two frames                                                                           |
| CogVideoX1.5 5B I2V (50 steps)            | CogVideoX License (commercial use: a free basic licence after registration, under 1M visits a month) | conditions                                                       | 16:9 landscape | 1360×768 · 81 fr · 16 fps  | 406.4 s      | $0.45 | 26.6 GB | clean but almost static: the requested camera move is ignored                                                                                       |
| CogVideoX1.5 5B I2V (50 steps)            | CogVideoX License (commercial use: a free basic licence after registration, under 1M visits a month) | conditions                                                       | 9:16 people    | —                          | not finished | —     | —       | not finished: 768×1360 is refused (tensor size mismatch in the pipeline)                                                                            |
| HunyuanVideo 1.5 720p I2V (50 steps)      | Tencent Hunyuan Community License                                                                    | no for us: excludes the EU, UK and South Korea, outputs included | 16:9 landscape | —                          | not finished | —     | —       | not finished: 19 of 50 steps at 90 s a step (75 GB offloaded), about 75 minutes a clip — and the territory exclusion rules it out for a public site |
| Mochi 1 (genmo)                           | Apache-2.0                                                                                           | yes                                                              | —              | —                          | not finished | —     | —       | not run: text-to-video only                                                                                                                         |
| LongCat-Video (Meituan, 13.6B)            | MIT                                                                                                  | yes                                                              | —              | —                          | not finished | —     | —       | not run: custom code outside diffusers; to try next                                                                                                 |

**Winners:**

- **16:9 landscape and iteration**: LTX-Video 0.9.8 13B distilled — 14 s a 5 s clip at 1216×704, 39 GB, the camera move is obeyed, sharp — and commercial use is free of any revenue clause
- **9:16 people and animals**: Wan 2.2 I2V A14B at 40 steps — the best anatomy and gait; 18 minutes, so the finish of a hero clip, with LTX as the 14 s preview

## Failures, timeouts and limits

| Kind           | What happened                                                                                                                                                                              | Cause                                                                                                                                                                                                                                                               |
| -------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| access         | Wan 2.2 A14B refused the first call (403)                                                                                                                                                  | the model access key did not list the model; an all-models key fixed it                                                                                                                                                                                             |
| limit          | Wan 2.2 A14B cannot make 9:16                                                                                                                                                              | every size asked (720×1280, 480×832, 1280×720, with or without aspect_ratio/resolution) came back 832×480; the model is refused for 9:16 by name rather than cropped                                                                                                |
| limit          | Wan 2.2 TI2V 5B on the Mac (480×832, 25 frames)                                                                                                                                            | MPS out of memory while decoding (26 GiB of MPS, 62 GiB held by other allocations on a 64 GB machine)                                                                                                                                                               |
| infrastructure | three video runs took 264 s, 439 s and 1 063 s instead of about 130 s                                                                                                                      | the Mac worker had lost its tunnel and restarted; the jobs waited in the queue (their times are left out of the statistics)                                                                                                                                         |
| access         | openai-gpt-image-2 refused (403)                                                                                                                                                           | not available for this subscription tier (tried once)                                                                                                                                                                                                               |
| configuration  | Stable Diffusion 3.5 Large: first Orazaka runs returned a 1×1 transparent image                                                                                                            | the provider's base URL lacked /v1 (the client appends /images/generations): 404, then a transparent fallback; fixed with the provider do-inference-images                                                                                                          |
| timeout        | FLUX.2 [klein] returned a 1×1 placeholder on several runs (server room and notary desk on every attempt; neon arcade twice)                                                                | the image client gives up waiting through the relay (about 2 minutes) while the engine needed 60–75 s plus the transfer of a 2.5 MB image; a web deploy also restarts the relay and drops the tunnel                                                                |
| process        | two FLUX.2 images (server room, notary desk) were produced in about 62 s each, then lost                                                                                                   | later retries wrote placeholders over the same file names before they were published (a process mistake, now guarded by checking the size of each download)                                                                                                         |
| timeout        | Z-Image Turbo at 1024×1024 was dropped                                                                                                                                                     | the engine needs about 157 s per image (8 steps at about 18 s each), longer than the client waits                                                                                                                                                                   |
| limit          | DigitalOcean GPUs: the 48 GB sizes (L40S, RTX 6000 Ada) were never in stock; the 20 GB RTX 4000 Ada appeared and disappeared (3 starts, 2 refused for stock)                               | sold out in tor1, the only region the private broker's VPC reaches                                                                                                                                                                                                  |
| infrastructure | ten clips in a row failed in 17 s (HTTP 500) after the first one succeeded                                                                                                                 | the worker's free-RAM pre-flight counted the first clip's resident pipeline (~22 GB) against the next job (fixed: no pre-flight for a resident pipeline, orazaka-worker-media#22)                                                                                   |
| infrastructure | three clips (up to 9 minutes of GPU each) were lost mid-render                                                                                                                             | every deploy of dev.orazaka.com restarts the relay: the worker's AMQP stream dropped and the process exited (fixed: the consumer reconnects and skips a redelivered job, orazaka-worker-media#24)                                                                   |
| infrastructure | several runs failed with 404 on their starting photo                                                                                                                                       | the platform keeps uploads on the core's disk, which a redeploy wipes; the batch runner uploads the photo again (media durable on Bunny is krizaka/orazaka#144)                                                                                                     |
| infrastructure | the droplet's watchdog destroyed an idle droplet (1.2 h) while the laptop that drove the batch was asleep                                                                                  | 30 min without a job; the batch runner now resumes a run in flight from its ticket                                                                                                                                                                                  |
| limit          | Wan 2.2 A14B at 720p cannot be resident on one 80 GB card                                                                                                                                  | two bf16 experts are 57 GB; with the activations of 1280×720 the card is full, so the experts are offloaded to the host and swapped (~26 s a step, ~18 min a clip)                                                                                                  |
| infrastructure | the RunPod pod kept billing about two hours after its last job                                                                                                                             | its in-pod watchdog destroys the pod through the RunPod API and the pod has no API key of its own; a guard loop on the maintainer's machine ended it (to fix: a restricted key for the pod, or stop from the driver as soon as the queue is empty)                  |
| configuration  | the first 720p run failed: cached snapshot incomplete                                                                                                                                      | the fetch pattern list had transformer/* but not the second expert transformer_2/* (fixed, orazaka-worker-media#27)                                                                                                                                                 |
| quality        | three Wan 2.2 TI2V 5B attempts of the mint-tea pour were rejected on review                                                                                                                | the stream missed the glass, the glass doubled, the teapot drifted — the subject was replaced rather than a bad clip shown                                                                                                                                          |
| quality        | Stable Diffusion 3.5 Large candidates were rejected on review (21 of the 48 images are the ones kept or compared)                                                                          | a smeared glass-like haze over the face of the tea pourer, a mushy hologram; 1024×1024 is the only size the provider makes, so each kept image is cut to 16:9                                                                                                       |
| access         | one finished clip could not be downloaded (404) and was run again                                                                                                                          | the platform keeps outputs on the core's disk, which a redeploy of dev wipes (durable media: krizaka/orazaka#144)                                                                                                                                                   |
| quality        | the 720p text-to-video clip « City at night, neon in the rain » was rejected (a neon sign read as gibberish letters despite « no lettering »); « Acoustic guitar by the fire » replaced it | Wan 2.2 A14B draws signage on neon scenes; the text gate caught it on the frame grid                                                                                                                                                                                |
| quality        | the image-to-video clip of the snowy Plateau street drifted in colour on two seeds (grey first frame, then a saturated copper wall); its first ten frames were cut                         | the model re-exposes the starting image within the first 0.6 s; the rest of the clip is consistent                                                                                                                                                                  |
| limit          | LTX-2 (native high resolution) was not run                                                                                                                                                 | its checkpoint is 25 GB in fp8 but the repository publishes a 93 GB text encoder, more than the pod's free disk, and its licence is the LTX-2 Community License (free under $10M annual revenue, a paid agreement above) — a licence to read before it is a default |
| limit          | Wan 2.2 A14B image-to-video needs its own 106 GB checkpoint                                                                                                                                | text and image are two checkpoints of the family; the network volume has 35 GB free, so it is fetched to the pod's container disk each session (2 minutes) instead of a recurring volume                                                                            |
| process        | the first Wan 2.2 bake-off run (18 minutes of GPU) finished but was lost                                                                                                                   | the bake script saved numpy frames as if they were PIL images (output_type); fixed (output_type=pil) and run again                                                                                                                                                  |
| limit          | HunyuanVideo 1.5 720p image-to-video was stopped after 19 of 50 steps                                                                                                                      | 90 s a step with model offload (75 GB resident), about 75 minutes a clip; its licence also excludes the EU, UK and South Korea                                                                                                                                      |

## What it cost

| Provider                                                    | Output                                               | Price              | Runs | Spent      |
| ----------------------------------------------------------- | ---------------------------------------------------- | ------------------ | ---- | ---------- |
| DigitalOcean serverless inference                           | Wan 2.2 T2V A14B                                     | $0.60 per video    | 21   | $12.60     |
| DigitalOcean serverless inference                           | Stable Diffusion 3.5 Large                           | $0.08 per image    | 51   | $4.08      |
| A DigitalOcean GPU droplet, by the hour (through the relay) | Wan 2.2 TI2V 5B (image to video, H100 80 GB)         | $3.99 per GPU hour | 28   | $5.93      |
| A DigitalOcean GPU droplet, by the hour (through the relay) | Wan 2.2 TI2V 5B (image to video, RTX 4000 Ada 20 GB) | $0.76 per GPU hour | 1    | $0.23      |
| A DigitalOcean GPU droplet, by the hour (through the relay) | Wan 2.2 T2V A14B (720p, H100 80 GB)                  | $3.99 per GPU hour | 8    | $10.24     |
| A DigitalOcean GPU droplet, by the hour (through the relay) | Wan 2.2 A14B, image to video (720p, H100 80 GB)      | $3.99 per GPU hour | 7    | $9.16      |
| **Total of the runs above**                                 |                                                      |                    |      | **$42.24** |

### GPU droplet sessions (what was billed)

| Session                                                                                                                                                                                  | GPU                   | Hours | Rate    | Billed     |
| ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------- | ----- | ------- | ---------- |
| DigitalOcean droplet 1 (first start: image build, aborted)                                                                                                                               | RTX 4000 Ada 20 GB    | 0.10  | $0.76/h | $0.08      |
| DigitalOcean droplet 2 (first clip, 18 min; failures)                                                                                                                                    | RTX 4000 Ada 20 GB    | 1.91  | $0.76/h | $1.45      |
| DigitalOcean droplet 3 (dropped tunnels, watchdog)                                                                                                                                       | RTX 4000 Ada 20 GB    | 1.05  | $0.76/h | $0.80      |
| DigitalOcean droplet 4 (idle while the driver slept)                                                                                                                                     | RTX 4000 Ada 20 GB    | 1.20  | $0.76/h | $0.91      |
| RunPod pod (image-to-video pairs, 10 s chains, 720p A14B heroes; it ran ~2 h past its last job because the in-pod watchdog had no key to destroy itself — the local guard loop ended it) | H100 80 GB (CA-MTL-1) | 5.01  | $3.99/h | $19.99     |
| RunPod pod (the 5B image-to-video batch of the home page, the 720p heroes, the first upscaler tests; ended by the spend guard once idle)                                                 | H100 80 GB (CA-MTL-1) | 2.50  | $3.99/h | $9.98      |
| RunPod pod (six directed image-to-video clips with Wan 2.2 A14B, one retried, the 1080p/4K passes, the product hero; stopped by id when done)                                            | H100 80 GB (CA-MTL-1) | 2.72  | $3.99/h | $10.85     |
| RunPod pod (the image-to-video bake-off: four models, two shots; a first Wan run finished but crashed in the bake script's save step and was run again)                                  | H100 80 GB (CA-MTL-1) | 1.77  | $3.99/h | $7.06      |
| **Total billed**                                                                                                                                                                         |                       |       |         | **$51.12** |

A droplet is billed from its creation to its destruction: the sessions include the setup (image build, weights), the load of the model and the attempts that failed, which the runs above do not. The cost of a run is its own start-to-finish time at the hourly rate.

The local models cost nothing per output; the energy of the Mac was **not measured**, so none is claimed. Probes and refused calls (a 403, a size test) are not runs and are not in this table.

See also: [Supported models](MODELS.md) for the registry, and the comparison images on the gallery (`/gallery?kind=compare`).

## Cinematic shorts

2 film(s) made by `orazaka cinema` (ADR-080, last measured **2026-10-20**): script or treatment, character references and keyframes (FLUX.2 [klein]), shots, the cut (SeedVR2 3B, speed ramps, LUT grade, titles, synthesized foley), the voice-over and the music (ACE-Step). Each film's shots, on its GPU:

- **NOVA ARDENT: DAWN OF CYAN**: Wan 2.2 I2V A14B (4-step distill) on one RunPod H100 80 GB (CA-MTL-1, $3.99/h), upscaled by SeedVR2 3B
- **The World of Krizaka: Dawn**: Wan 2.2 I2V A14B (40 steps), LTX-Video 0.9.8 13B distilled on one RunPod H200 141 GB (secure cloud, $5.29/h), upscaled by SeedVR2 3B to 2160p (batch 21)

| Film                           | Frame            | Length | Shots (reshot) | Runs (failed) | GPU time of its runs | At the GPU rate | Wall time |
| ------------------------------ | ---------------- | ------ | -------------- | ------------- | -------------------- | --------------- | --------- |
| **NOVA ARDENT: DAWN OF CYAN**  | 1080×1920 (9:16) | 40.5 s | 10 (2)         | 35 (5)        | 119 min              | $7.89           | 237 min   |
| **The World of Krizaka: Dawn** | 3840×2160 (16:9) | 62.5 s | 19 (1)         | 20 (0)        | 329 min              | $29.00          | —         |

### What it cost per minute of finished film

| Measure                                                                           | Value      |
| --------------------------------------------------------------------------------- | ---------- |
| Finished film                                                                     | 1.72 min   |
| GPU pod sessions billed (setup, reviews and reshoots included)                    | $58.01     |
| **Billed per minute of finished film**                                            | **$33.81** |
| The runs' own GPU time per minute of film (what the Studio's estimate multiplies) | $21.50     |
| Wall time per minute of film (one pod, runs in sequence)                          | 138.3 min  |

> The billed sessions also rendered the Montréal reel and the news explainer (shots, the reel's cut) that are not delivered yet, and the showcase session's own image-to-video jobs drained by the same pod: the billed figure per minute is this batch's, not a film's. A film's own cost is its runs' GPU time at the rate (the table above). « l'aube » was made outside the Studios on a GPU host running the media worker's own generator and upscaler (orazaka cinema jobs / take), 40-step Wan takes beside concurrent SeedVR2 4K upscales; its keyframes, score and narrator were made on a Mac (Metal, $0).

| Pod session                                                                                                                                                                         | GPU         | Hours | Rate    | Billed |
| ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------- | ----- | ------- | ------ |
| pod 1 (first start: weights and sd-server build, then released while CI ran)                                                                                                        | H100 80 GB  | 0.30  | $3.99/h | $1.20  |
| pod 2 (superhero, reel and news shots, two cuts; shared with the showcase session's I2V jobs; stopped by an idle guard at ~14:45, destroyed 15:04)                                  | H100 80 GB  | 5.22  | $3.99/h | $20.83 |
| pod 3 (resumed for the reel's music and the news shots; stopped by the coordinator for the GPU queue before a run could start: dev's runs were refused by a saturated billing pool) | H100 80 GB  | 0.40  | $3.99/h | $1.60  |
| « l'aube »: H200 141 GB, secure cloud, 03:47–09:47 UTC (stopped by the spend guard at 6 h)                                                                                          | H200 141 GB | 6.00  | $5.29/h | $31.74 |
| « l'aube »: the same pod resumed for one reshoot and three 4K upscales, then terminated                                                                                             | H200 141 GB | 0.50  | $5.29/h | $2.65  |
