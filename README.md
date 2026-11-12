<!-- krizaka-header -->
<div align="center">

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="https://raw.githubusercontent.com/krizaka/.github/main/profile/assets/sovkora-dark.svg">
  <img src="https://raw.githubusercontent.com/krizaka/.github/main/profile/assets/sovkora-light.svg" alt="Sovkora" width="96">
</picture>

# Sovkora

**The AI that never leaves home.**

**[Sovkora](https://www.sovkora.com)** is the source-available sovereign AI platform of [Krizaka](https://www.krizaka.com) (Montréal):
chat, documents, images, video and agents on your own machines. Every request crosses a deterministic pipeline to the best
local model: your documents, your models, your network, nothing leaves.

[![CI](https://github.com/krizaka/sovkora/actions/workflows/ci.yml/badge.svg)](https://github.com/krizaka/sovkora/actions/workflows/ci.yml)
[![OpenSSF Scorecard](https://api.scorecard.dev/projects/github.com/krizaka/sovkora/badge)](https://scorecard.dev/viewer/?uri=github.com/krizaka/sovkora)
[![License: FSL-1.1-ALv2](https://img.shields.io/badge/license-FSL--1.1--ALv2-blue.svg)](LICENSE)
[![Dev](https://img.shields.io/badge/dev-dev.sovkora.com-2f6fde)](https://dev.sovkora.com)
[![Quality Gate](https://sonarcloud.io/api/project_badges/measure?project=krizaka_sovkora&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=krizaka_sovkora)
[![Security Rating](https://sonarcloud.io/api/project_badges/measure?project=krizaka_sovkora&metric=security_rating)](https://sonarcloud.io/summary/new_code?id=krizaka_sovkora)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=krizaka_sovkora&metric=coverage)](https://sonarcloud.io/summary/new_code?id=krizaka_sovkora)

[Website](https://www.sovkora.com) · [Dev](https://dev.sovkora.com) · [Docs](docs/README.md) · [Krizaka](https://www.krizaka.com) · [Krizaka on GitHub](https://github.com/krizaka)

</div>
<!-- /krizaka-header -->

## What Sovkora is

A multimodal AI orchestration engine (chat, RAG, agents, image, audio, video) where every request crosses a
deterministic interceptor pipeline to the best **local** model. A product (Verikora, Folioviv) or a person brings a
question; Sovkora routes it through its packs and Studios, runs it on models you host, and accounts for every turn.
Québec Law 25 and GDPR by design: nothing leaves your network unless you say so.

**Unfiltered models, your rules.** Sovkora serves open-weight models without a vendor's refusal layer, so a security
team, a clinician, a reporter or a novelist gets a straight answer. The policy is the operator's: a pack declares what it
refuses (`scopeGuard`) and the crisis text it answers (`safety`), the engine enforces both before any model runs. It
never covers content sexualising minors, explicit sexual content, real persons, weapons or bio-chemical procedures,
working malware, or anything that enables serious harm.

## One repository

Everything Sovkora is lives here, in one tree (ADR-029, 2026-11-08): the Java reactor, the clients, the workers, the
packs, the infrastructure and the docs. Each folder kept its history (`git log -- <folder>`). The list of modules, with
what each is and what it depends on, is [`modules.json`](modules.json); the generated map is
[docs/_generated/MODULES.md](docs/_generated/MODULES.md).

```
sovkora/
├── build/                  the parent POM (krizaka-parent from Maven Central) and the governance test kit
├── contracts/              the platform contracts: job plane, persistence ports, secret sealing
├── engine/                 the engine: core, interceptors, tools, Spring AI, business, persistence, assets
├── services/               one Spring Boot deployable per bounded context
│   ├── edge/               the HTTP edge: routing, API key → JWT, CORS, tracing
│   ├── conversation/       chat (SSE), intents, models, MCP: the interactive ingress
│   ├── job/                the job lanes, the capability and worker registries
│   ├── knowledge/          RAG retrieval (pgvector) and the recommender
│   ├── automation/         connectors (Jira, Slack, WhatsApp…), schedules, CLI agents
│   └── studio/             the Studio and pack marketplace: blueprints, installations, runs
├── e2e/                    the cross-service journeys
├── workers/media/          the Python media and GPU worker (uv)
├── packs/                  the capability packs (Apache-2.0)
├── apps/                   the clients, one pnpm workspace
│   ├── web/                Next.js: the site, the Studios and the operator console (BFF)
│   ├── mobile/             Expo / React Native
│   ├── desktop/            Sovkora for Mac (Tauri 2)
│   └── cli/                the developer CLI (`orazaka`, on npm as @krizaka/orazaka-cli)
├── infra/                  local stack, dev deployment, migrations, OpenTofu
├── scripts/                the repository's tooling and its tests
├── tests/load/             the k6 load tests of the edge
└── docs/                   the documentation (start at docs/README.md)
```

## Architecture

Sovkora's C1 and C2, current and target, are code: [docs/architecture/sovkora.c4](docs/architecture/sovkora.c4)
(LikeC4, ADR-027). Each container is a folder of this repository (`repo 'sovkora'` + `path`).

**C1, the context.** People use the web app, the mobile app, the Mac app or the CLI; the products Verikora and Folioviv
call the edge with an API key. Sovkora runs the models it routes to on your machines (Ollama, sd-server, the media
worker on a GPU host) or, when the operator allows it, on a hosted provider. It relies on Krizaka's users and billing
services and on PostgreSQL, RabbitMQ and an object store.

**C2, the containers.**

| Container | Folder | What it does |
|:---|:---|:---|
| Web app and console | [`apps/web`](apps/web) | the site, chat, Studios and the operator console, with its server routes (the only door for a browser) |
| Mobile app | [`apps/mobile`](apps/mobile) | chat and Studios on the phone, against the edge with a Bearer token |
| Sovkora for Mac | [`apps/desktop`](apps/desktop) | installer and control panel, the folders the user picks, local automations |
| CLI | [`apps/cli`](apps/cli) | install, start, dev, test, docs and packs, for the local stack |
| Edge | [`services/edge`](services/edge) | the one HTTP entry (:8088): routes `/api/v1/**`, exchanges keys for JWTs |
| Conversation | [`services/conversation`](services/conversation) | chat over SSE, intents, models, the pipeline, MCP |
| Jobs | [`services/job`](services/job) | the interactive and batch lanes, the capability registry |
| Studio | [`services/studio`](services/studio) | packs, blueprints, installations and saga-driven runs |
| Knowledge | [`services/knowledge`](services/knowledge) | RAG retrieval and asynchronous indexing |
| Automation | [`services/automation`](services/automation) | connectors, Quartz schedules, execution telemetry |
| Engine (library) | [`engine`](engine) | the cognitive SDK every service runs: AiClient, interceptors, tools, routing |
| Media worker | [`workers/media`](workers/media) | images, video, sound and composition, on Metal or CUDA |
| Packs | [`packs`](packs) | the Studios and rules a tenant installs |

## Run it

Requirements: git, JDK 21, Node.js 22+, pnpm 12, Docker, Python 3.12 with uv (the media worker), macOS recommended for
native inference.

```bash
git clone https://github.com/krizaka/sovkora.git && cd sovkora
./mvnw verify                           # every Java module, unit and integration tests (Testcontainers)
pnpm install                            # the clients: apps/web, mobile, cli, desktop (each keeps its own lockfile)
npx orazaka start                       # the local infrastructure: Docker middleware and AI engines
npx orazaka dev                         # the whole stack: every service, the web app, the console, mobile
npx orazaka demo seed                   # second terminal, stack up: Eric, the demo persona
```

The users and billing services come from [krizaka-java](https://github.com/krizaka/krizaka-java), at the release
`build/sovkora-parent/pom.xml` imports (`krizaka.version`). The local stack and the docs generator read it from
`.krizaka-java/`, a checkout of that tag (git-ignored).

| Part | Build and test it |
|:---|:---|
| One Java module | `./mvnw verify -pl services/studio/sovkora-studio-service -am` (and `-amd` for what depends on it) |
| The web app | `pnpm --dir apps/web run validate && pnpm --dir apps/web run build` |
| The mobile app | `pnpm --dir apps/mobile run validate` |
| The CLI | `pnpm --dir apps/cli run validate` |
| The Mac app | `pnpm --dir apps/desktop run check` (macOS) |
| The media worker | `cd workers/media && uv sync && uv run pytest -q` |
| The packs | `cd packs && npm ci && npm test`, then each pack worker's `python -m pytest` |
| The tooling and its guards | `node --test scripts/test/*.test.mjs` and `node scripts/check-java-layout.mjs` |
| The generated docs | `node scripts/generate-docs.mjs` (`--check` in CI) |

Self-hosting (a Mac or a Linux server with an NVIDIA GPU, offline once the models are downloaded):
[docs/SELF_HOSTING.md](docs/SELF_HOSTING.md). Packaging and signing: [docs/DELIVERY.md](docs/DELIVERY.md). The dev
environment (dev.sovkora.com on DigitalOcean App Platform): [docs/DEPLOY_DEV.md](docs/DEPLOY_DEV.md).

## The packs

A pack is a folder of [`packs/`](packs) with a `pack.yaml` (validated against [`pack.schema.json`](packs/pack.schema.json)):
its Studios (semver'd blueprints), their texts in French and English, and, when it needs one, its own worker.

| Pack | What a tenant gets | Studios | Worker |
|:---|:---|:---:|:---:|
| [`document-validation`](packs/document-validation) | compliance check of a document against dated rules and their sources; authenticity signals of a PDF | 2 | yes |
| [`identity-document-check`](packs/identity-document-check) | the ICAO check digits and cross-checks of an identity document | 1 | yes |
| [`tenant-screening-qc`](packs/tenant-screening-qc) | the gated saga of a rental application: documents, court records, credit | 1 | yes |
| [`privacy-law25`](packs/privacy-law25) | Québec Law 25: privacy impact, incident register, consent texts | 4 | — |
| [`bill96-french-first`](packs/bill96-french-first) | Bill 96: French first, for a business's texts | 4 | — |
| [`message-authenticity`](packs/message-authenticity) | the authenticity signals of a message | 1 | yes |
| [`media-safety`](packs/media-safety) | the safety check of photos, video frames and voice messages (fails closed) | 1 | yes |
| [`profile-photo-check`](packs/profile-photo-check) | a profile photo is a real, single, visible face | 1 | yes |
| [`orazaka-media`](packs/orazaka-media) | the media toolkit: image, video, sound and art direction | 16 | — |
| [`data-analytics`](packs/data-analytics) | recommendations, product analytics, video understanding | 3 | — |
| [`outbound-prospection`](packs/outbound-prospection) | lead research, outbound angles and follow-up sequences | 3 | — |
| [`business-brief`](packs/business-brief) | a business brief from a few answers | 1 | — |
| [`code-contributor`](packs/code-contributor) | an agent that turns an issue into a pull request | 2 | — |
| [`realestate-studio`](packs/realestate-studio) | listing reels from room photos | 1 | — |
| [`trade-showcase`](packs/trade-showcase) | a tradesperson's showcase | 1 | — |
| [`wellbeing`](packs/wellbeing) | wellbeing Studios, with the crisis guard | 4 | — |
| [`echo-toolkit`](packs/echo-toolkit) | the reference used by the platform tests (draft, never on the shelf) | 1 | yes |

Writing a pack: [packs/README.md](packs/README.md) and the generated [docs/_generated/PACKS.md](docs/_generated/PACKS.md).

## Workflows

One CI for the whole repository, and a few workflows that run on their own schedule. Each says what it does in its name.

| Workflow | What it does | When |
|:---|:---|:---|
| [CI](.github/workflows/ci.yml) | the Java reactor (the changed modules and their dependents on a pull request), the web, mobile and CLI apps, the media and pack workers, the packs, the tooling and its guards, the generated docs, one SonarCloud scan; the gate `workspace` | every pull request, the merge queue, main, nightly |
| [Security scan](.github/workflows/security.yml) | secrets (gitleaks), workflows (zizmor), commit trailers | every pull request and the merge queue |
| [lang-guards](.github/workflows/lang-guards.yml) | the Rust of the Mac app; ruff and pyright on the media worker and the packs, counted in their ratchets | every pull request, main |
| [Desktop](.github/workflows/desktop.yml) | the Mac app's checks and, on a `desktop-v*` tag, the signed dmg | changes in `apps/desktop`, tags |
| [Dev image build](.github/workflows/dev-image-build.yml) | builds the dev `core` image (never pushed) | changes to the image, weekly |
| [Dev redeploy](.github/workflows/dev-redeploy.yml) | rebuilds dev when main moved; fails on a rollback, a stale source or a schema drift | every 15 minutes |
| [Dev persona smoke](.github/workflows/dev-persona-smoke.yml) | the demo persona signs in on dev | after a deploy, every 30 minutes |
| [Dev service keys](.github/workflows/dev-service-keys.yml) | each product's Sovkora service key answers 200 on the edge; a 401 fails the run naming the product | every 30 minutes |
| [Web live](.github/workflows/web-live.yml) | waits for the merged commit on dev, then the live smoke, the SEO contract, the console gate and the UI crawl | main, when `apps/web` changed |
| [Web videos](.github/workflows/web-videos.yml) | records the download page's videos from the deployed app | daily |
| [Mobile E2E](.github/workflows/mobile-e2e.yml) | the Maestro flows of the mobile app on an Android emulator | changes in `apps/mobile` |
| [Mobile release](.github/workflows/mobile-release.yml) | the Android APK on a `mobile-v*` tag; a TestFlight submission on demand | tags, on demand |
| [Media worker image](.github/workflows/media-image.yml) | the CUDA image of the media worker (`ghcr.io/krizaka/sovkora-media-worker`) | changes to the worker's image |
| [GPU smoke](.github/workflows/gpu-smoke.yml) | Sovkora's own engines on a rented GPU, through dev | on demand |
| [load-smoke](.github/workflows/load-smoke.yml) | the edge under the k6 smoke load, thresholds from the SLO table | changes to the edge or the load tests, weekly |
| [Packs golden stack](.github/workflows/packs-golden.yml) | the packs' goldens against a real stack, on the trusted runner | main, when enabled |
| [Docs regenerate](.github/workflows/docs-regen.yml) / [docs-sync](.github/workflows/docs-sync.yml) | keep `docs/_generated` in step with the code | pull requests, daily |
| [Dependabot auto-merge](.github/workflows/dependabot-automerge.yml), [merge resolution](.github/workflows/merge-resolution.yml), [Scorecard](.github/workflows/scorecard.yml) | the org's shared workflows | as their names say |

## Manual tools

Scripts a person runs by hand (ADR-026: nothing runs unlisted). Each opens with a header saying why, what, how and who owns it.

| Script | What it does | Doc |
|:---|:---|:---|
| `scripts/journey-dev.mjs` | replays the core journey (chat, image, analysis, clip) on the dev edge as the demo persona | [docs/DEPLOY_DEV.md](docs/DEPLOY_DEV.md) |
| `scripts/dev-persona-runs.mjs` | puts a few real Studio runs on a dev persona's shelf before a recording | its header |
| `scripts/dev-reset.mjs` | clears and re-seeds a product's dev databases | [docs/DEV_DATABASE.md](docs/DEV_DATABASE.md) |
| `apps/mobile/scripts/record-story.mjs` | records the mobile app's story clips | its header |
| `apps/mobile/scripts/frame-clips.mjs` | puts those clips in a device frame, with their captions | its header |
| `apps/web/scripts/record-dev-clip.mjs` | records a clip of the web app on dev | its header |

## Licence

Each folder kept the licence it had as its own repository ([NOTICE](NOTICE)):

- **FSL-1.1-ALv2** ([LICENSE](LICENSE)): any use except a Competing Use; each version becomes Apache-2.0 two years after it
  is published. It covers every folder without a licence of its own.
- **Apache-2.0**: [`build/`](build/LICENSE), [`contracts/`](contracts/LICENSE), [`services/edge/`](services/edge/LICENSE),
  [`apps/cli/`](apps/cli/LICENSE) and [`packs/`](packs/LICENSE).

External contributions sign the Krizaka Contributor Licence Agreement. Security reports: [SECURITY.md](SECURITY.md).
Agents and contributors read [AGENTS.md](AGENTS.md) first.
