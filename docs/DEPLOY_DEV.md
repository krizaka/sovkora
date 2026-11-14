# Sovkora in dev — dev.sovkora.com and admin.sovkora.com

The development environment runs on DigitalOcean App Platform, project `orazaka`, Toronto, as cheaply as the platform
allows (decision 2026-10-14: everything Sovkora in dev ≤ $80/month; a GPU never stays on). Sign-in is open; sign-up is
closed everywhere — the fronts hide it and the users service answers **403** (`AUTH_SIGNUP_ENABLED=false`).

## What runs

| App (DO) | Component | What | Size | $/month |
|---|---|---|---|---|
| `sovkora-dev` | `web` | `krizaka/orazaka-web-client` (Next.js + BFF), `dev.sovkora.com/` | `apps-s-1vcpu-0.5gb` | 5 |
| `sovkora-dev` | `core` | one image, seven JVMs and the document-validation worker (below), public only as `dev.sovkora.com/edge` | `basic-s` (2 GB) | 20 |
| Droplet | `orazaka-broker-dev` | RabbitMQ 4.2, **TLS only** (5671), bound to its private IP, cloud firewall: VPC `10.118.0.0/20` only | `s-1vcpu-512mb-10gb` | 4 |
| `sovkora-dev` | `web` (same component) | the operator console, `admin.sovkora.com` = an alias of `dev.sovkora.com`, served by host routing (§ Admin console), BFF → `http://core:8088` | — | 0 |
| — | databases | the shared cluster `krizaka-dev-pg` (PostgreSQL 18, project `krizaka`, docs/DEV_DATABASE.md) | — | (15, shared) |
| — | chat | DigitalOcean serverless inference, `mistral-3-14B`, per token | — | ≈ 0 at test volume |
| `sovkora-dev` | `gpu-relay` | chisel server, `dev.sovkora.com/gpu-relay` — the GPU host dials out to it (§ GPU) | `apps-s-1vcpu-0.5gb` | 5 |
| — | images, 9:16 video | an hourly GPU droplet, only while started (`scripts/gpu-dev.sh`) | `small` / `big` | 0.76 / 1.57 **per hour** |
| — | model weights | Volume `orazaka-gpu-models-<region>`, 80 GB, kept between droplets | — | 8 |

**Total: $47/month** for Sovkora's own resources (+ its share of the $15 cluster, + tokens).

**Organisation total, measured 2026-10-21 (`doctl`, recurring cloud, GPU apart): about $97/month** — ten web services at $5, the Sovkora core $20, `gpu-relay` $5, the fourth static site $3, the shared PostgreSQL $15, the broker droplet $4 — under the $100 ceiling; about **$87** once `orazaka-admin-dev` (rollback until 2026-10-23) is deleted. There is no RunPod network volume on the account (the earlier « ≈ $14 volume » line was wrong); the GPU is billed by the hour, on use. Dev services run on the smallest instance that fits (`apps-s-1vcpu-0.5gb`; the JVM core cannot go below `basic-s`). Upgrading `core` to
`basic-m` (4 GB, 2 vCPU, $40) is the first lever if it restarts under load: still $54.

### The `core` container

`infra/deploy/Dockerfile` builds from this tree (and krizaka-java at the imported release for users and billing), builds the seven services
without tests, and `infra/deploy/run-services.sh` starts them side by side; the first JVM to exit takes the container
down so App Platform restarts the set.

| Service | Port | Heap | Database | Why it is here |
|---|---|---|---|---|
| `krizaka-users-service` | 8083 | 160 MiB | `krizaka_users_db` | sign-in, JWT, profile — sign-up closed |
| `krizaka-billing-service` | 8095 | 160 MiB | `krizaka_billing_db` | plans, wallets, packs held, credit holds — enforcement `DRY_RUN` (meters, never blocks) |
| `orazaka-job-service` | 8090 | 256 MiB | `orazaka_db` | capability registry, text jobs of Studio runs |
| `orazaka-conversation-service` | 8080 | 320 MiB | `orazaka_db` | chat (SSE), models, bootstrap features |
| `orazaka-studio-service` | 8096 | 160 MiB | `orazaka_studio_db` | Studios, packs (installed at boot), runs |
| `orazaka-knowledge-service` | 8084 | 128 MiB | `orazaka_knowledge_db` | the products' recommender (ADR-078, pgvector) |
| `orazaka-automation-service` | 8091 | 128 MiB | `orazaka_automation_db` | cloud automations: signed webhooks and schedules that start Studio runs (ADR-084), run history |
| `orazaka-edge` | **8088** | 96 MiB | — | the only routed port: `/` → conversation, `/api/v1/auth…` → users, `/api/v1/studios` → studio, `/api/v1/recommendations` → knowledge, `/api/v1/automations` and `/hooks/` → automation |

Measured on 2026-10-14 against the cluster (five JVMs, serial GC, C1 only, virtual threads): **1.3 GB resident**
idle and after a round of sign-ins and Studio listings — the reason for a 2 GB container.

### Core memory (orazaka#499)

Eight JVMs and two Python workers ran `basic-s` (2 GiB) at 93-99 % and restarted several times an hour (the OOM, orazaka#498).
Cost comes before size: the first answer is the JVM footprint, measured with `docker --memory=2g` on the image's jars
(`infra/deploy/core-memory-measured.json`, guarded by `scripts/test/core-memory-budget.test.mjs`).

| | Before | After |
|---|---|---|
| Container, eight JVMs, steady | 2042 MiB (at the limit) | 1502 MiB |
| Largest JVMs (job / conversation / users) | 390 / 341 / 324 MiB | 299 / 315 / 256 MiB |
| Heap ceilings (sum) | 1408 MiB | 1040 MiB |
| Time until the eight ports answer | 80 s | 55 s |

What moved it: `MALLOC_ARENA_MAX=2` and `-XX:ActiveProcessorCount=2` / `-XX:CICompilerCount=1` (the container sees the host's 8 CPUs: every
pool and arena was sized from it), a heap that gives back what it does not hold (`-Xms16m`, free ratios 10-30 %), no perf-data file, heap
ceilings from the measured live set (18-49 MiB at idle). The rest of a JVM (about 100-250 MiB) is class metadata of the Spring and
ai-engine stacks: only fewer JVMs, or fewer loaded libraries, reduces it. With the two workers (68 + 74 MiB) the steady set is about
1.65 GiB, 80 % of the container. Not done: AppCDS (the archive must be trained against live databases at image build), lazy bean
initialisation (RabbitMQ listeners would not start), dropping automation (-143 MiB; a paused feature is the founder's call).

**Not deployed** (and why): the automation service's connector stubs (Jira, Slack… log only; the service runs for ADR-084), notifications (no e-mail in dev: no password reset),
worker-media (Metal/MLX — Mac only), Redis (only for `orazaka.identity.rate-limit.enabled`, off).

The **document-validation worker** (orazaka-packs, Python, `job.validation.*`) runs in the same container: the image
builds its venv (`pika`, `pyyaml`, `pypdf`, pinned), `run-services.sh` starts it after the JVMs (`ORAZAKA_WORKERS`,
default `validation`; empty to skip) with the broker's TLS settings and CA file, its lines prefixed `[validation]`. It
takes ~40 MB; like a JVM, if it exits the container restarts.

## Automations — webhooks and schedules (ADR-084)

The automation service starts a Studio run **as its owner** from a signed call or a schedule. Set it up once:

```bash
doctl databases pool create <cluster-id> pool-automation --db orazaka_automation_db --user orazaka_automation_db --size 1 --mode transaction
node scripts/dev-deploy.mjs automations     # the model, triggers, webhook ledger, runs and rule set v2 (idempotent)
```

- **A webhook trigger** has its own secret (32 random bytes, `whsec_…`), sealed under the asset keyring
  (`MEDIA_ENCRYPTION_KEY`), shown once, rotatable with a 24 h grace. `POST /edge/hooks/<triggerId>` with the Standard
  Webhooks headers (`webhook-id`, `webhook-timestamp`, `webhook-signature: v1,<base64 HMAC-SHA256>`) answers `202` with the
  run; a bad or stale signature `401`; a reused `webhook-id` `409`; a flood `429` with `Retry-After`; a body over 64 KiB `413`.
  It is the only anonymous route of the platform (ADR-035 amendment).
- **A schedule trigger** is « daily at HH:mm in a time zone on these weekdays »; one sweep every 15 s fires the slots that
  have come. The limits (body cap, tolerance, rates, budgets) are the rule set v2 (`automation_settings`), not configuration.
- **The pool is one connection**: no transaction is held across the call to the Studio service. The connection budget is
  22 of 22 with it (docs/DEV_DATABASE.md); the next service needs the plan upgrade or a smaller pool elsewhere.

## External checks — court records, credit, identity (orazaka-packs#138)

The engine's checks are job steps (`orazaka.core.checks.courtrecords`, `.credit`, `.identity`, handler `check.external`,
billed per `CALL` as `EXTERNAL_CHECK`), still on the mock connector. Two idempotent steps bring an existing dev database to
the schema of a fresh one, after orazaka-job-service#74 and krizaka-billing#57 are deployed:

```bash
node scripts/dev-deploy.mjs external-checks                    # orazaka_db: the three rows, the named billable check, external_check_audit (append-only)
node scripts/dev-deploy.mjs pricebook-external-check-research  # krizaka_billing_db: billing's rows (EXTERNAL_CHECK, mock, CALL) and (RESEARCH, law-corpus, CALL)
```

- **Off on dev, fail closed**: no `checks.subject-pepper` row is seeded, so the engine cannot name a subject and every check
  job is refused until an operator seals a pepper. No row, no check.
- The audit holds no personal data: the subject is an HMAC (`subject_ref`), a line is never rewritten (a trigger refuses
  an `UPDATE`).
- `infra/migrations/2026-10-25-external-checks.sql` is the `EXTERNAL CHECKS` block of orazaka-job-service's
  `infra/initdb/30-jobs-config.sql`, and `2026-10-25-pricebook-external-check-research.sql` is krizaka-billing's migration of
  the same name, unchanged; `scripts/test/dev-deploy.test.mjs` pins both against their checkouts.

## Semantic cache (orazaka-ai-engine#40)

```bash
node scripts/dev-deploy.mjs semantic-cache   # orazaka_db: semantic_cache_entry (pgvector, HNSW) and the SemanticCacheInterceptor row, OFF
```

- **Off on dev**: the interceptor row is disabled, and a pack must also declare `orazaka.pipeline.cache.mode`. An existing
  row is never overwritten (an operator's choice stays).
- `infra/migrations/2026-10-22-semantic-cache.sql` carries orazaka-job-service's `semantic_cache_entry` table verbatim;
  `scripts/test/dev-deploy.test.mjs` pins it against the checkout.

## Service accounts — a Krizaka product calling Sovkora

Krizaka's products consume AI **only through Sovkora**: Yomizaka runs the document Studios with its own account, never a
model called directly. One command makes the account, its API key and its Studio grants:

```bash
node scripts/dev-deploy.mjs service-account yomizaka \
  --studios compliance-check,document-authenticity \
  --out ~/Documents/krizaka/yomizaka-dev.env
```

- the user `svc_<product>` (`svc+<product>@orazaka.com`, ROLE_USER, a random password nobody keeps) in `krizaka_users_db`;
- an `oz_` API key named `<product>-dev`, created through the supported endpoint (`POST /api/v1/api-keys`, signed in as
  that user); earlier keys of the same name are revoked — one live key per product;
- the operator's grants in `orazaka_studio_db` (`studio_entitlement_grant`, krizaka/orazaka-studio#9; incremental script
  `infra/migrations/2026-10-14-studio-entitlement-grant.sql`): the PAID Studios open for that account only, with a reason;
- `AI_PROVIDER=orazaka`, `AI_API_URL`, `AI_API_KEY` written to `--out` (mode 600) — never printed. The product stores the
  key as a DO **SECRET** and calls `https://www.sovkora.com/edge` with `Authorization: Bearer oz_…`; the edge exchanges
  it for a JWT (`ApiKeyExchangeFilter`). Re-running rotates the key.

A key can be **scoped**: `--scopes ai:embed` makes the key usable for `POST /api/v1/embeddings` and nothing else (the
edge answers 403 anywhere else; an unscoped key keeps its account's rights). `--prefix AI_ENGINE` names the variables
`AI_ENGINE_URL` / `AI_ENGINE_API_KEY` instead of the Studio callers' `AI_PROVIDER` / `AI_API_URL` / `AI_API_KEY`:

```bash
node scripts/dev-deploy.mjs service-account product-explorer --scopes ai:embed --prefix AI_ENGINE \
  --out ~/Documents/krizaka/product-dev.env
```

Embeddings: `POST /api/v1/embeddings` `{ "input": ["…"], "model"?: "gte-large-en-v1.5" }` → `{ runId, model, dimensions, data:[{index, embedding}],
usage }` (and the header `X-Sovkora-Run-Id`). The model is data (`orazaka_models`, category `embedding`, GTE Large (en) v1.5 on the
`do-inference` provider; BGE-M3 is planned until the inference key may call it — `infra/deploy/dev-model-registry.sql`, run after `dev-data.sql`); the `runId` is the line `embedding.run
runId=…` in the core's logs (user, model, counts — never a text or a vector). No model configured or the provider down →
503, so a caller falls back to its own non-AI path. Existing databases need
`infra/migrations/2026-10-15-embeddings-and-key-scopes.sql` on `krizaka_users_db`.

### The service accounts and the dev reset

The products that call Sovkora are data: `scripts/dev-service-products.json` (`product → { studios, app, vault }`: the Studios
granted, the dev App Platform app, the `secrets.map` source whose file holds the key). Today: **verikora** (`verikora-dev`,
`compliance-check` and `document-authenticity`). Folioviv has no `AI_API_KEY` in its dev app spec and its Sovkora call is the
`text-translation` Studio behind a port, so it has no service account to put back; a product is added to the file the day it gets one.

A key lives in **three places**, and `service-account` only writes the first kind of file by hand:

1. the product's **vault file** (`AI_API_KEY`, one upsert, mode 600, the file never printed);
2. the product's **dev App Platform app** (`AI_API_KEY`, type SECRET, on the components that already declare it, set on the
   *live* spec through `doctl apps update`; the repository spec is never applied);
3. this repository's **`SERVICE_KEY_<PRODUCT>`** Actions secret, read by the key watch (`dev-service-keys.yml`, every 30 minutes:
   `GET /api/v1/studios/installations` → 200).

`node scripts/dev-reset.mjs orazaka --target dev --yes` clears `krizaka_users_db`, which drops every `svc_<product>` and its key. It
is refused unless each existing product is named: `--drop-service-accounts verikora` (and refused before anything is cleared when
a dropped account is not in the list). After the reseed the reset, for each product of the list: makes the account again with the
same code as `service-account` (`issueServiceAccount`), writes the new key to the three places, then calls the edge once per key.
Any answer other than 200, or any failed write, fails the reset with the product named (and no key in the message). Changing a
product's key by hand is `service-account` plus the same three writes.

## Recommendations — a product's feed, served by Sovkora (ADR-078)

The knowledge service serves the products' recommender (ADR-078); decision:
ADR-078). A product feeds it
with its service key — events, eligible items, erasures (scope `reco:sync`) — and reads its feeds (scope `reco:read`):
`POST /api/v1/recommendations/{events,items,erasures}`, `GET /api/v1/recommendations/{for-you,similar,insights}`. Users
are the product's pseudonyms; titles are embedded (on the caller's own session, through `/api/v1/embeddings`) and never
stored. Every feed has a `requestId` — the line `recommendation.served requestId=…` in the core's logs (`[knowledge]`).
The parameters are rows of `reco_settings` (versioned; v1 seeded), never configuration.

```bash
node scripts/dev-deploy.mjs recommendations      # the tables and settings v1 on orazaka_knowledge_db (idempotent)
node scripts/dev-deploy.mjs service-account product-explorer --scopes ai:embed,reco:sync,reco:read --prefix AI_ENGINE \
  --out ~/Documents/krizaka/product-dev.env   # rotates the product's key: update its App Platform secret
```

## Admin console (host routing)

`admin.sovkora.com` is not an app of its own any more: it is a domain **alias** of `sovkora-dev` and the `web` component
answers it (krizaka/orazaka-web-admin#31, -5 $/month). The console's code lives in `orazaka-web-client`
(`src/console/`, routes `src/app/console/`); `src/proxy.ts` reads the `Host`: on `admin.*` it applies the network gate
(`AUTH_ALLOWED_IPS`, 403 for any other address, sign-in page included), then the console's sign-in gate, and rewrites the
request under the internal `/console` prefix; on any other host `/console/**` is a 404. Nothing is shared with the public
site: the session cookie (`orazaka-admin.session-token`), its signing key (`AUTH_CONSOLE_SECRET`, filled from
`NEXTAUTH_SECRET_ADMIN`) and the sign-in flag (`AUTH_CONSOLE_LOGIN_ENABLED`). `AUTH_TRUST_HOST=true` makes next-auth build
the console's callback URLs from the Host it was asked on (`NEXTAUTH_URL` stays `https://www.sovkora.com`).
`/api/healthz` answers on both hosts, before any gate.

Cut-over (the old app serves the live console until step 3):

1. `node scripts/dev-deploy.mjs apply sovkora-dev --dry-run`, then check the new variables in the rendered spec
   (`AUTH_TRUST_HOST`, `AUTH_CONSOLE_LOGIN_ENABLED`, `AUTH_CONSOLE_SECRET`, `AUTH_ALLOWED_IPS`). The `web` component must be
   on the `main` of `orazaka-web-client` that holds the console.
2. A domain belongs to one app: take `admin.sovkora.com` off `orazaka-admin-dev` (control panel → Settings → Domains), then
   `node scripts/dev-deploy.mjs apply sovkora-dev`. Brief downtime of the console only.
3. DNS: `admin.sovkora.com` CNAME → the `sovkora-dev` default ingress (`doctl apps get <id> --format DefaultIngress`),
   unchanged if it already points there.
4. Check from an allowed address: `/login` signs a `ROLE_ADMIN` in; from any other address `https://admin.sovkora.com/`
   answers 403; `https://www.sovkora.com/console` answers 404; `https://www.sovkora.com/api/healthz` 200.

## Deploy

Secrets never enter the repository: `~/Documents/krizaka/env/dev.env` (mode 600; read through `~/.krizaka-worktrees/tools/secret.sh @dev KEY`, or give it with `--env-file`) holds the database role
passwords (`DATABASE_PASSWORD_<DB>`, read with `doctl databases user get`), `IDENTITY_JWT_SECRET`, `CRYPTO_KEY`,
`CRYPTO_SALT` (16 hex), `MEDIA_ENCRYPTION_KEY` (32 bytes, base64 — the asset keyring), `RABBITMQ_PASS`,
`NEXTAUTH_SECRET_WEB`, `NEXTAUTH_SECRET_ADMIN` and `AI_INFERENCE_KEY` (a **model access key**, created in the control
panel: Agent Platform → Serverless Inference → Model Access Keys — the API no longer creates them).

```bash
node scripts/dev-deploy.mjs apply sovkora-dev --dry-run   # render + validate, nothing sent
node scripts/dev-deploy.mjs apply sovkora-dev             # SECRET values are stored encrypted by App Platform
node scripts/dev-deploy.mjs data                          # infra/deploy/dev-data.sql: providers + chat default
node scripts/dev-deploy.mjs seed                          # test accounts → the QA store (@dev: ORAZAKA_USER_*, ORAZAKA_ADMIN_*), never printed
node scripts/dev-deploy.mjs persona                       # Eric, the demo persona for recordings → @dev APP_QA_ORAZAKA_PERSONA_*, never printed
node scripts/dev-deploy.mjs status
```

`web` and the admin redeploy on every push to their `main`. `core` does not (it is built from many repositories):

```bash
doctl apps create-deployment <sovkora-dev id> --force-rebuild   # --force-rebuild, or the clone layer is cached
```

Why: `core` clones every component's `main` while building, so a merge in a component (or in this workspace) changes
nothing App Platform watches; `deploy_on_push` cannot see it and stays `false`. `.github/workflows/dev-redeploy.yml`
(every 15 minutes, or by hand) runs `scripts/dev-redeploy.mjs`: when the `main` of the workspace or of a component is newer
than the last manual deployment, and none is building, it starts the `--force-rebuild` above. It needs the repository
secret `OPERATOR_DEPLOY_TOKEN` (named by context, not vendor; its value is a DigitalOcean API token; without it the job says so and stops). It never changes the spec and never
applies a migration (`scripts/dev-migrations.mjs check`, docs/DEV_DATABASE.md).

The build reads the Krizaka building blocks (`krizaka-parent`…) from GitHub Packages (ADR-019, krizaka-internal#1879), not from
Maven Central: `infra/deploy/Dockerfile` writes the repositories into the build's Maven settings (the org generator, no
credential in the file) and takes the read credential from the build argument `MAVEN_GITHUB_PACKAGES_TOKEN`, a `BUILD_TIME`
secret of the `core` component (`__MAVEN_GITHUB_PACKAGES_TOKEN__` in the spec, filled by `scripts/dev-deploy.mjs` from `@orazaka`;
a classic token with `read:packages`). The build stage is not shipped, so the value is in no image layer. `.github/workflows/dev-image-build.yml`
builds the image (no push) on a pull request that touches the image or the build configuration, so a resolution break fails the PR.

Logs: `doctl apps logs <id> core --type run` (each line prefixed by its service: `[users]`, `[conversation]`…).

## Broker — reachable from the GPU workers, safely

App Platform cannot expose AMQP, and a GPU droplet must reach the job lanes — so RabbitMQ lives on its own small
Droplet in the `default-tor1` VPC (`node scripts/dev-deploy.mjs broker`, idempotent):

- **TLS only**: `listeners.tcp = none`, 5671 with a certificate for `broker-dev.orazaka.com` signed by a dev CA kept in
  `~/Documents/krizaka/orazaka-broker/` (the CA key never leaves it). Clients verify the host name; the services trust
  the CA through Spring's SSL bundle `broker` (`RABBITMQ_CA_CERT_BASE64` → `/app/var/broker-ca.pem`).
- **Private**: bound to the Droplet's private IP; `broker-dev.orazaka.com` resolves to it; the cloud firewall admits 5671
  from the VPC range only (SSH from the maintainer's IP). `sovkora-dev` joins the VPC (`vpc.id` in its spec).
- **Two users**, strong random passwords in the secrets file: `orazaka` (the platform, everything on `/`) and
  `gpu-worker` (`RABBITMQ_PASS_GPU`: configure/write/read on `orazaka.*` names only). A GPU droplet must be created in
  the same VPC (`--vpc-uuid` of `default-tor1`; another region needs VPC peering).
- No volume worth keeping: queues are re-declared by the services and workers at start (AGENTS.md §6).

## Media — kept outside the container

`core` runs on App Platform, whose disk is wiped by every deploy: a generated image or video written only there
leaves a run whose file is gone (the run page then says "This result is no longer available"). The sealed
envelope (ADR-054: encrypted before it leaves the process) is therefore also stored through the `DurableAssetStore`
port (`orazaka-assets`), chosen by environment: `MEDIA_STORAGE_PROVIDER=local|bunny|directory`.

- `bunny`: a **private** storage zone (`orazaka-media-dev`, NY, standard tier, no pull zone, never a public URL — media is served only by
  the authenticated `/api/v1/assets/{jobId}/{file}` route, which restores a missing file after the ownership check).
  `MEDIA_STORAGE_ZONE`, `MEDIA_STORAGE_API_KEY` (the zone password, secret), `MEDIA_STORAGE_ENDPOINT`
  (default `storage.bunnycdn.com`, the Falkenstein host; **a zone answers 401 on any host but its own region's**, so
  the NY zone sets `ny.storage.bunnycdn.com`), `MEDIA_STORAGE_TENANT` (first key segment).
- Key layout (krizaka/.github#61): `t/<tenant>/u/<userId>/jobs/<jobId>/<file>`; retention deletes the job prefix.
  Besides the outputs, the job's archived `input.json` / input file and `result.json` live in the same job prefix,
  and what a user uploads under `t/<tenant>/u/<userId>/jobs/uploads/<assetId><ext>` (uploads belong to no job).
  After a redeploy a read that finds one gone fetches the sealed envelope back, for its owner only.
- Cost: Bunny Storage bills per GB stored (about 0.01 USD/GB/month, no minimum on top of the existing account), so dev
  outputs cost cents; DO Spaces would be 5 USD/month flat. No signed URL is needed: the zone has no public path, the
  only way out is the authenticated asset route (JWT expiry, ownership from the job row, other users get 404).
- Retention is data: `MEDIA_RETENTION_DAYS` (dev: 30; `0` keeps) — the job-service sweeper deletes the job's files,
  its durable prefix, then its row. Protected data classes keep their own, shorter window.
- `local` (default) keeps today's behaviour; self-hosted customers keep local disk (or S3/Bunny adapters later).

## Routing is data

`infra/deploy/dev-data.sql` (idempotent) points the providers of this environment: `do-inference`
(`https://inference.do-ai.run`, OpenAI-compatible, the key from the secrets file) as the chat default with
`mistral-3-14B`; `localai-image` at `http://gpu-relay:8086/v1` and `localai-video` at `http://gpu-relay:8188` (the GPU
host, through the relay). Change a model or a provider in the admin
console or in that file — never in a service's configuration.

- **The chat route is applied at startup, not by hand** (ADR-095: no GPU is ever left on; krizaka-internal#3285). The
  `mistral-3-14B` row and the chat default are the job service's repeatable migration
  `services/job/sovkora-job-service/src/main/resources/db/dev/R__dev_chat_route.sql`; only the `core` container's supervisor
  (`infra/deploy/run-services.sh`, `DATABASE_JOB_MIGRATIONS_LOCATIONS`) adds that location, so local and customer databases keep
  the baseline. A re-baselined dev database gets its route back at the next deploy.
- **The Download videos preflight proves it** (`apps/web/scripts/videos-preflight.mjs`): the default chat model of
  `GET /edge/api/v1/models/catalog` must be on `do-inference` (the drift check), and one short chat turn as the persona must come
  back non-empty (a few tokens on the per-token route), else the preflight is red naming the route and no paid step runs.

- The other hosted models (DeepSeek V4 Pro, Llama 4 Maverick, Gemma 4) and every model's registry metadata come
  from `infra/deploy/dev-model-registry.sql` (ORA-MODELS), run after `dev-data.sql`; selectable, never the default.
- The key reaches the call through `ai_providers.api_key` (krizaka/orazaka-ai-engine#10); `sovkora-dev` also holds it
  as the SECRET `AI_INFERENCE_KEY`, the deployment's copy of record. `node scripts/dev-deploy.mjs data` reads it from
  the secrets file and sends it on psql's stdin, never on a command line.
- `ORAZAKA_CORE_DEFAULT_PROVIDER=do-inference`: with the historical `ollama` default the pipeline pins Ollama's active
  model and never reaches the catalogue default.
- Embeddings (`gte-large-en-v1.5`, 1024 dimensions, tested on the key) also embed the recommender's items; the knowledge
  service runs in dev for the recommender, its RAG store stays dormant (no vector store bean): chat degrades to no RAG.

## GPU — images and 9:16 video, by the hour

Generation runs on a GPU droplet that exists only while it works (ADR-077). It runs the CUDA image of
`workers/media` of `krizaka/sovkora` (`ghcr.io/krizaka/sovkora-media-worker:cuda-latest`): **sd-server** with FLUX.2 [klein] 4B
for images, and the **media worker** draining `job.video.*` / `job.compose.*` with Wan for 9:16 video.

| Size | Droplet | $/hour | Video model (default) | 9:16 clip |
|---|---|---|---|---|
| `big` | NVIDIA L40S 48 GB (else RTX 6000 Ada 48 GB) | 1.57 | Wan 2.2 TI2V 5B | 704×1280, 5 s @ 24 fps |
| `small` | NVIDIA RTX 4000 Ada 20 GB | 0.76 | Wan 2.1 T2V 1.3B | 480×832, 5 s @ 16 fps |

```bash
scripts/gpu-dev.sh relay-secret          # once: the relay credential into the secrets file, then apply the app spec
scripts/gpu-dev.sh start big             # tor1 first, then nyc2, atl1, ams3 — wherever there is stock
scripts/gpu-dev.sh logs                  # setup and containers (ssh with a key made for it, SSH open to your IP only)
scripts/gpu-dev.sh status                # what runs, since when, what it has cost so far
scripts/gpu-dev.sh stop                  # destroy now — never a powered-off droplet, which still bills
scripts/gpu-dev.sh cost                  # every session (ledger in ~/Documents/krizaka): hours × rate, total
```

**How it connects — nothing is exposed.** The droplet opens no port (SSH from the caller's IP only). A chisel client
on it dials **out** to `https://www.sovkora.com/gpu-relay` (App Platform's TLS, a 256-bit credential
`AI_GPU_RELAY_PASSWORD`) and may open exactly five remotes — the relay's allow-list: `broker-dev.orazaka.com:5671` (the TLS broker of the VPC, as the narrow `gpu-worker` user, verified end to end against the dev CA),
`core:8090` (the job service: worker registry and output hand-over), and back inside the app `gpu-relay:8085` (LocalAI: Whisper and Piper, the `localai` provider), `gpu-relay:8086`
(sd-server), `gpu-relay:8188` (the video pipeline) and `gpu-relay:11434` (Ollama: `qwen2.5:7b` for the Studios'
text steps, `llava` for their photo steps — sovereign, Apache-2.0 / open weights, while the GPU runs). The broker stays VPC-only. While no GPU is connected nothing
listens on `gpu-relay:8086/8188`, which is exactly what greys the image and video capabilities out.

**A model is offered as runnable only while something can run it** (2026-10-20). The video jobs (`job.video.generate`, and the
sound ones) are drained by an external worker — the media worker, on a GPU or the Mac — and the job service lists in
`model_category_queue` (data, `infra/deploy/dev-data.sql`) the categories that depend on one. A model of such a category is
runnable when its engine answers **and** a `HEALTHY` worker of the registry is bound to that queue; otherwise it stays in the
Studio's list, greyed out with a short reason in the page's language (« GPU offline » for a self-hosted model, « Engine offline »
for the hosted Wan that the worker routes), is never the default, and a run that still names it — or leaves the model on automatic
while none can run — is refused at start (« no video model is available right now »), before any credit is held, instead of
waiting for a machine. The registry is advisory (ADR-038): a database without the table greys nothing out. Nothing starts a GPU on
demand here: the pod or the Mac is started by hand (`gpu-dev.sh`), and the model comes back a minute after its worker registers.

**Outputs.** The droplet keeps nothing: the worker hands each clip to the job service (`PUT
/internal/v1/jobs/{id}/output/{file}`, a `SERVICE` token minted by `gpu-dev.sh` from the identity secret — which never
leaves this machine — and expiring with the droplet), which seals it with the platform's key, adopts the job's row if a
Studio step left none, and serves it at `/api/v1/assets/{id}/video.mp4`.

**The image** is pulled from GHCR; while that package is private, the droplet builds it from the worker's public repository at its `main` commit (~20 min, once) and keeps it on the Volume (`/models/images`), so later starts load it in a minute. Making the package public (GitHub → Packages → orazaka-worker-media → visibility) removes the build.

**Weights** live on the Volume `orazaka-gpu-models-<region>` (80 GB, created on the first start in a region): Wan 2.2
5B 34 GB, Wan 2.1 1.3B 6 GB (its text encoder is the 5B's), FLUX.2 [klein] 8.7 GB. Fetched once, pinned (revisions,
SHA-256), then every start finds them in place.

**The watchdog** (`infra/gpu/watchdog.sh`, every minute, armed before anything else can fail) destroys the droplet
through the API after **30 min without a job** (a job start or end, an image request, the GPU busy), after
`GPU_MAX_HOURS` (8) whatever it does, or when setup has not finished after 90 min. It uses `GPU_WATCHDOG_DO_TOKEN`
from the secrets file when present — a token scoped to `droplet:read` + `droplet:delete`, to create in the control
panel — else the doctl token. Containers cannot read the droplet's metadata (and so its user-data).

### The sovereign smoke — do our own engines still generate?

`scripts/gpu-dev.sh smoke` starts the `small` GPU, runs `scripts/gpu-smoke.mjs` against dev.sovkora.com as the test user
— a chat Studio on Ollama, an image on sd-server (FLUX.2 [klein]), a 1 s 9:16 clip on the media worker (Wan 2.1 1.3B) —
asserts each (run SUCCEEDED, output non-empty, latency, the model our job reported) and **destroys the droplet whatever
happens** (trap on exit, interrupt and error). TTS, transcription and RAG are reported `SKIPPED` with their reason until
dev serves them (LocalAI, knowledge) — never passed. The report: `~/Documents/krizaka/orazaka-smoke/last.json`.
`scripts/gpu-dev.sh smoke mac` runs the same checks against the Mac's engines on the relay, for free. The weekly
workflow `GPU smoke (sovereign)` runs it on Mondays once its secrets are set (see the workflow's header; its DigitalOcean token is the repository secret `OPERATOR_GPU_TOKEN`, droplets and volumes only, separate from `OPERATOR_DEPLOY_TOKEN`, which deploys the apps); a run on a
warm Volume costs ≈ $0.20 (the first one also builds the image and fetches the weights).

### Brand-level clips: Wan 2.2 A14B from an image, then SeedVR2 to 1080p/4K

For a clip that is shown on our sites (the "Prompt → Image → Video" strip of dev.sovkora.com): the `hero` profile (80 GB card), the
Studio `video-generation` with `model: wan2.2-t2v-a14b` **and an `image`** — the worker then loads the family's image-to-video
checkpoint (`Wan-AI/Wan2.2-I2V-A14B-Diffusers`, 106 GB, pinned in the worker's `models.yaml`), 1280×720, 81 frames at 16 fps, about 20 minutes
a clip on an H100. The checkpoint is **not** on the network volume (35 GB free): fetch it to the pod's container disk
(`GPU_RUNPOD_DISK_GB=220`, `huggingface_hub.snapshot_download(..., cache_dir=/root/hfx)` — 2 minutes — then symlink
`/models/hf/hub/models--Wan-AI--Wan2.2-I2V-A14B-Diffusers` to it). `GPU_WORKER_BRANCH` starts the pod on a worker branch under test.

The optional Studio input `upscale` (`none` · `1080p` · `2160p`, blueprint 1.6.0) runs SeedVR2 3B after the clip, on the same GPU:
about 107 s for a 5 s clip to 1080p, 280 s to 4K (docs/_generated/MEASURED_RUNS.md, "Video upscaling"). It is installed by the pod's bootstrap
(pinned checkout and venv on the volume, weights fetched at the first use). Every clip is then reviewed frame by frame, graded and given its sound
before it is published; a clip that drifts, shows text or morphs is regenerated, never shown.

### The Mac as the GPU (free)

The Mac's Metal worker can drain the same queue:

```bash
scripts/gpu-dev.sh mac                   # writes ~/.orazaka/gpu-dev/mac-worker.env (0600, 12 h)
# then the two commands it prints: a chisel client (brew install chisel) and the worker with that env
```

The tunnel also carries the Mac's own engines back into the app — sd-server (`:8086`, FLUX.2 [klein] on Metal), the
worker's pipeline (`:8188`) and Ollama (`:11434`, with `qwen2.5:7b` and `llava` pulled) — so dev's image, video, chat and
vision steps run on the Mac while it is connected. The worker hands its outputs to the dev job service the same way. Speech (Piper through LocalAI on `:8085`) shares the image engine's relay port: start sd-server on `:8087`
(`MEDIA_IMAGE_PORT=8087`) and `node scripts/mac-engines-router.mjs` on `:8086`, which answers `/v1/audio/*` from LocalAI and
the rest from sd-server; the dev provider `localai` points at `gpu-relay:8086` (`infra/deploy/dev-data.sql`). Run one host at a time: two workers on one queue
share the jobs, and a Wan job reaching the Mac runs on Metal (slowly) or fails by name.

### Wan 2.2 A14B — a provider's model, run by Sovkora

`wan2-2-t2v-a14b` (catalogue row, provider `do-inference`) is made by DigitalOcean serverless inference; Sovkora is
the run: the Studio run id, the seed, the metering and the sealed asset are its own, and the clip says where it came from
(`metrics.provider`). The media worker routes it (`app/remote_video.py`: `POST /v1/videos`, poll `GET /v1/videos/{id}`,
`GET /v1/videos/{id}/content`); **text-to-video, 832×480 (16:9 only — measured: any other size is answered at 832×480, so
9:16 is refused by name), 5 s at 16 fps, $0.60 a clip, the seed makes the same shot again**. The key
(`AI_INFERENCE_KEY_ALL` in the secrets file, a model access key that lists the model) goes into the Mac worker's 0600
env by `scripts/gpu-dev.sh mac` — never printed, never in a repository. Select it with `--model wan2-2-t2v-a14b --aspect 16:9`.

## What works in dev

| Feature | Dev | Why |
|---|---|---|
| Sign-in (web, admin) | yes | admin needs `ROLE_ADMIN` |
| Sign-up | **no** — 403 | closed on purpose |
| Chat | yes — `mistral-3-14B` (DeepSeek V4 Pro once the key allows it) | serverless inference |
| Studios list, Prospecting runs (text only) | yes | chat capability |
| Trades showcase, Media production, 9:16 video | while the GPU runs | `scripts/gpu-dev.sh start big` |
| Document validation (compliance-check, document-authenticity) | for a **granted** account | PAID Studios, billing off: an operator grant opens them (`service-account`, below); the worker runs in `core` |
| Real-estate reels | locked | PAID Studio, billing off |
| E-mails (verify, reset) | no | notifications not deployed |

## The core journey on dev (Dev preview `journey.core`, `journey.ai-via-orazaka`)

`node scripts/journey-dev.mjs --out <dir>` replays what Sovkora is for, on https://www.sovkora.com/edge as the demo persona
Eric (credentials from the environment, read with `secret.sh`, never printed): sign in → ask the chat → make an image with
the `image-generation` Studio → have the picture read by `image-analysis` →
make a hosted clip with `video-generation`. Every step goes through the public API (no SQL, no direct model call) and records
the edge request id; each Studio step also records its run id, the engine's own trace (`GET /api/v1/studios/runs/{id}`, the
job ids of its steps). It writes the report (`journey-<date>.json`, `.md`) and the clip, and exits 1 unless every step is
proven. Cost: about 0.70 USD (one hosted image, one 5 s hosted clip). The 9:16 models run on the GPU host, which is up only for
a batch: pass `--aspect 9:16 --video-model wan2.2-ti2v-5b` while a pod is running. Rules tested first: `scripts/test/journey.test.mjs`.
