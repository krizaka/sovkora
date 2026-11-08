---
title: Modules
description: The folders of the one Sovkora repository — what each holds, what it depends on, and how they build together.
category: Architecture
order: 3
generated: true
---

# Modules

Sovkora is **one repository**, [`krizaka/sovkora`](https://github.com/krizaka/sovkora) (ADR-029), made of **15 modules**; the Krizaka building blocks come from Maven Central.

```bash
./mvnw verify          # every Java module, in one reactor
pnpm install           # the clients: apps/web, mobile, cli, desktop
```

## Krizaka building blocks — from Maven Central

| Module | Kind | What it holds | Depends on |
|:---|:---|:---|:---|
| [`krizaka-java`](https://github.com/krizaka/krizaka-java) | maven | The Krizaka Java building blocks (com.krizaka on Maven Central). Modules: `krizaka-billing-service`, `krizaka-notifications-service`, `krizaka-users-service`, `krizaka-billing-client`, `krizaka-payments`, `krizaka-quality`, `krizaka-rewrite-style`, `krizaka-users-client`, `krizaka-users-persistence`, `krizaka-users-core`, `krizaka-billing-api`, `krizaka-users-api`, `krizaka-app-config`, `krizaka-idempotency`, `krizaka-messaging`, `krizaka-notifications-api`, `krizaka-observability`, `krizaka-persistence`, `krizaka-ratelimit`, `krizaka-security`, `krizaka-spring-boot-starter-app-config`, `krizaka-spring-boot-starter-idempotency`, `krizaka-spring-boot-starter-observability`, `krizaka-spring-boot-starter-persistence`, `krizaka-spring-boot-starter-rabbitmq`, `krizaka-spring-boot-starter-ratelimit`, `krizaka-spring-boot-starter-security`, `krizaka-spring-boot-starter-web`, `krizaka-spring-boot-starter-webhooks`, `krizaka-web`, `krizaka-webhooks`. | — |

## Foundation

| Module | Kind | What it holds | Depends on |
|:---|:---|:---|:---|
| [`build`](https://github.com/krizaka/sovkora/tree/main/build) | maven | The parent POM sovkora-parent (krizaka-parent from Maven Central, the Spring Boot / Spring AI BOMs, the Sovkora BOM, the ADR-029 naming guards) and the shared governance test kit (ArchUnit rules, Testcontainers base). | — |
| [`contracts`](https://github.com/krizaka/sovkora/tree/main/contracts) | maven | Tier-1 platform contracts shared by every Sovkora service: the job plane (sovkora-jobs-api), the application-persistence ports (sovkora-persistence-api) and the secret sealing (sovkora-crypto). Pure interfaces and records. Modules: `sovkora-crypto`, `sovkora-jobs-api`, `sovkora-persistence-api`. | `build` |
| [`services/edge/sovkora-edge-service`](https://github.com/krizaka/sovkora/tree/main/services/edge/sovkora-edge-service) | maven | Transparent HTTP edge in front of every Sovkora service: routing, API-key → JWT exchange, CORS and tracing. Spring MVC + virtual threads. Modules: `sovkora-edge-service`. | `build` |

## The AI engine and its services

| Module | Kind | What it holds | Depends on |
|:---|:---|:---|:---|
| [`services/studio`](https://github.com/krizaka/sovkora/tree/main/services/studio) | maven | Studio & pack marketplace: pack catalogue, studio blueprints, installations and saga-driven runs, with its contract (studio-api) and HTTP client (studio-client). Modules: `sovkora-studio-service`, `sovkora-studio-client`, `sovkora-studio-api`. Owns `80-studio.sql`. | `build`, `contracts` |
| [`engine`](https://github.com/krizaka/sovkora/tree/main/engine) | maven | The Sovkora cognitive SDK: AiClient & provider mesh (core), the interceptor pipeline, tools (MCP · RAG · sandbox), use-case orchestration (business), application persistence and asset encryption, as sovkora-engine-* modules. Modules: `sovkora-engine-architecture`, `sovkora-engine-assets`, `sovkora-engine-persistence`, `sovkora-engine-persistence-bridge`, `sovkora-engine-springai`, `sovkora-engine-business`, `sovkora-engine-interceptors`, `sovkora-engine-tools`, `sovkora-engine-core`. Owns `60-governance.sql`. | `build`, `contracts`, `studio` |
| [`services/conversation/sovkora-conversation-service`](https://github.com/krizaka/sovkora/tree/main/services/conversation/sovkora-conversation-service) | maven | Interactive ingress of the Sovkora engine: chat (SSE streaming), intents, models, pipeline, MCP and job APIs — translates transport into Intentions, never business logic. Modules: `sovkora-conversation-service`. Owns `20-conversation.sql`. | `build`, `contracts`, `engine` |
| [`services/job/sovkora-job-service`](https://github.com/krizaka/sovkora/tree/main/services/job/sovkora-job-service) | maven | Asynchronous job executor of the Sovkora engine: drains the interactive and batch lanes, owns the capability registry and the worker registry. Modules: `sovkora-job-service`. Owns `30-jobs-config.sql`. | `build`, `contracts`, `engine` |
| [`services/knowledge/sovkora-knowledge-service`](https://github.com/krizaka/sovkora/tree/main/services/knowledge/sovkora-knowledge-service) | maven | Knowledge & RAG retrieval service (pgvector) with asynchronous indexing. Modules: `sovkora-knowledge-service`. Owns `40-knowledge.sql`. | `build` |
| [`services/automation/sovkora-automation-service`](https://github.com/krizaka/sovkora/tree/main/services/automation/sovkora-automation-service) | maven | Connector automation (Jira, Slack, WhatsApp, Messenger, CLI agents) with Quartz scheduling and execution telemetry. Modules: `sovkora-automation-service`. Owns `50-automation.sql`. | `build`, `engine` |

## Native workers

| Module | Kind | What it holds | Depends on |
|:---|:---|:---|:---|
| [`workers/media`](https://github.com/krizaka/sovkora/tree/main/workers/media) | python | Native (Metal) Python worker for image/video generation and media composition, speaking the Sovkora AMQP worker protocol. Modules: `worker-media`. | — |

## Applications

| Module | Kind | What it holds | Depends on |
|:---|:---|:---|:---|
| [`apps/web`](https://github.com/krizaka/sovkora/tree/main/apps/web) | pnpm | Next.js (App Router) client of the Sovkora platform with its mandatory BFF. Modules: `web`. | — |
| [`apps/mobile`](https://github.com/krizaka/sovkora/tree/main/apps/mobile) | pnpm | Expo / React Native client of the Sovkora platform (talks to the edge with Bearer tokens). Modules: `mobile`. | — |
| [`apps/cli`](https://github.com/krizaka/sovkora/tree/main/apps/cli) | pnpm | The developer CLI: install, start, dev, test, docs and pack tooling for the local Sovkora stack, with an offline SQLite queue. Modules: `cli`. | — |
| [`apps/desktop`](https://github.com/krizaka/sovkora/tree/main/apps/desktop) | pnpm | The Sovkora desktop app (Tauri 2, Rust and React): the local runtime, the code workspace and the Mac engines, one signed app. Modules: `desktop`. | — |

## Content

| Module | Kind | What it holds | Depends on |
|:---|:---|:---|:---|
| [`packs`](https://github.com/krizaka/sovkora/tree/main/packs) | packs | Reference packs for the Sovkora Studio marketplace (document validation, media, prospection, real-estate, wellbeing…) and the pack manifest schema. | — |

## Rules

- A module depends only on modules listed **before** it in `modules.json` — the graph has no cycle.
- A domain module holds its contract, its client and its service together; other modules depend on the contract or the client, never on the implementation.
- A module owns its context's bootstrap SQL (`infra/initdb/NN-*.sql`).
- The rules that read the whole tree run in the repository and are reported as skipped — never as passed — outside it.
