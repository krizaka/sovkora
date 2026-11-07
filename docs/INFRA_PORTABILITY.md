---
title: "Infrastructure portability and cost — local first, cheap and fast in the cloud"
description: "How Sovkora runs today on a Mac, on RunPod and on DigitalOcean; where latency and money actually go; an options matrix (serverless GPU, model loading on the fly, serving engines, routing, portability, UX) scored on cost, latency and on-prem fit; and a recommended path in phases, with the issues that carry it."
category: Guidelines
order: 22
---

# Infrastructure portability and cost

> Study ORAZAKA-INFRA-STUDY, 2026-10-21. Documentation only: no code, no paid resource. Two watchwords from the
> founder: (1) **portability, optimised for on-premises (local)**; (2) **cost in the cloud, with minimal latency and the
> smoothest experience**. Every number marked *(est.)* is an estimate to be replaced by a measurement; the others come
> from ADR-078 and `docs/measurements/`.

## 1. How Sovkora runs today (read from the code)

| Layer | Local (Mac / on-prem) | Cloud (dev) | Source |
|:---|:---|:---|:---|
| Stateful infra | `infra/docker-compose.yml`: pgvector, RabbitMQ, Redis, Jaeger, Mailpit, Lago | App Platform + managed PG + a small broker Droplet | `infra/docker-compose.yml`, `infra/deploy/*.app.yaml` |
| Chat / vision LLM | Ollama (`:11434`), native | **Provider-served, per token** (`do-inference`, Mistral 3 14B, Nemotron VL) | `infra/deploy/dev-data.sql` |
| Speech | LocalAI (`:8085`, Metal) | not deployed | `infra/localai/README.md` |
| Image | sd-server (stable-diffusion.cpp, `:8086`, Metal) | same binary built for CUDA on the pod | ADR-075, `Dockerfile.cuda` |
| Video | worker `:8188`, MLX / AnimateDiff (`mps`) | Wan 2.x, LTX on a RunPod H100 pod | `orazaka-worker-media/models.yaml` |
| Model routing | `DynamicModelRouter` over `ai_providers` + `orazaka_models` (DB), client cached per (base-url, model) | same DB | ADR-030, `DynamicModelRouter.java` |
| Worker contract | AMQP `job.video.*` / `job.compose.*` / `job.sound.*`, declared in `worker.yaml` | same, through the chisel relay (`gpu-relay`) | ADR-038, ADR-077 |
| GPU lifecycle | n/a | `scripts/gpu-dev.sh runpod`, boot `infra/gpu/runpod-bootstrap.sh`, pod watchdog, launchd spend guard (idle 20 min, max 6 h) | ADR-078 |

What is already portable, and should be protected: the **queue contract** (the worker sees routing keys, never a
product), the **catalogue as data** (`orazaka_models` names the model, `models.yaml` says how a worker runs it, and
refuses by name what it does not describe), the `hardware: [cuda | mps | remote]` tag per model, pinned revisions, and
weights kept off the image (`/models`).

## 2. Where the latency and the money go

1. **Idle GPU.** The H100 pod bills $3.99/h whether or not it renders. Measured 2026-10-16: 25 clips in 5 h, 2.8 h
   rendering, 2.2 h waiting, so $0.80 per clip against $0.19 for a busy pod (ADR-078). Idle is the largest single line.
2. **Cold start.** Pod boot builds the venv in place (about 15 min of billed GPU when the volume has none, see
   `runpod-bootstrap.sh`), then loads weights: 129 s for the 57 GB A14B from the network volume. `apt-get` and
   `pip install` run at boot.
3. **Weight I/O.** A 200 GB network volume ($14/month) holds 120 GB of weights; network-volume reads are the load time.
   Re-downloading from Hugging Face costs $1-2 of pod time each time (ADR-078).
4. **Relay hop.** Every pod job crosses chisel (core to `gpu-relay` to pod), plus a sealed hand-over of outputs. It adds
   little latency per job but one more moving part and a fixed-port limit (orazaka#205: a second pod cannot join).
5. **Generation itself** is not the lever: 175 s per 5 s clip on the 5B. It is paid once; idle and load are paid again.
6. **LLM path.** In dev, chat is per token with no GPU to hold: cheap and fast to start. Cost appears only if Sovkora
   self-serves LLMs (on-prem clients, high volume), where the Ollama default (one model, little concurrency, no shared
   prefix cache) is the ceiling.

Conclusion: the order of attack is **idle, then load, then batching, then engine**. An engine swap (vLLM) saves nothing
while the GPU waits for work.

## 3. Options matrix

Scores: cost and latency for the cloud watchword, **on-prem fit** for the first watchword (high = runs unchanged on a
client's Mac or Linux/NVIDIA box). Numbers *(est.)* unless cited.

### 3.1 Serverless GPU, scale to zero

| Option | Cost | Latency (cold / warm) | On-prem fit | Verdict |
|:---|:---|:---|:---|:---|
| RunPod Serverless, flex + FlashBoot | per second, $0 idle; H100 about $0.0016/s (ADR-078, to confirm); active workers cheaper | RunPod claims sub-200 ms container start with FlashBoot; model load is ours (60-130 s from volume) | Medium: same image, handler adapter only | **Spike first** (ADR-078 item 2): our vendor, volume and region already in place |
| Modal | per second, $0 idle | GPU memory snapshots reported 118 s to 12 s cold start for a 3B LLM (alpha, Modal's own figure) | Low: proprietary SDK | Comparator only (orazaka#324), not adopted |
| Replicate-style (per prediction) | per second plus margin | minutes on custom models *(est.)* | Low | Refused: loses control of weights and licences |
| Provider-served inference (DO) | per token / per clip ($0.60 A14B 832x480) | no cold start | None | Keep as a **route** in the DB, not as the platform |
| Spot / community pods + checkpointed jobs | 50-70 % under on-demand *(est.)* | interruptions | High | Batch only (cinema), after a checkpoint contract |

### 3.2 Loading models on the fly

| Technique | Effect | On-prem fit | Verdict |
|:---|:---|:---|:---|
| Stage weights from the network volume to local NVMe at boot, memory-map safetensors | the 129 s load may drop 2-4x *(est.)*; measure first | High | **Do now** (cheap) |
| Image holds the runtime only, weights on the volume (already true) + slim image (orazaka#316) | pull time and disk fee | High | Continue |
| Cached-model features of the platform | skips download | Low | Check at the spike; never a dependency |
| Lazy / streamed loading (safetensors streaming, Run:ai Model Streamer, Apache-2.0) | tensors read in parallel from NVMe straight to the GPU | High | **Do now** with FP8 weights (section 7.1) |
| Warm-container snapshot (CRIU, CUDA checkpoint, GPU memory snapshot) | 5-10x on cold start for vLLM-class servers (Modal reports ~10x) | Low today (root, driver-bound, young) | Watch |
| Keep hot by traffic (LRU per VRAM budget, pre-load on queue depth) | hides load for repeat models | High | **Do** in the worker |

### 3.3 Serving engines

| Option | Gain | On-prem fit | Verdict |
|:---|:---|:---|:---|
| vLLM or SGLang (continuous batching, prefix and KV cache, multi-LoRA on one base) | several times Ollama throughput under concurrency *(est.)* | Linux/NVIDIA only | Provider row type `openai-compatible`; adopt when self-served LLM volume exists |
| Quantisation AWQ / GPTQ / FP8 | about 2x memory and speed *(est.)*; FP8 needs Ada/Hopper (sm_89+, our target) | High on NVIDIA | With vLLM, a flag per model |
| llama.cpp / MLX on Macs | already the local path (Ollama, MLX, sd.cpp) | **Highest** | Keep as the reference local engine |
| Video: distilled few-step models (LTX distilled, Wan 4-step I2V already in `cinema`) | fewer steps per clip | High | Continue, measure per clip |

### 3.4 Routing (`DynamicModelRouter`, DB)

Today the router resolves **one** endpoint per model name and caches the client. No fallback, no health, no cost or
latency awareness, no response cache. The DB already carries what is needed to go further (`supported_hardware`,
`is_default`, `category`, provider rows).

| Option | Effect | Verdict |
|:---|:---|:---|
| Ordered fallback chain + circuit breaker per provider | availability; local answers when the cloud is down and the reverse | **Do (P1)** |
| Capability-based selection (modality, hardware, context, cost, p95) | one API whatever the backend | After the chain |
| Cascade: small model first, escalate on a declared signal | 30-70 % of chat token cost *(est.)* | Opt-in per Studio step |
| Exact then semantic response cache (pgvector is there) | no cost and tens of ms on a hit | Exact match first (a seed makes a run repeatable) |
| Batching media jobs into windows | amortises one load over N clips | Use the existing queue (RabbitMQ), not Redis |

### 3.5 Portability

| Option | Verdict |
|:---|:---|
| One runtime contract: queue bindings (`worker.yaml`), model description (`models.yaml`), `/models` layout, health and registration | Exists; write it as the contract and test it per target |
| One image, three targets: Docker (Linux/NVIDIA), k3s, RunPod (pod and serverless handler) | **Do**: the pod stops rebuilding the runtime in place |
| OCI model store: weights as digest-pinned OCI artifacts, `orazaka models pull` from a registry or an offline bundle | Do; serves air-gapped clients and reproducibility |
| Same API whatever the backend | Already the rule (ADR-030); add a contract test per provider row type |

### 3.6 User experience

| Option | Verdict |
|:---|:---|
| Stage events in the run stream (`queued`, `loading_model`, `rendering`, `uploading`) with an ETA from past durations: a cold start becomes an honest progress step | **Do (P1)**: numbered run events and `Last-Event-ID` exist (ADR-083, #189) |
| First-token streaming for chat steps (SSE) | Verify end to end; no new work if so |
| Optimistic UI (a Studio run appears at submit) | Client side, already the pattern |

## 4. Recommended path, in phases

**Phase 0 - see (now).** Per-run timing fields (queue wait, cold start, model load, render, upload) in the
measurements (orazaka#315). Nothing below is sized without them.

**Phase 1 - stop paying for idle and for loading.** (Superseded by section 7: one active plan, no date.)
1. Serverless spike with a decision gate (RunPod flex, FlashBoot, 5B and FLUX, same image, weights on the volume).
2. Model-cache contract: one `/models` layout, stage to local NVMe, verify by digest, same on Mac, pod and k3s.
3. Stage events in the run stream: a 60-130 s cold start is a progress step, not a freeze.
4. Router fallback chain with a circuit breaker.
5. On-prem packaging: a GPU compose profile and a k3s manifest of the **same** worker image, no RunPod needed.

**Phase 2 - cost per unit.** Keep-hot LRU in the worker, cascade and exact-match cache in the
router, offline model bundle (OCI) with `orazaka models pack/import`.

**Phase 3 - volume (when measured traffic asks).** vLLM provider type with FP8/AWQ for self-served LLMs, spot pods for
checkpointed batches, multi-LoRA. **Phase 4 - watch**: GPU snapshot/restore, lazy weight streaming.

Expected effect if the spike confirms ADR-078's arithmetic *(est.)*: cost per clip from about $0.80 (guarded pod, mixed
use) to $0.28-0.49, GPU at $0 at rest without a local guard; cold start visible instead of silent; an on-prem client
runs the same image with no cloud account.

## 5. Issues

The existing GPU backlog (orazaka#157, #205, #206, #315, #316, #320-#324, worker-media#28) is not duplicated. This
study adds the router, the cache contract, the on-prem packaging, the UX events and the spike gate. The issues are
listed in section 7 and in milestone « Infra portability » (no date).

## 6. Refused for now

Modal or Replicate as the platform (lock-in); GPU snapshot/restore (immature on our drivers); a second queue technology
(Redis) for batching; the vLLM *provider row* stays data, and the GPU-server profile with prefix caching is now in the plan (section 7.5); any change of the product API for portability reasons.

## 7. Founder decision, 2026-10-21: one active plan, no date

The 2026-11-15 date is dropped. The only goal is the best open-source architecture and the lowest infrastructure cost.
There is no « before » and « after » split: all nine issues are in one milestone, **Infra portability** (orazaka,
orazaka-ai-engine, orazaka-worker-media, orazaka-cli). Order of work: 7.1 and 7.3 first (the 129 s and the cost per
clip), then 7.4 (cache), then the rest. Every figure below is an estimate *(est.)* until measured on a real pod.

### 7.1 Cold start 129 s to under 12 s, and faster diffusion (worker-media#63)

What the worker really runs today (`models.yaml`, read 2026-10-21): **Wan 2.2 TI2V 5B** and **Wan 2.1 T2V 1.3B**
(diffusers, BF16, Apache-2.0), **FLUX.2 [klein] 4B** (GGUF Q8_0 + Qwen3-4B GGUF through `sd-server`, Apache-2.0),
AnimateDiff on the Mac (MPS). Wan 2.1 14B and Wan 2.2 A14B are **not** in the worker: A14B (the 57 GB / 129 s case of
ADR-078) is a provider-served route. The Wan-AI repositories hold no FP8 file (checked on the Hub), so the FP8
checkpoints are **produced by us** (Apache-2.0 allows derivatives), digest-pinned, and published in the model store.

| Lever | What | Hardware | Licence (read in its repository, 2026-10-21) |
|:---|:---|:---|:---|
| FP8 `safetensors` (E4M3, per-tensor scales) | half the bytes to read and to hold in VRAM | native FP8 tensor cores on Hopper (H100, sm90) and Ada (L40S, 4090, sm89); **BF16 path kept** for Ampere (A100) and Mac | model licences unchanged (Apache-2.0) |
| `runai-model-streamer` (or direct mmap NVMe to GPU) | tensors read in parallel, no full-file copy through host RAM | any NVMe | Apache-2.0 (run-ai/runai-model-streamer) |
| TeaCache | skips redundant denoising steps by timestep-embedding similarity | any CUDA | Apache-2.0 (ali-vilab/TeaCache); last push 2025-06 and written for Wan 2.1: must be proven on Wan 2.2 |
| SageAttention (INT8/FP8 attention) | faster attention | sm80+ (INT8), sm89/sm90 (FP8) | Apache-2.0 (thu-ml/SageAttention) |

Target: 34 GB (5B, BF16) to about 17 GB (FP8); under 12 s needs about 1.4 GB/s sustained, within one Gen4 NVMe. The
network volume stays the source of truth, NVMe is the stage. For a 57 GB model (A14B) FP8 gives about 29 GB, which needs
2.4 GB/s: it will be measured, not promised. Inference: TeaCache + SageAttention, 2 to 2.5x faster *(est.)*.
FLUX.2 stays on `sd-server` (GGUF); its FP8 and streaming path is checked, not assumed.

### 7.2 Serverless GPU, scale to zero (orazaka#342)

Scale to zero is coupled to the RabbitMQ queue (`orazaka-job-service` publishes, the worker consumes): the queue depth
wakes a worker; no depth, no GPU. A smart `idle_timeout` of about 60 s after a job absorbs a user's burst with the
weights hot in VRAM, then a strict scale to $0. It works with the keep-hot manager (worker-media#65): the model stays
resident exactly as long as the idle window.

Economic target *(est.)*: cost per clip = (load + n x render + idle) x price per second / n. With the H100 pod price
($3.99/h = $0.00111/s), FP8 + NVMe load 12 s, render about 70-80 s with TeaCache and SageAttention, idle 60 s: a lone
clip about $0.17, a burst of 3 about $0.11, a burst of 5 about $0.10. Target **under $0.12-0.15 per clip** on bursts,
against $0.80 mixed today. The serverless price per second (ADR-078's $0.0016/s) is unconfirmed and moves these figures.

### 7.3 Deterministic real-time progress (worker-media#64)

The worker publishes granular events on **RabbitMQ**, which the stack already runs in dev and in the cloud (Redis exists
only in the local compose and is not deployed, decision 2026-10-16): `COLD_START_WAKEUP`, `STREAMING_WEIGHTS_NVME` (real
percentage of tensors read), `INFERENCE_STEP` (step X / total, sliding ETA from the last runs of that model and
hardware), `SANDBOX_VERIFICATION`, `COMPLETED`. `orazaka-job-service` turns them into the existing numbered run events
(ADR-083, #189), served as SSE with `Last-Event-ID` resume. Optional on the wire (ADR-048).

### 7.4 Semantic cache and small model, in the interceptor chain (orazaka-ai-engine#40)

`SemanticCacheInterceptor` (Apache-2.0) in `orazaka-interceptors`, in Phase 2 **just before `RouterInterceptor`**, placed
by a `pipeline_interceptor_config` row (never `@Order`). On a hit it ends the turn through the existing
`PipelineShortCircuitException`: 0 GPU, and 0 or a reduced credit in `EntitlementInterceptor`. Store: PostgreSQL
pgvector (already in the stack; Redis is not deployed). Embeddings: a local model, `bge-m3` (MIT, multilingual, fits
EN/FR) or `nomic-embed-text-v1.5` (Apache-2.0, lighter, English-leaning), through an Ollama provider row. Partition
key `(tenantId/studioId, scopeGuardHash, modelTier)`, cosine threshold at least 0.94. The exact-match tier (hash of
tenant, model, parameters, messages) is checked first. Opt-in per pack or route, off by default for tools, RAG over
changing data, user-specific context and numbers that matter (invoices, taxes, leases: Akinaza, Yomizaka).
The non-negotiable safety conditions are the acceptance criteria of the issue. The opt-in cascade (small model first)
stays in the same issue, after the cache.

### 7.5 Prefix caching on the GPU-server profile (orazaka#343, ai-engine#39, ai-engine#41)

The unified image's GPU-server profile serves LLMs with **SGLang (RadixAttention)** or **vLLM
`--enable-prefix-caching`** (both Apache-2.0), behind the OpenAI-compatible API that `AiClient` already consumes
(`openai-compatible` provider row, ai-engine#41, folded into #343's deliverables). Why now: the 15-interceptor pipeline
(Safety, ScopeGuard, BrandContext, Rag, Tool) builds a long system prefix that recurs on every call; prefix caching
saves 60-80 % of GPU time and time to first token *(est., to measure)*. Ollama or LocalAI stay in `AiClient` as the
automatic local / offline fallback (ai-engine#39): used when the remote GPU worker is off or the deployment is fully
on-prem.

### 7.6 Also in the plan

Keep-hot resident models (worker-media#65, aligned with `idle_timeout`); offline OCI model bundle with signed digests
(orazaka-cli#81, it carries the FP8 checkpoints and the model-cache layout).
