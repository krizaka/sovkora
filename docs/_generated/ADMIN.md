---
title: Administration
description: What an Orazaka administrator controls — the SecOps console, every ADMIN-only endpoint, and how the console is secured — extracted from the code.
category: Operations
order: 2
generated: true
---

# Administration

Everything commercial and operational in Orazaka is **data an administrator edits** — models, the
interceptor pipeline, capabilities, MCP servers, plans, prices, wallets, Studios — never a redeploy. An
administrator is an account holding `ROLE_ADMIN`, granted by the identity service; every
administrative endpoint checks it on the server (`@PreAuthorize`), whatever client calls it.

## The SecOps console (`web`)

A Next.js console served by `orazaka-web-client` on the admin host (`admin.sovkora.com`, host routing), started with the web client by `orazaka dev`.

| Screen | What it is for | Tabs |
|:---|:---|:---|
| `/` | `/` — the console opens on the platform's health: usage, credits, jobs, latency. | — |
| `/audit` | `/audit` — the audit trail of account administration. | — |
| `/billing` | The billing console. | Consommation · Clés des produits · Tarifs par modèle · Tarifs par capacité · Offres · Catalogue de prix · Packs métier · Portefeuilles |
| `/engine` | — | — |
| `/engine/tools` | — | — |
| `/law` | `/law` — Law → Proposals (ADR-089 §8): the review queue and promotion of the rules from the law, the corpus, the demand. | — |
| `/login` | — | — |
| `/models` | `/models` — the model registry, its providers and the capabilities that may be dispatched. | — |
| `/operations` | `/operations` — dependencies, queue depth, recent errors, runs and the deployed version. | — |
| `/studios` | The Studio Builder console (ADR-034 §12.4). | — |
| `/tasks` | `/tasks` — what needs an operator: refused or failed actions without a success since, failed jobs (ADR-085). | — |
| `/users` | `/users` — the platform's accounts: state, role, Law 25 export and erasure. | — |

![Billing — consumption per capability and model, refusals included](https://krizaka-assets.b-cdn.net/pr-assets/orazaka/admin-console/02-billing-usage.png)

*Billing — consumption per capability and model, refusals included.*

![Billing — the plans and what each one grants](https://krizaka-assets.b-cdn.net/pr-assets/orazaka/admin-console/05-billing-plans.png)

*Billing — the plans and what each one grants.*

![Billing — the price book: credits per unit for every capability and model, versioned](https://krizaka-assets.b-cdn.net/pr-assets/orazaka/admin-console/04-billing-pricebook.png)

*Billing — the price book: credits per unit for every capability and model, versioned.*

![Studios — the blueprint editor: versions, credits, steps and forms](https://krizaka-assets.b-cdn.net/pr-assets/orazaka/admin-console/09-studios-builder.png)

*Studios — the blueprint editor: versions, credits, steps and forms.*

> The console's screens are in French for now, and it ships no sign-in page yet
> (tracked in `orazaka-web-client`): every administrative
> action is also an endpoint, listed below.

### How the console is secured

- **Sign-in by credentials only** — no social login: the role that matters is granted by the identity service, and an account without `ROLE_ADMIN` is refused a console session.
- **A backend-for-frontend** — the browser never reaches a service port: `/api/v1/**` is proxied server-side, the session token injected as `Authorization: Bearer`.
- **No internal surface** — `/internal/v1/**` (credit holds and settlements, registries) is not in the edge's route table, so the console cannot reach it by construction.

### What the console calls

| Method | Path | Access | Summary |
|:---|:---|:---|:---|
| PUT | `/api/v1/features/{featureKey}` | ADMIN | Admin: toggles an existing capability's enabled state. |
| GET | `/api/v1/features/all` | ADMIN | Admin: all capabilities (enabled or not) from the database. |
| GET | `/api/v1/jobs` | authenticated | Retrieves a paginated list of jobs for the authenticated user (or all jobs for admins). |
| GET | `/api/v1/jobs/active-connections` | ADMIN | Retrieves the current active SSE connection count. |
| GET | `/api/v1/models/admin/audit` | ADMIN | — |
| GET | `/api/v1/models/catalog` | authenticated | Retrieves the complete AI model catalog definitions from the database. |
| GET | `/api/v1/models/providers` | authenticated | Retrieves all available AI provider names from the database. |
| GET | `/api/v1/status/health` | public | Returns sovereign readiness health details (public probe). |
| GET | `/api/v1/status/infrastructure` | authenticated | Returns the current online/offline snapshot of the local inference engines. |
| GET | `/api/v1/studios` | authenticated | The catalogue, optionally narrowed to one profession. |
| GET | `/api/v1/studios/{studioKey}` | authenticated | One Studio's detail, including the schemas its forms are generated from. |
| GET | `/api/v1/studios/{studioKey}/blueprints` | ADMIN | Every version of a Studio, drafts included — the Builder's version list. |
| DELETE | `/api/v1/studios/{studioKey}/blueprints/{version}` | ADMIN | Deprecates a version without deleting it. |
| PUT | `/api/v1/studios/{studioKey}/blueprints/{version}` | ADMIN | Creates or replaces a draft version. |
| POST | `/api/v1/studios/{studioKey}/blueprints/{version}/publish` | ADMIN | Publishes a draft, minting it as the Studio's latest version. |
| GET | `/api/v1/studios/admin/audit` | ADMIN | — |
| GET | `/api/v1/studios/admin/runtime-config` | ADMIN | — |

## Every ADMIN-only endpoint

What an administrator can do, by service — from the console, the CLI or any HTTP client with an
administrator's token (through the edge, `http://localhost:8088` locally).

### conversation-service

| Method | Path | Summary |
|:---|:---|:---|
| PUT | `/api/v1/features/{featureKey}` | Admin: toggles an existing capability's enabled state. |
| GET | `/api/v1/features/all` | Admin: all capabilities (enabled or not) from the database. |
| GET | `/api/v1/jobs/active-connections` | Retrieves the current active SSE connection count. |
| POST | `/api/v1/jobs/purge` | Purges jobs, chat sessions and rate-limit cache for the default test accounts. |
| GET | `/api/v1/mcp/servers/platform` | — |
| POST | `/api/v1/mcp/servers/platform` | — |
| DELETE | `/api/v1/mcp/servers/platform/{id}` | — |
| POST | `/api/v1/models` | Adds a new model definition to the catalog. |
| DELETE | `/api/v1/models/{id}` | Deletes a model definition by its database ID. |
| PUT | `/api/v1/models/{id}` | Updates an existing model definition in the catalog. |
| GET | `/api/v1/models/admin/audit` | — |
| GET | `/api/v1/models/admin/feature-cache-policies` | Every policy, the default row first. |
| DELETE | `/api/v1/models/admin/feature-cache-policies/{featureKey}` | Removes the policy a feature set for itself; the default row then applies. |
| PUT | `/api/v1/models/admin/feature-cache-policies/{featureKey}` | Sets the policy of a feature (* is the default row). |
| GET | `/api/v1/models/admin/feature-routes` | The feature routes, read only. |
| GET | `/api/v1/models/admin/feature-structured-bounds` | Every bound, the default row first. |
| DELETE | `/api/v1/models/admin/feature-structured-bounds/{featureKey}` | Removes the structured-output bound a feature set for itself; the default row then applies. |
| PUT | `/api/v1/models/admin/feature-structured-bounds/{featureKey}` | Sets the bound of a feature (* is the default row). |
| GET | `/api/v1/models/admin/feature-tool-bounds` | Every bound, the default row first. |
| DELETE | `/api/v1/models/admin/feature-tool-bounds/{featureKey}` | Removes a feature's own bound so it falls back to the default row. |
| PUT | `/api/v1/models/admin/feature-tool-bounds/{featureKey}` | Sets the bound of a feature (* is the default row). |
| GET | `/api/v1/pipeline/interceptors` | Retrieves all pipeline interceptor configurations ordered by execution order. |
| PUT | `/api/v1/pipeline/interceptors` | Bulk-updates pipeline interceptor ordering and enabled state. |
| POST | `/api/v1/pipeline/interceptors/reset` | Resets all pipeline interceptor configurations to hardcoded defaults. |
| GET | `/api/v1/pipeline/validation` | Retrieves all validation pipeline tier configurations ordered by execution order. |
| PUT | `/api/v1/pipeline/validation` | Bulk-updates validation pipeline tier ordering and enabled state. |

### studio-service

| Method | Path | Summary |
|:---|:---|:---|
| GET | `/api/v1/studios/{studioKey}/blueprints` | Every version of a Studio, drafts included — the Builder's version list. |
| DELETE | `/api/v1/studios/{studioKey}/blueprints/{version}` | Deprecates a version without deleting it. |
| PUT | `/api/v1/studios/{studioKey}/blueprints/{version}` | Creates or replaces a draft version. |
| POST | `/api/v1/studios/{studioKey}/blueprints/{version}/publish` | Publishes a draft, minting it as the Studio's latest version. |
| GET | `/api/v1/studios/admin/audit` | — |
| GET | `/api/v1/studios/admin/runtime-config` | — |
| PUT | `/api/v1/studios/admin/runtime-config/{key}` | — |
| GET | `/api/v1/studios/admin/runtime-config/audit` | — |
| POST | `/api/v1/studios/packs/bundles` | Installs a bundle. |
| DELETE | `/api/v1/studios/packs/bundles/{bundleKey}` | Removes a bundle's catalogue rows and withdraws its pack from sale. |
| POST | `/api/v1/studios/packs/bundles/validation` | Checks a bundle against this platform without writing anything. |
