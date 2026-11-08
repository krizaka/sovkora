# Sovkora — target module map (C2)

> The executable target of the rebuild epic [krizaka-internal#2927](https://github.com/krizaka/krizaka-internal/issues/2927).
> It turns the C2 target ([`sovkora.c4`](sovkora.c4), ADR-027) and the module catalogue ([`modules.json`](../../modules.json),
> ADR-029) into one table: for every target module, its folder, its one responsibility, the modules it may depend on, and
> the existing code that lives or moves there. The rules that hold this map are in
> [`scripts/architecture-target.mjs`](../../scripts/architecture-target.mjs), run by the non-required CI job
> `architecture-target` until the rebuild is done (then required, #2436). This file is **written by hand**; the generated
> module facts live in [`docs/_generated/architecture.json`](../_generated/architecture.json).

## How to read the dependency column

Dependencies are **build order** (`modules.json`): a module may depend only on modules listed **before** it, through the
**sharing tier** it is allowed to see (AGENTS.md §2.1):

- **Tier 1 — Contracts** (`*-api`, `contracts/`): pure interfaces + records, shared freely; the only way one context calls another.
- **Tier 2 — Platform SDK** (`engine/` core, interceptors, tools, test-support): shared with SDK discipline.
- **Tier 3 — Owned domain** (a service's `business`/`persistence`/`identity`): owned by **one** service, never shared across contexts.

A service may depend on Tier 1 + Tier 2 + **its own** Tier 3, never another context's Tier 3 ([SEAM-002]).

## The modules

| Module | Folder | Responsibility (single) | May depend on | Existing code that lives / moves here |
|:---|:---|:---|:---|:---|
| **build** | `build/` | The parent POM `sovkora-parent` (inherits `krizaka-parent` from Maven Central; Spring Boot/AI BOMs, Sovkora BOM, naming guards) and the governance test kit `sovkora-test-support` (ArchUnit rules, Testcontainers base). | — (foundation) | `build/sovkora-parent`, `build/sovkora-test-support` |
| **contracts** | `contracts/` | Tier-1 platform contracts: the job plane (`sovkora-jobs-api`), the application-persistence ports (`sovkora-persistence-api`), the asset sealing (`sovkora-crypto`). Pure interfaces + records. | build | `contracts/sovkora-jobs-api`, `-persistence-api`, `-crypto` |
| **studio** | `services/studio/` | Studio & pack marketplace: catalogue, blueprints, installations, saga-driven runs. The **only producer** of `job.*` work (its transactional outbox). | build, contracts | `sovkora-studio-{api,client,service}` |
| **engine** | `engine/` | The cognitive SDK (Tier 2), imported never run: `AiClient` & provider mesh (`core`), the interceptor pipeline, tools (MCP·RAG·sandbox), use-case orchestration (`business`), application persistence + asset encryption. | build, contracts, studio | `sovkora-engine-{core,interceptors,tools,springai,business,persistence,persistence-bridge,assets,architecture}` |
| **conversation** | `services/conversation/` | Interactive ingress: chat (SSE streaming), intents, models, pipeline, MCP & job APIs. Translates transport → `Intention`, **no business logic**. Still `:8080` behind the edge. | build, contracts, engine | `sovkora-conversation-service` (owns `20-conversation.sql`) |
| **job** | `services/job/` | Asynchronous job executor: drains the two lanes (interactive/batch), owns the capability registry and the worker registry, settles runs. `:8090`. | build, contracts, engine | `sovkora-job-service` (owns `30-jobs-config.sql`) |
| **knowledge** | `services/knowledge/` | Knowledge & RAG retrieval on pgvector, with asynchronous indexing. | build | `sovkora-knowledge-service` (owns `40-knowledge.sql`) |
| **automation** | `services/automation/` | Connector automation (Jira, Slack, WhatsApp, Messenger, CLI agents) with Quartz scheduling and execution telemetry; signed webhooks and schedules that start Studio runs. | build, engine | `sovkora-automation-service` (owns `50-automation.sql`) |
| **edge** | `services/edge/` | The one public port: routing, API-key→JWT exchange, CORS, tracing. Spring MVC + virtual threads. `:8088`. | build | `sovkora-edge-service` |
| **worker-media** | `workers/media/` | Native (Metal/CUDA) Python worker: image/video generation and media composition. An **external process** speaking only the AMQP wire contract — imports no service/engine code. | — (wire contract only) | `workers/media/app/*` (the media worker) |
| **web** | `apps/web/` | Next.js 16 (App Router) customer site + operator console on `admin.*`, with its mandatory BFF (server routes). | — (edge API over HTTP) | `apps/web` |
| **mobile** | `apps/mobile/` | Expo 57 / React Native client; calls the edge with Bearer tokens (never the BFF). | — (edge API over HTTP) | `apps/mobile` |
| **cli** | `apps/cli/` | The `orazaka` developer CLI: install, start, dev, test, docs and pack tooling, with an offline SQLite queue. On npm as `@krizaka/orazaka-cli`. | — | `apps/cli` |
| **desktop** | `apps/desktop/` | Sovkora for Mac (Tauri 2, Rust + React): local runtime, code workspace, Mac engines, one signed app (ADR-079). | — (runs the CLI as a sidecar) | `apps/desktop` |
| **packs** | `packs/` | Reference packs for the Studio marketplace (document-validation, media, prospection, real-estate, wellbeing…) and `pack.schema.json`. Apache-2.0. Pack workers are Python. | — | `packs/*` |

### Run beside the core, not folders of this repository

- **users** / **billing** — Krizaka Java building blocks (`com.krizaka:*` from Maven Central, krizaka-java), run in the Sovkora core (sign-up closed, billing meters-never-blocks in dev). Consumed through their Tier-1 `*-api` contracts only.
- **relay** (`infra/deploy/gpu-relay`, chisel), **gpu host** (RunPod/droplet, ADR-077), **db** (PostgreSQL 18 + pgvector, one DB+role per context), **broker** (RabbitMQ 4.2: `sovkora.jobs`, `sovkora.events`, `sovkora.dlx`).

### Target-only (`#planned`) and retiring (`#retiring`)

| Element | State | Decided by | Lands in a slice as |
|:---|:---|:---|:---|
| **Web search** (SearXNG, grounded answers that cite sources) | `#planned` | sovkora ADR-087 | a feature of `engine` + a `search` container |
| **Serverless GPU endpoint** (media jobs at zero idle) | `#planned` | sovkora ADR-078 | a `job` → serverless trigger |
| **Desktop → GitHub** (read issue, push branch, open PR; user approves each write) | `#planned` | sovkora ADR-093 | a `desktop` feature |
| **Operator audit read model** (broker → users) | `#planned` | sovkora ADR-085 | a `users`/broker binding |
| **Production web + per-product API key** | `#planned` | #2136, #2132 | deferred (local phase, §0) |
| **UI kit** (`@krizaka/orazaka-design-system`, `-shared`) | `#retiring` | .github ADR-029 | folds into `@krizaka/ui`, then its repo is deleted |

## Target feature list (from the C2 and the generated docs)

The target platform delivers, by module:

- **Chat** — token-streaming (SSE) conversations with models, the interceptor pipeline (security, token, context, translation, enrichment, reformulation, tooling, validation, governance), the two kill-switches (`disable-ai`, `orchestration.enabled`), credit holds metered never blocking (dev). *(conversation + engine)*
- **Studios & packs** — a pack marketplace, Studios installed at boot, saga-driven runs with a transactional outbox. *(studio)*
- **Jobs** — two lanes (interactive `job.text.*`/`job.media.analyze`, batch `job.media.generate`), a capability registry with `latency_class`, a worker registry, run settlement to billing. *(job)*
- **Media** — image & 9:16 video generation and composition on the GPU host, consumed as hosted video in the core. *(worker-media + job)*
- **Recommendations** — a products recommender on pgvector. *(knowledge)*
- **Automations** — cloud connectors and schedules (Jira, Slack, WhatsApp, Messenger, CLI agents), signed webhooks, execution history. *(automation)*
- **Sign-in & billing** — tokens, profiles, plans, wallets, packs held, credit holds, usage metering (a key + plan per product). *(users, billing)*
- **Clients** — the web app + operator console (BFF), the mobile app, the Mac desktop app (folder grants, local automations), the dev CLI (offline queue). *(apps/\*)*
- **Grounded web search** *(`#planned`, ADR-087)* and **serverless media at zero idle** *(`#planned`, ADR-078)*.

## The target rules, in two layers

The target is held at two levels, because one of them cannot run until the reactor compiles.

### Layer 1 — the source-level floor (`architecture-target`, green today)

The non-required CI job `architecture-target` runs [`scripts/architecture-target.mjs`](../../scripts/architecture-target.mjs), a pure
Node check that needs **no compiling reactor**, so it reports on every push:

1. **Java** — every `com.krizaka.sovkora.*` package sits under a `maven` module folder of `modules.json`, and each module's `pom.xml` declares only Sovkora dependencies on modules listed **before** it (build order, no forward/cyclic module dependency).
2. **TypeScript** — no app under `apps/*` imports from another app's sources (cross-app boundary).
3. **Python** — `workers/media` imports no `services/`, `engine/` or `contracts/` code (an external process speaks only the AMQP wire contract).

These three are **green today**: the consolidation ([sovkora#621](https://github.com/krizaka/sovkora/pull/621), 1dd33b45) already moved every
module into its target folder and renamed every package to `com.krizaka.sovkora.*`. The floor's job is now to keep that true — a
parallel slice that reintroduces a cross-module forward dependency, a cross-app import, or a worker→engine import turns it red. It
is **non-required** because it is new and because it does not yet carry Layer 2.

### Layer 2 — the deep architecture (ArchUnit at the one entry point, red today)

The layer rings (`business → core`, nothing calls up, `core` imports no outer layer), the sharing tiers ([SEAM-002]) and the
port/adapter rules are **bytecode** rules: the per-module `<Module>GovernanceTest` and the one entry point
`engine/sovkora-engine-architecture` (AGENTS.md §2.1, §12.1). They need the reactor to **compile**, which it does not today — so they
run inside the **`Java reactor`** CI job, which is **red on `main`** ([run 42450913058](https://github.com/krizaka/sovkora/actions/runs/42450913058),
1dd33b45). Greening the reactor is the first slice; it is only then that these rules can be asserted.

### How the job turns required

`architecture-target` is a **standalone workflow** ([`.github/workflows/architecture-target.yml`](../../.github/workflows/architecture-target.yml)),
deliberately separate from `ci.yml` — it is a guard, not a build-and-test step. It is **non-required**: branch protection does not
list it, so a red here never blocks a merge. It already triggers on `merge_group`, so the closing slice (#2933) turns it **required**
with one branch-protection flip, once the reactor compiles and the one ArchUnit entry point is wired into `ci.yml` (#2436). Until then
the deep rules are gated by the `Java reactor` job as each slice greens its module.

A violation in either layer is the target not yet reached, not a formatting gap.

## The slices

The rebuild is cut into disjoint-folder slices under the epic [#2927](https://github.com/krizaka/krizaka-internal/issues/2927), so parallel
Sonnet sessions never touch the same files (≤6 at a time). Each slice has its folder, its rules, its tests and a Done-when.

| Order | Slice | Folders | Clears |
|:---:|:---|:---|:---|
| — | [#2929](https://github.com/krizaka/krizaka-internal/issues/2929) — this PR: the map, the rules, the media-worker fix | `docs/`, `scripts/`, `.github/`, `workers/media` | `Media worker` |
| 1 | [#2930](https://github.com/krizaka/krizaka-internal/issues/2930) — Java reactor: foundation + engine | `build/`, `contracts/`, `engine/` | `Java reactor` (engine) |
| 2 | [#2931](https://github.com/krizaka/krizaka-internal/issues/2931) — the six Spring Boot services | `services/*` | `Java reactor` (services) |
| 1 ∥ | [#2932](https://github.com/krizaka/krizaka-internal/issues/2932) — the web app | `apps/web` | `Web app` |
| 4 | [#2933](https://github.com/krizaka/krizaka-internal/issues/2933) — turn `architecture-target` required + Sonar A/A/A | `.github/`, `engine/sovkora-engine-architecture`, whole-tree Sonar | `SonarCloud`, `workspace` gate |

Slice 2 needs slice 1 on `main` (services depend on `contracts/` + `engine/`). Slice 3 runs in parallel with slice 1. Slice 4 is
last: it is the one place a still-red check is allowed to flip to required.
