<!-- krizaka-header -->
<div align="center">

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="https://raw.githubusercontent.com/krizaka/.github/main/profile/assets/orazaka-dark.svg">
  <img src="https://raw.githubusercontent.com/krizaka/.github/main/profile/assets/orazaka-light.svg" alt="Orazaka" width="96">
</picture>

# Orazaka

**The AI that never leaves home.**

**[Orazaka](https://www.orazaka.com)** is the source-available sovereign AI platform (Apache-2.0 after two years) by [Krizaka](https://www.krizaka.com) (Montréal) — chat, documents, images, video and agents on your own machines — and this is the **workspace** that assembles it. Website: [www.orazaka.com](https://www.orazaka.com).

[![CI](https://github.com/krizaka/orazaka/actions/workflows/ci.yml/badge.svg)](https://github.com/krizaka/orazaka/actions/workflows/ci.yml)
[![OpenSSF Scorecard](https://api.scorecard.dev/projects/github.com/krizaka/orazaka/badge)](https://scorecard.dev/viewer/?uri=github.com/krizaka/orazaka)
[![License: FSL-1.1-ALv2](https://img.shields.io/badge/license-FSL--1.1--ALv2-blue.svg)](LICENSE)
[![Orazaka](https://img.shields.io/badge/part%20of-Orazaka-f67e23)](https://github.com/krizaka/orazaka#repositories)
[![Website](https://img.shields.io/badge/site-dev.orazaka.com-f67e23)](https://www.orazaka.com)
[![Quality Gate](https://sonarcloud.io/api/project_badges/measure?project=krizaka_orazaka&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=krizaka_orazaka) [![Security Rating](https://sonarcloud.io/api/project_badges/measure?project=krizaka_orazaka&metric=security_rating)](https://sonarcloud.io/summary/new_code?id=krizaka_orazaka) [![Reliability Rating](https://sonarcloud.io/api/project_badges/measure?project=krizaka_orazaka&metric=reliability_rating)](https://sonarcloud.io/summary/new_code?id=krizaka_orazaka) [![Maintainability Rating](https://sonarcloud.io/api/project_badges/measure?project=krizaka_orazaka&metric=sqale_rating)](https://sonarcloud.io/summary/new_code?id=krizaka_orazaka) [![Coverage](https://sonarcloud.io/api/project_badges/measure?project=krizaka_orazaka&metric=coverage)](https://sonarcloud.io/summary/new_code?id=krizaka_orazaka)

[Website](https://www.orazaka.com) · [Documentation](https://www.orazaka.com/docs) · [Krizaka](https://www.krizaka.com) · [Krizaka on GitHub](https://github.com/krizaka)

</div>
<!-- /krizaka-header -->

<p align="center">
  <br><sub>Eric answers a customer from his own returns policy, then generates the product photo — everything ran on one Mac, nothing left it. <a href="https://www.krizaka.com/en/products/orazaka/demos">More demos</a></sub>
</p>

A multimodal AI orchestration engine — chat, RAG, agents, image, audio, video — where every request crosses a
deterministic interceptor pipeline to the best **local** model. Your documents, your models, your network: nothing
leaves it. Law 25 and GDPR by design.

**Unfiltered models, your rules.** Hosted assistants apply their vendor's refusal policy to every answer. Orazaka
serves open-weight models without that layer — Qwen 2.5, Dolphin Llama 3 and Gemma 3, abliterated or fine-tuned
without refusals ([`infra/migrations/2026-10-11-uncensored-models.sql`](infra/migrations/2026-10-11-uncensored-models.sql))
— so a security team, a clinician, a reporter or a novelist gets a straight answer, on your own machines. The policy
is the operator's: a pack declares what it refuses (`scopeGuard`) and the crisis text it answers (`safety`), the engine
enforces both before any model runs, and every turn is accounted for. "Uncensored" means not over-refusing legitimate
work; it never covers content sexualising minors, explicit sexual content, real persons, weapons or bio-chemical
procedures, working malware, or anything that enables serious harm. [See the answers](https://www.krizaka.com/en/products/orazaka/demos#unfiltered).

This repository is the entry point of the Orazaka platform. It holds the governance contract
([AGENTS.md](AGENTS.md)), the workspace manifest ([`orazaka.workspace.json`](orazaka.workspace.json)),
the local infrastructure (`infra/`), the hermetic end-to-end suite (`e2e/sovkora-e2e/`) and the
architecture documentation (`docs/`). Every component lives in its own repository so that any
Krizaka application can pick only what it needs.

## Repositories

### Orazaka foundation

| Repository | What it is |
|:---|:---|
| [`orazaka-build`](https://github.com/krizaka/orazaka-build) | Parent POM of every Orazaka JVM repository: inherits `krizaka-parent`, imports `krizaka-bom` 0.2.0 and the Spring AI BOM, and carries the Orazaka governance tests. |
| [`orazaka-contracts`](https://github.com/krizaka/orazaka-contracts) | Tier-1 platform contracts shared by every Orazaka service: the job plane (jobs-api) and the application-persistence ports (persistence-app-api). Pure interfaces and records, zero implementation. |
| [`orazaka-edge`](https://github.com/krizaka/orazaka-edge) | Transparent HTTP edge in front of every Orazaka service: routing, API-key → JWT exchange, CORS and tracing. Spring MVC + virtual threads. |
| [`orazaka-ui-kit`](https://github.com/krizaka/orazaka-ui-kit) | @krizaka/orazaka-shared (TypeScript types, Zod schemas, design tokens) and @krizaka/orazaka-design-system (React components, Tailwind preset, theme, icon registry) for Next.js and React Native apps. |

### Krizaka building blocks — reusable by any application

Released on [Maven Central](https://central.sonatype.com/namespace/com.krizaka) (`com.krizaka`, BOM 0.2.0) and
cloned under `krizaka/` so the reactor builds them from source. Documentation: [krizaka.com/docs/java](https://www.krizaka.com/docs/java).

| Repository | What it is |
|:---|:---|
| [`krizaka-build`](https://github.com/krizaka/krizaka-build) | Parent POM, `krizaka-bom` and the test kit (ArchUnit rules, Testcontainers helpers) of every Krizaka JVM repository. |
| [`krizaka-platform-kit`](https://github.com/krizaka/krizaka-build/tree/main/krizaka-platform-kit) | Web (problem details, correlation id), security (session JWT, `SERVICE` tokens), messaging (outbox, deduplication, retry / DLQ), observability — and their Spring Boot starters. |
| [`krizaka-users`](https://github.com/krizaka/krizaka-build/tree/main/krizaka-users) | Reusable user management: registration, e-mail verification, login (password + Google/GitHub OAuth), forgot/reset password, profile & preferences, API keys, RBAC and JWT issuance. |
| `krizaka-notifications` (modules of [`krizaka-platform-kit`](https://github.com/krizaka/krizaka-build/tree/main/krizaka-platform-kit)) | Channel-based notification delivery: e-mail (SMTP), SMS (Twilio) and webhooks behind one DeliveryClient port, driven by platform events or explicit requests over AMQP. |

### Orazaka AI engine

| Repository | What it is |
|:---|:---|
| [`orazaka-studio`](https://github.com/krizaka/orazaka-studio) | Studio & pack marketplace: pack catalogue, studio blueprints, installations and saga-driven runs, with its contract (studio-api) and HTTP client (studio-client). |
| [`orazaka-ai-engine`](https://github.com/krizaka/orazaka-ai-engine) | The Orazaka cognitive SDK: AiClient & provider mesh (core), the interceptor pipeline, tools (MCP · RAG · sandbox), use-case orchestration (business), application persistence and asset encryption. |
| [`orazaka-conversation-service`](https://github.com/krizaka/orazaka-conversation-service) | Interactive ingress of the Orazaka engine: chat (SSE streaming), intents, models, pipeline, MCP and job APIs — translates transport into Intentions, never business logic. |
| [`orazaka-job-service`](https://github.com/krizaka/orazaka-job-service) | Asynchronous job executor of the Orazaka engine: drains the interactive and batch lanes, owns the capability registry and the worker registry. |
| [`orazaka-knowledge-service`](https://github.com/krizaka/orazaka-knowledge-service) | Knowledge & RAG retrieval service (pgvector) with asynchronous indexing. |
| [`orazaka-automation-service`](https://github.com/krizaka/orazaka-automation-service) | Connector automation (Jira, Slack, WhatsApp, Messenger, CLI agents) with Quartz scheduling and execution telemetry. |
| [`orazaka-worker-media`](https://github.com/krizaka/orazaka-worker-media) | Native (Metal) Python worker for image/video generation and media composition, speaking the Orazaka AMQP worker protocol. |

### Applications & content

| Repository | What it is |
|:---|:---|
| [`orazaka-web-client`](https://github.com/krizaka/orazaka-web-client) | Next.js (App Router) client of the Orazaka platform with its mandatory BFF. |
| ~~`orazaka-web-admin`~~ | **Retired** — the operator console is served by [`orazaka-web-client`](https://github.com/krizaka/orazaka-web-client) on `admin.orazaka.com` (host routing). The repository is archived and deleted on 2026-10-23. |
| [`orazaka-mobile-client`](https://github.com/krizaka/orazaka-mobile-client) | Expo / React Native client of the Orazaka platform (talks to the edge with Bearer tokens). |
| [`orazaka-cli`](https://github.com/krizaka/orazaka-cli) | The `orazaka` developer CLI: install, start, dev, `demo seed`, test, docs and pack tooling for the Orazaka workspace, with an offline SQLite queue. |
| [`orazaka-packs`](https://github.com/krizaka/orazaka-packs) | Reference packs for the Orazaka Studio marketplace (document validation, media, prospection, real-estate, wellbeing…) and the pack manifest schema. |

## Dependency graph

```mermaid
graph LR
  krizaka_build --> krizaka_platform_kit
  krizaka_build --> krizaka_users
  krizaka_platform_kit --> krizaka_users
  krizaka_build --> krizaka_notifications
  krizaka_platform_kit --> krizaka_notifications
  krizaka_build --> orazaka_build
  orazaka_build --> orazaka_contracts
  orazaka_build --> orazaka_studio
  orazaka_contracts --> orazaka_studio
  krizaka_build --> orazaka_studio
  orazaka_build --> orazaka_ai_engine
  orazaka_contracts --> orazaka_ai_engine
  krizaka_build --> orazaka_ai_engine
  orazaka_studio --> orazaka_ai_engine
  orazaka_build --> orazaka_conversation_service
  orazaka_contracts --> orazaka_conversation_service
  krizaka_users --> orazaka_conversation_service
  krizaka_build --> orazaka_conversation_service
  orazaka_ai_engine --> orazaka_conversation_service
  orazaka_build --> orazaka_job_service
  orazaka_contracts --> orazaka_job_service
  krizaka_users --> orazaka_job_service
  krizaka_build --> orazaka_job_service
  orazaka_ai_engine --> orazaka_job_service
  orazaka_build --> orazaka_knowledge_service
  orazaka_build --> orazaka_automation_service
  krizaka_build --> orazaka_automation_service
  orazaka_build --> orazaka_edge
  orazaka_ui_kit --> orazaka_web_client
  orazaka_ui_kit --> orazaka_web_admin
  orazaka_ui_kit --> orazaka_mobile_client
```

Rules (AGENTS.md §2): a repository depends only on repositories **before** it in
`orazaka.workspace.json` (no cycle); a service depends on another context's **contract**
(`*-api`) or **client**, never on its implementation.

## Run Orazaka on your own infrastructure

On a Mac (Apple Silicon) or a Linux server with an NVIDIA GPU — your data never leaves your machine,
no telemetry, offline once the models are downloaded. Requirements, the 3 steps per target, update,
backup and uninstall: **[docs/SELF_HOSTING.md](docs/SELF_HOSTING.md)**. How it is packaged and signed:
[docs/DELIVERY.md](docs/DELIVERY.md). Security reports: [SECURITY.md](SECURITY.md).

## Quick start

Requirements: git, JDK 21, Node.js 22+, Docker, Python 3.11+ (media worker), macOS recommended for native
inference.

```bash
npx orazaka install                    # clones this workspace and every repository into ./orazaka, verifies the tools, configures it
cd orazaka
./mvnw install                         # builds and tests every JVM repository in one reactor
(cd orazaka-apps/ui && npm install)    # links the cloned UI repositories
npx orazaka start                      # local infrastructure: Docker middleware and AI engines
npx orazaka dev                        # the whole stack: every service, Web, Admin and Mobile
npx orazaka demo seed                  # second terminal, stack up: Eric, the demo persona (account, plan, packs, Studios)
```

Sign in on the web client as Eric — the account `orazaka demo seed` prints — and replay the demo above on your
own machine. `npx orazaka` is [`@krizaka/orazaka-cli`](https://www.npmjs.com/package/@krizaka/orazaka-cli) on npm
(`orazaka` is its short name; `npx @krizaka/orazaka-cli …` is the canonical form). By hand instead of `install`:
`git clone https://github.com/krizaka/orazaka.git && cd orazaka && node scripts/workspace.mjs clone`.

`node scripts/workspace.mjs status` shows the branch and state of every repository;
`node scripts/workspace.mjs pull` updates them all.

## Follow Studio runs without polling

Studio runs expose per-run and per-key Server-Sent Events streams, and signed `run.completed` /
`run.failed` webhooks for server-to-server integrations. Webhook endpoints are managed in **Settings →
Webhooks**; delivery is at-least-once, so receivers should deduplicate on `webhook-id`. See the
[Studio guide](docs/STUDIO_GUIDE.md#follow-a-run) for event resumption, signatures, retries, and the
delivery log.

## Grounded research (proposed)

The engine looking things up, reading the source and citing it, instead of answering from memory: ADR-087 and its [design](docs/design/grounded-research.md) (proposed, not built yet).

The compliance checks built from the law, on top of it — identify the document, read the dated articles, propose rules, promote them to dated rulesets: ADR-089 (proposed, not built yet).

## Manual tools

Scripts a person runs by hand (ADR-026: nothing runs unlisted). Each opens with a header saying why, what, how and who owns it.

| Script | What it does | Doc |
| :-- | :-- | :-- |
| `scripts/journey-dev.mjs` | replays the core journey (chat, image, analysis, clip) on the dev edge as the demo persona | [docs/DEPLOY_DEV.md](docs/DEPLOY_DEV.md) |
| `scripts/dev-persona-runs.mjs` | puts a few real Studio runs on a dev persona's shelf before a recording | its header |
| `scripts/dev-reset.mjs` | clears and re-seeds a product's DEV databases | [docs/DEV_DATABASE.md](docs/DEV_DATABASE.md) |

## Workspace layout

```
orazaka/                               ← this repository
├── AGENTS.md · orazaka.workspace.json · pom.xml (reactor)
├── infra/        docker-compose, initdb (reset), migrations
├── docs/         architecture, ADRs, generated references
├── e2e/sovkora-e2e/                   hermetic E2E
├── sovkora-libs/                      ← cloned: orazaka-build, orazaka-contracts, orazaka-ai-engine
├── krizaka/                           ← cloned: krizaka-build (with the platform kit), krizaka-users, krizaka-billing (both inside krizaka-build)
├── orazaka-apps/services/             ← cloned: orazaka-studio, conversation, job, knowledge, automation, edge
├── orazaka-apps/workers/              ← cloned: orazaka-worker-media
├── orazaka-apps/ui/                   ← npm workspace root; cloned: orazaka-ui-kit, web, mobile, cli
└── orazaka-packs/                     ← cloned: orazaka-packs
```

Cloned directories are ignored by this repository's git; each is its own repository.

## Building a new application from Orazaka components

1. Import `com.krizaka:krizaka-bom` 0.2.0 from Maven Central and add the starters you need
   (`krizaka-spring-boot-starter-web`, `-security`, `-rabbitmq`, `-observability`) — or inherit
   `com.krizaka.orazaka:orazaka-parent` (from [orazaka-build](https://github.com/krizaka/orazaka-build)) for the
   full Orazaka stack and quality gates.
2. Run the services you need — e.g. [krizaka-users](https://github.com/krizaka/krizaka-build/tree/main/krizaka-users) for
   registration / login / forgot password / profile, krizaka-notifications ([in krizaka-build](https://github.com/krizaka/krizaka-build/tree/main/krizaka-platform-kit/docs/notifications.md))
   for e-mail / SMS / webhook delivery, [krizaka-billing](https://github.com/krizaka/krizaka-build/tree/main/krizaka-billing) for credits and
   subscriptions, [orazaka-edge](https://github.com/krizaka/orazaka-edge) in front of them.
3. Depend only on their contracts (`krizaka-users-api`, `krizaka-notifications-api`,
   `krizaka-billing-api`) or clients.
4. Build the UI with [`@krizaka/ui`](https://www.krizaka.com/docs/ui) and the Orazaka identity,
   [`@krizaka/orazaka-design-system`](https://www.npmjs.com/package/@krizaka/orazaka-design-system).

## Consuming packages

- **Krizaka building blocks** (`com.krizaka`) are on **Maven Central** — no repository to declare. Import the BOM:
  `com.krizaka:krizaka-bom:0.2.0` ([all artifacts](https://central.sonatype.com/namespace/com.krizaka)).
- **npm packages** (`@krizaka/ui`, `@krizaka/orazaka-design-system`, `@krizaka/orazaka-shared`, …) are on the
  **public npm registry** — no `.npmrc`, no token.
- **Orazaka JVM artifacts** (`com.krizaka.orazaka`) are still published to GitHub Packages on a `v*` tag. Maven, in
  `~/.m2/settings.xml`:

```xml
<servers>
  <server><id>github</id><username>YOUR_GITHUB_USER</username><password>${env.GITHUB_TOKEN}</password></server>
</servers>
<!-- and a <repository> with id "github" per Orazaka repository you consume, e.g.
     https://maven.pkg.github.com/krizaka/orazaka-build -->
```

## License

[FSL-1.1-ALv2](LICENSE) (Functional Source License, Apache-2.0 future licence) © Technologies Krizaka Inc. — read,
run, modify and self-host it for any purpose except offering a competing service; each version becomes Apache-2.0 two
years after its release. Commits before the licence change remain [Apache-2.0](LICENSE-APACHE) — see [NOTICE](NOTICE)
and ADR-012.
The Krizaka names and birds are not licensed: [trademark policy](https://github.com/krizaka/.github/blob/main/TRADEMARKS.md).
