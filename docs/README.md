# Sovkora documentation

Everything here describes the one Sovkora repository (ADR-029). What the code *is* (modules, ports, routes, rules, packs,
models) is generated from the code into [`_generated/`](_generated/) and never written by hand; the pages below explain
*why* and *how*. Architecture decisions are internal; proposals start in
[GitHub Discussions](https://github.com/orgs/krizaka/discussions).

## Start here

| Page | For |
|:---|:---|
| [Quickstart](101.md) | your first run, end to end |
| [Concepts](CONCEPTS.md) and [Glossary](GLOSSARY.md) | the words Sovkora uses: intention, pack, Studio, capability, lane |
| [Install, update and back up](SELF_HOSTING.md) | running Sovkora on your own Mac or Linux GPU server |
| [Content policy](CONTENT_POLICY.md) | what the operator decides, and what Sovkora never serves |
| [Licensing](LICENSING.md) | FSL-1.1-ALv2 and the Apache-2.0 folders |

## Architecture

| Page | What it explains |
|:---|:---|
| [The C1 and C2, as code](architecture/README.md) | the containers, current and target, each bound to its folder |
| [Target architecture vision](VISION_ARCHITECTURE.md) | the hexagon, the pipeline, the lanes, the edge |
| [Microservices target](MICROSERVICES_TARGET_ARCHITECTURE.md) | the bounded contexts and their services |
| [Core engine](CORE.md) and [Interface contracts](INTERFACES.md) | the engine's ports and the contracts between contexts |
| [Worker protocol](WORKER_PROTOCOL.md) | how a worker speaks to the job lanes |
| [Billing, credits and metering](BILLING_ARCHITECTURE.md) | holds, settlements, the price book |
| [Asset encryption](ASSET_ENCRYPTION.md) and [Authentication](AUTH.md) | the envelope format; sessions, keys and service tokens |
| [Architecture assessment](ARCHITECTURE_ASSESSMENT.md) and [Production readiness](PRODUCTION_READINESS_AUDIT.md) | where the platform stands |

## Packs and Studios

| Page | What it explains |
|:---|:---|
| [Run a Studio](STUDIO_GUIDE.md) | runs, streams and signed webhooks |
| [Studios](STUDIO_ARCHITECTURE.md) | installable business workflows: blueprints, saga, entitlements |
| [The pack catalogue](PACK_CATALOGUE_ARCHITECTURE.md) and [Pack extensibility](PACK_EXTENSIBILITY_ARCHITECTURE.md) | the marketplace, workers, open core |
| [One surface for media](UNIFIED_PACK_SURFACE.md) | why media generation is a pack too |
| [Cinematic shorts](CINEMATIC_SHORTS.md) and [Automations](AUTOMATION.md) | two families of Studios in practice |
| [Business implementation playbook](BUSINESS_IMPLEMENTATION.md) | building a product's domain on Sovkora |
| The packs themselves | [`packs/`](../packs/README.md) and the generated [PACKS.md](_generated/PACKS.md) |

## The apps

| Page | What it explains |
|:---|:---|
| [UI reference](UI_REFERENCE.md) and [UX decisions](UX_DECISIONS.md) | the screens and why they look so |
| [Mobile](MOBILE.md) and [App versioning](APP_VERSIONING.md) | the mobile app; compatibility, forced updates, OTA |
| [Information architecture](ux/information-architecture.md) | the site's structure |

## Building, testing, running

| Page | What it explains |
|:---|:---|
| [DevX, environments and lifecycle](DEVEX_LIFECYCLE.md) | the local loop, from clone to running stack |
| [Integration and E2E tests](END2END_TEST.md) | Testcontainers, the hermetic journeys |
| [Dev environment](DEPLOY_DEV.md) and [Dev database](DEV_DATABASE.md) | dev.sovkora.com on App Platform, the shared cluster, migrations |
| [Prod pilot](DEPLOY_PROD.md) | api.sovkora.com for the Verikora pilot: declared as code, not deployed (ADR SOV-105) |
| [Delivery](DELIVERY.md) and [Infrastructure portability](INFRA_PORTABILITY.md) | packaging and signing; moving providers |
| Measurements and evaluations | [`measurements/`](measurements/), [`evaluations/`](evaluations/) |

## Generated from the code

| Page | What it lists |
|:---|:---|
| [MODULES.md](_generated/MODULES.md) | every module of the repository, its dependencies and the SQL it owns |
| [ARCHITECTURE.md](_generated/ARCHITECTURE.md) and [architecture.json](_generated/architecture.json) | the module map, dependencies, flows, pipeline, messaging |
| [API_REFERENCE.md](_generated/API_REFERENCE.md) and [ADMIN.md](_generated/ADMIN.md) | every endpoint with its authorisation rule; the admin side |
| [INTERFACE_CONTRACTS.md](_generated/INTERFACE_CONTRACTS.md), [USE_CASES.md](_generated/USE_CASES.md), [BUSINESS.md](_generated/BUSINESS.md) | ports, use cases, the business layer |
| [INTERCEPTORS.md](_generated/INTERCEPTORS.md), [TOOLS.md](_generated/TOOLS.md), [JOBS.md](_generated/JOBS.md) | the pipeline, tools and MCP, jobs and schedules |
| [PACKS.md](_generated/PACKS.md), [MODELS.md](_generated/MODELS.md), [MEASURED_RUNS.md](_generated/MEASURED_RUNS.md) | packs, the model catalogue, measured runs |
| [GOVERNANCE.md](_generated/GOVERNANCE.md), [CLI.md](_generated/CLI.md) | every governance rule and the suite that holds it; the CLI |

Regenerate with `node scripts/generate-docs.mjs` (a krizaka-java checkout at the imported release in `.krizaka-java/`);
CI fails a pull request whose generated docs are stale.
